import json
import logging
import re

from openai import AsyncOpenAI, BadRequestError

from app.core.config import settings
from app.schemas.outfit import (
    ClosetOutfitSuggestion,
    ClosetRecommendRequest,
    ClosetRecommendResponse,
    SituationRecommendRequest,
    SituationRecommendResponse,
    SituationOutfitSuggestion,
    ShoppingSuggestion,
)

logger = logging.getLogger("uvicorn.error")

client = AsyncOpenAI(api_key=settings.openai_api_key)

_BODY_TYPE_LABELS = {
    "SLIM": "슬림",
    "NORMAL": "보통",
    "MUSCULAR": "근육질",
    "CHUBBY": "통통",
}

_STYLE_LABELS = {
    "CASUAL": "캐주얼",
    "FORMAL": "포멀",
    "SPORTY": "스포티",
    "STREET": "스트릿",
    "VINTAGE": "빈티지",
    "MINIMAL": "미니멀",
}

_SITUATION_PROMPT_TEMPLATE = """당신은 개인 패션 스타일리스트입니다.
사용자의 체형/취향 정보와 오늘 날씨, 상황을 바탕으로 옷장 없이도 바로 참고할 수 있는
구체적인 코디 조합을 2~3가지 추천해주세요.

현재 날씨:
- 기온: {temperature}°C
- 날씨 상태: {condition}

상황: {situation}

사용자 체형/취향 정보:
- 키: {height}
- 몸무게: {weight}
- 체형: {body_type}
- 선호 스타일: {preferred_style}

다음 JSON 형식으로만 응답해주세요. 다른 텍스트 없이 JSON만 출력하세요:
{{
  "outfits": [
    {{
      "description": "구체적인 아이템 조합 (예: 네이비 슬림핏 셔츠 + 베이지 치노 팬츠 + 로퍼)",
      "reason": "추천 이유 (2~3문장, 한국어)",
      "style_tag": "캐주얼|포멀|스포티|스트릿|빈티지|미니멀 중 하나",
      "shopping_suggestions": [
        {{
          "item": "구체적인 아이템명 (예: 네이비 슬림핏 셔츠)",
          "site": "무신사|지그재그|에이블리|W컨셉 중 그 아이템에 가장 어울리는 한 곳",
          "search_keyword": "그 쇼핑몰 앱/사이트에서 바로 검색하면 좋은 키워드"
        }}
      ]
    }}
  ]
}}

규칙:
- 날씨와 상황에 맞는 아이템으로 구성하세요 (더운 날 두꺼운 아우터 제외, 추운 날 민소매 제외)
- 체형/선호 스타일 정보가 있으면 반영하고, 없으면 무난한 조합으로 추천하세요
- 상의+하의, 또는 아우터를 포함한 구체적인 아이템 조합으로 description을 작성하세요
- shopping_suggestions는 실제 상품 검색 결과가 아니라 사용자가 직접 쇼핑몰에서 검색해볼 수 있도록 돕는 제안이므로, description에 포함된 아이템 중 2~3개만 골라 site와 search_keyword를 구체적으로 제시하세요
- 특별한 이유가 없다면 무신사와 지그재그를 우선적으로 추천하세요
- 기온 구간별 가이드를 따르세요: 5°C 미만은 패딩/두꺼운 코트, 5~12°C는 코트/자켓+니트, 12~20°C는 가디건/가벼운 자켓, 20~27°C는 반팔/얇은 셔츠, 28°C 이상은 반팔/린넨 소재(아우터 제외)
- 추천하는 조합끼리 아이템과 분위기가 서로 겹치지 않게 구성하세요"""


