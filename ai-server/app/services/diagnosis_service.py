import base64
import json
import re

from openai import AsyncOpenAI

from app.core.config import settings
from app.schemas.diagnosis import DiagnosisResponse, SimilarStyleSuggestion

client = AsyncOpenAI(api_key=settings.openai_api_key)

_DIAGNOSIS_PROMPT = """당신은 개인 패션 스타일리스트입니다.
이 코디 사진을 분석해서 다음 JSON 형식으로만 응답해주세요. 다른 텍스트 없이 JSON만 출력하세요.

{
  "score": 0부터 100 사이의 정수 (전체적인 코디 완성도 점수),
  "feedback": "이 코디에 대한 구체적인 평가와 개선 제안 (3~4문장, 한국어. 색상 조합, 핏, 아이템 매칭 등을 근거로 설명)",
  "similar_styles": [
    {
      "style_tag": "캐주얼|포멀|스포티|스트릿|빈티지|미니멀 중 하나",
      "description": "현재 코디와 비슷한 무드를 가진 스타일 방향 제안 (1~2문장, 한국어)"
    }
  ]
}

규칙:
- 사진에 사람이나 옷이 명확히 보이지 않으면 낮은 점수와 함께 그 이유를 feedback에 설명하세요
- feedback은 비판만 하지 말고 구체적으로 무엇을 바꾸면 좋을지 제안하세요 (예: "상의 색을 더 밝게", "핏을 슬림하게")
- similar_styles는 1~2개만 제시하세요"""


def _parse_json(content: str) -> dict:
    content = content.strip()
    content = re.sub(r"```(?:json)?\n?", "", content).strip()
    if not content:
        raise ValueError("OpenAI response was empty after stripping")
    return json.loads(content)


async def diagnose_outfit_image(image_bytes: bytes, content_type: str = "image/jpeg") -> DiagnosisResponse:
    base64_image = base64.b64encode(image_bytes).decode("utf-8")

    response = await client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": _DIAGNOSIS_PROMPT},
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:{content_type};base64,{base64_image}"},
                    },
                ],
            }
        ],
        max_tokens=600,
        temperature=0.4,
    )

    choice = response.choices[0]
    content = choice.message.content
    if not content:
        raise ValueError(f"OpenAI returned empty content (finish_reason={choice.finish_reason})")

    data = _parse_json(content)
    similar_styles = [
        SimilarStyleSuggestion(style_tag=s["style_tag"], description=s["description"])
        for s in data.get("similar_styles", [])
    ]

    return DiagnosisResponse(
        score=data["score"],
        feedback=data["feedback"],
        similar_styles=similar_styles,
    )
