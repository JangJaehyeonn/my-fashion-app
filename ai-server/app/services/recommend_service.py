import json
import re

from openai import AsyncOpenAI

from app.core.config import settings
from app.schemas.outfit import (
    RecommendRequest,
    RecommendResponse,
    RecommendedOutfit,
    SituationRecommendRequest,
    SituationRecommendResponse,
    SituationOutfitSuggestion,
)

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
      "style_tag": "캐주얼|포멀|스포티|스트릿|빈티지|미니멀 중 하나"
    }}
  ]
}}

규칙:
- 날씨와 상황에 맞는 아이템으로 구성하세요 (더운 날 두꺼운 아우터 제외, 추운 날 민소매 제외)
- 체형/선호 스타일 정보가 있으면 반영하고, 없으면 무난한 조합으로 추천하세요
- 상의+하의, 또는 아우터를 포함한 구체적인 아이템 조합으로 description을 작성하세요"""

_PROMPT_TEMPLATE = """당신은 개인 패션 스타일리스트입니다.
사용자의 현재 날씨와 보유 옷 목록을 바탕으로 오늘 입기 좋은 코디를 1~3가지 추천해주세요.

현재 날씨:
- 기온: {temperature}°C
- 날씨 상태: {condition}

보유 옷 목록 (JSON):
{clothes_list}

다음 JSON 형식으로만 응답해주세요. 다른 텍스트 없이 JSON만 출력하세요:
{{
  "outfits": [
    {{
      "clothes_ids": ["옷ID1", "옷ID2"],
      "reason": "추천 이유 (2~3문장, 한국어)",
      "style_tag": "캐주얼|포멀|스포티|스트릿|빈티지|미니멀 중 하나"
    }}
  ]
}}

규칙:
- clothes_ids는 반드시 제공된 옷 목록의 실제 id 값만 사용하세요
- 날씨에 맞는 옷을 선택하세요 (더운 날 두꺼운 아우터 제외, 추운 날 민소매 제외)
- 가능하면 상의+하의, 또는 아우터를 포함한 조합으로 구성하세요"""


def _parse_json(content: str) -> dict:
    content = content.strip()
    content = re.sub(r"```(?:json)?\n?", "", content).strip()
    return json.loads(content)


async def recommend_outfits(request: RecommendRequest) -> RecommendResponse:
    clothes_list = [
        {
            "id": c.id,
            "category": c.category or "미분류",
            "color": c.color or "미분류",
            "pattern": c.pattern or "미분류",
            "season": c.season or "미분류",
            "style_tag": c.style_tag or "미분류",
        }
        for c in request.clothes
    ]

    prompt = _PROMPT_TEMPLATE.format(
        temperature=request.weather.temperature,
        condition=request.weather.condition,
        clothes_list=json.dumps(clothes_list, ensure_ascii=False, indent=2),
    )

    response = await client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=1000,
        temperature=0.7,
    )

    data = _parse_json(response.choices[0].message.content)
    outfits = [
        RecommendedOutfit(
            clothes_ids=item["clothes_ids"],
            reason=item["reason"],
            style_tag=item["style_tag"],
        )
        for item in data["outfits"]
    ]

    return RecommendResponse(outfits=outfits)


async def recommend_outfit_by_situation(request: SituationRecommendRequest) -> SituationRecommendResponse:
    profile = request.body_profile

    prompt = _SITUATION_PROMPT_TEMPLATE.format(
        temperature=request.weather.temperature,
        condition=request.weather.condition,
        situation=request.situation,
        height=f"{profile.height}cm" if profile and profile.height else "정보 없음",
        weight=f"{profile.weight}kg" if profile and profile.weight else "정보 없음",
        body_type=_BODY_TYPE_LABELS.get(profile.body_type, "정보 없음") if profile and profile.body_type else "정보 없음",
        preferred_style=_STYLE_LABELS.get(profile.preferred_style, "정보 없음") if profile and profile.preferred_style else "정보 없음",
    )

    response = await client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=1000,
        temperature=0.7,
    )

    data = _parse_json(response.choices[0].message.content)
    outfits = [
        SituationOutfitSuggestion(
            description=item["description"],
            reason=item["reason"],
            style_tag=item["style_tag"],
        )
        for item in data["outfits"]
    ]

    return SituationRecommendResponse(outfits=outfits)
