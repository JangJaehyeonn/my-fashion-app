import base64

from app.schemas.clothes import ClothesClassifyResponse
from app.services.diagnosis_service import _parse_json
from app.services.recommend_service import client

_VALID_CATEGORIES = {"TOP", "BOTTOM", "SHOES", "OUTER", "ETC"}

_CLASSIFY_PROMPT = """당신은 패션 아이템 분류 전문가입니다.
사진 속 옷 한 벌을 분석해서 다음 JSON 형식으로만 응답해주세요. 다른 텍스트 없이 JSON만 출력하세요.

{
  "category": "TOP|BOTTOM|SHOES|OUTER|ETC 중 하나",
  "color": "대표 색상 한 단어 (한국어, 예: 블랙, 화이트, 네이비, 베이지, 그레이, 카키)",
  "name": "색상+소재/핏+종류로 된 짧은 아이템명 (한국어, 예: 네이비 옥스포드 셔츠)"
}

분류 기준:
- TOP: 티셔츠, 셔츠, 니트, 맨투맨, 후드티, 블라우스, 원피스
- BOTTOM: 바지, 청바지, 슬랙스, 반바지, 스커트
- SHOES: 운동화, 구두, 로퍼, 부츠, 샌들 등 모든 신발
- OUTER: 자켓, 코트, 패딩, 가디건, 점퍼, 블레이저
- ETC: 가방, 모자, 액세서리, 또는 옷이 명확히 보이지 않는 사진

규칙:
- 사진에 여러 아이템이 있으면 가장 크게 보이는 한 벌만 분류하세요
- 사진에 옷이 명확히 보이지 않으면 category는 ETC, name은 "알 수 없는 아이템"으로 응답하세요"""


async def classify_clothes_image(image_bytes: bytes, content_type: str = "image/jpeg") -> ClothesClassifyResponse:
    base64_image = base64.b64encode(image_bytes).decode("utf-8")

    response = await client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": _CLASSIFY_PROMPT},
                    {
                        "type": "image_url",
                        # 카테고리/색상 판별엔 저해상도로 충분 — 기본(auto)은 큰 사진을 여러 타일로 쪼개
                        # gpt-4o-mini에서 이미지당 토큰이 크게 늘고 분당 토큰 한도(TPM)에 금방 걸림
                        "image_url": {"url": f"data:{content_type};base64,{base64_image}", "detail": "low"},
                    },
                ],
            }
        ],
        max_tokens=200,
        temperature=0.2,
    )

    choice = response.choices[0]
    content = choice.message.content
    if not content:
        raise ValueError(f"OpenAI returned empty content (finish_reason={choice.finish_reason})")

    data = _parse_json(content)
    category = str(data.get("category", "ETC")).upper()

    return ClothesClassifyResponse(
        # 모델이 목록 밖 값을 주더라도 등록 자체는 막지 않도록 ETC로 흡수
        category=category if category in _VALID_CATEGORIES else "ETC",
        color=data.get("color") or "기타",
        name=data.get("name") or "알 수 없는 아이템",
    )
