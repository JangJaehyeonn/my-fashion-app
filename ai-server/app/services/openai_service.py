import base64
import json
import re

from openai import AsyncOpenAI

from app.core.config import settings
from app.schemas.clothes import ClothesClassifyResponse

client = AsyncOpenAI(api_key=settings.openai_api_key)

_CLASSIFY_PROMPT = """이 옷 이미지를 분석해서 다음 JSON 형식으로만 응답해주세요. 다른 텍스트 없이 JSON만 출력하세요.

{
  "category": "상의|하의|아우터|신발|가방|액세서리 중 하나",
  "color": "주요 색상 (예: 흰색, 검정, 네이비, 베이지 등)",
  "pattern": "무지|줄무늬|체크|플로럴|도트|카모플라쥬|기타 중 하나",
  "season": "봄|여름|가을|겨울|사계절 중 하나",
  "style_tag": "캐주얼|포멀|스포티|스트릿|빈티지|미니멀|기타 중 하나"
}"""


def _parse_json(content: str) -> dict:
    content = content.strip()
    content = re.sub(r"```(?:json)?\n?", "", content).strip()
    return json.loads(content)


async def classify_clothes_image(image_bytes: bytes, content_type: str = "image/jpeg") -> ClothesClassifyResponse:
    base64_image = base64.b64encode(image_bytes).decode("utf-8")

    response = await client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": _CLASSIFY_PROMPT},
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:{content_type};base64,{base64_image}"},
                    },
                ],
            }
        ],
        max_tokens=300,
        temperature=0.1,
    )

    data = _parse_json(response.choices[0].message.content)
    return ClothesClassifyResponse(**data)