def style_text(profile) -> str:
    """프롬프트에 넣을 선호 스타일 문구. 여러 개면 모두 나열하고 골고루 반영하도록 안내한다."""
    if not profile:
        return "정보 없음"
    keys = list(profile.preferred_styles or [])
    if not keys and profile.preferred_style:  # 옛 Spring 요청(단일 값) 호환
        keys = [profile.preferred_style]
    labels = [_STYLE_LABELS[k] for k in keys if k in _STYLE_LABELS]
    if not labels:
        return "정보 없음"
    if len(labels) == 1:
        return labels[0]
    return f"{', '.join(labels)} (여러 스타일을 모두 반영해, 한 가지에 치우치지 않고 골고루 섞어서 추천)"


def _parse_json(content: str) -> dict:
    content = (content or "").strip()
    content = re.sub(r"```(?:json)?\n?", "", content).strip()
    return json.loads(content)


# 모델에 따라 max_tokens / temperature 를 거부할 수 있어 호출 파라미터를 한곳에서 관리한다.
# 추론 계열 모델이면 _TOKEN_PARAM 을 "max_completion_tokens" 로 바꾸고 _SEND_TEMPERATURE 를 False 로 둔다.
_TOKEN_PARAM = "max_tokens"
_SEND_TEMPERATURE = True
_TEMPERATURE = 0.7
_PARSE_ATTEMPTS = 2  # 최초 1회 + 파싱 실패 시 재시도 1회


def _completion_kwargs(max_tokens: int, *, compat: bool = False) -> dict:
    """compat=True 는 모델이 기본 파라미터를 거부했을 때의 폴백(max_completion_tokens, temperature 생략)."""
    kwargs = {
        "model": settings.openai_model,
        "response_format": {"type": "json_object"},
        "max_completion_tokens" if compat else _TOKEN_PARAM: max_tokens,
    }
    if _SEND_TEMPERATURE and not compat:
        kwargs["temperature"] = _TEMPERATURE
    return kwargs


async def _request_json(prompt: str, max_tokens: int) -> dict:
    """JSON 응답을 받아 파싱한다. 파싱 실패 시 1회 재시도하고, 끝내 실패하면 빈 dict 를 돌려준다."""
    messages = [{"role": "user", "content": prompt}]
    compat = False
    for attempt in range(_PARSE_ATTEMPTS):
        try:
            response = await client.chat.completions.create(messages=messages, **_completion_kwargs(max_tokens, compat=compat))
        except BadRequestError:
            if compat:
                raise
            # max_tokens / temperature 미지원 모델 — 호환 파라미터로 한 번 더 시도
            compat = True
            response = await client.chat.completions.create(messages=messages, **_completion_kwargs(max_tokens, compat=True))
        try:
            return _parse_json(response.choices[0].message.content)
        except (json.JSONDecodeError, TypeError):
            logger.warning("추천 응답 JSON 파싱 실패 (시도 %d/%d)", attempt + 1, _PARSE_ATTEMPTS)
    return {}


async def recommend_outfit_by_situation(request: SituationRecommendRequest) -> SituationRecommendResponse:
    profile = request.body_profile

    prompt = _SITUATION_PROMPT_TEMPLATE.format(
        temperature=request.weather.temperature,
        condition=request.weather.condition,
        situation=request.situation,
        height=f"{profile.height}cm" if profile and profile.height else "정보 없음",
        weight=f"{profile.weight}kg" if profile and profile.weight else "정보 없음",
        body_type=_BODY_TYPE_LABELS.get(profile.body_type, "정보 없음") if profile and profile.body_type else "정보 없음",
        preferred_style=style_text(profile),
    )

    data = await _request_json(prompt, max_tokens=1000)
    outfits = [
        SituationOutfitSuggestion(
            description=item["description"],
            reason=item["reason"],
            style_tag=item["style_tag"],
            shopping_suggestions=[
                ShoppingSuggestion(
                    item=s["item"],
                    site=s["site"],
                    search_keyword=s["search_keyword"],
                )
                for s in item.get("shopping_suggestions", [])
            ],
        )
        for item in data.get("outfits", [])
    ]

    return SituationRecommendResponse(outfits=outfits)


_CATEGORY_LABELS = {"TOP": "상의", "BOTTOM": "하의", "OUTER": "아우터", "SHOES": "신발", "ETC": "기타"}

