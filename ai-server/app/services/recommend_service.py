import json
import re

from openai import AsyncOpenAI

from app.core.config import settings
from app.schemas.outfit import RecommendRequest, RecommendResponse, RecommendedOutfit

client = AsyncOpenAI(api_key=settings.openai_api_key)

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