_CLOSET_PROMPT_TEMPLATE = """당신은 개인 패션 스타일리스트입니다.
아래는 사용자가 실제로 가지고 있는 옷 목록입니다. 이 목록에 있는 옷만 사용해서
오늘 날씨와 상황에 맞는 코디 조합을 최대 3가지 추천해주세요.

현재 날씨:
- 기온: {temperature}°C
- 날씨 상태: {condition}

상황: {situation}

사용자 체형/취향 정보:
- 키: {height}
- 몸무게: {weight}
- 체형: {body_type}
- 선호 스타일: {preferred_style}

보유 옷 목록 (코드 | 카테고리 | 색상 | 이름):
{closet}

다음 JSON 형식으로만 응답해주세요. 다른 텍스트 없이 JSON만 출력하세요:
{{
  "outfits": [
    {{
      "items": ["C1", "C4", "C7"],
      "reason": "추천 이유 (2~3문장, 한국어. 색 조합과 날씨/상황 적합성을 근거로)",
      "style_tag": "캐주얼|포멀|스포티|스트릿|빈티지|미니멀 중 하나"
    }}
  ]
}}

규칙:
- items에는 반드시 위 목록의 코드만 쓰세요. 목록에 없는 옷을 지어내지 마세요
- 한 조합에는 가능하면 상의 1개 + 하의 1개를 넣고, 신발이 있으면 신발 1개를 추가하세요
- 기온 구간별 가이드를 따르세요: 5°C 미만은 패딩/두꺼운 코트, 5~12°C는 코트/자켓+니트, 12~20°C는 가디건/가벼운 자켓, 20~27°C는 반팔/얇은 셔츠, 28°C 이상은 반팔/린넨 소재(아우터 제외)
- 20°C 미만이고 아우터가 있으면 위 가이드에 맞는 아우터를 추가하고, 28°C 이상이면 아우터를 넣지 마세요
- 같은 카테고리의 옷을 한 조합에 두 개 넣지 마세요
- 조합끼리 최대한 겹치지 않게 구성하되, 옷이 적으면 일부 겹쳐도 됩니다
- 만들 수 있는 조합이 적으면 1~2개만 추천해도 됩니다"""


async def recommend_outfit_by_closet(request: ClosetRecommendRequest) -> ClosetRecommendResponse:
    profile = request.body_profile

    # UUID를 그대로 넣으면 모델이 옮겨 적다 틀리기 쉬워서 짧은 코드(C1, C2…)로 치환 후 되돌림
    code_to_id = {f"C{i + 1}": item.id for i, item in enumerate(request.clothes)}
    closet_lines = "\n".join(
        f"- C{i + 1} | {_CATEGORY_LABELS.get(item.category, item.category)} | {item.color or '-'} | {item.name or '-'}"
        for i, item in enumerate(request.clothes)
    )

    prompt = _CLOSET_PROMPT_TEMPLATE.format(
        temperature=request.weather.temperature,
        condition=request.weather.condition,
        situation=request.situation,
        height=f"{profile.height}cm" if profile and profile.height else "정보 없음",
        weight=f"{profile.weight}kg" if profile and profile.weight else "정보 없음",
        body_type=_BODY_TYPE_LABELS.get(profile.body_type, "정보 없음") if profile and profile.body_type else "정보 없음",
        preferred_style=style_text(profile),
        closet=closet_lines,
    )

    data = await _request_json(prompt, max_tokens=800)
    outfits = []
    for outfit in data.get("outfits", []):
        # 목록에 없는 코드(환각)는 버리고, 중복 제거
        ids = list(dict.fromkeys(code_to_id[c] for c in outfit.get("items", []) if c in code_to_id))
        if not ids:
            continue
        outfits.append(
            ClosetOutfitSuggestion(
                clothes_ids=ids,
                reason=outfit.get("reason", ""),
                style_tag=outfit.get("style_tag", ""),
            )
        )

    return ClosetRecommendResponse(outfits=outfits)
