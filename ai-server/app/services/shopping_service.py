from app.schemas.shopping import (
    ShoppingRecommendRequest,
    ShoppingRecommendResponse,
    ShoppingItemSuggestion,
)
from app.services.recommend_service import (
    client,
    _parse_json,
    _BODY_TYPE_LABELS,
    _STYLE_LABELS,
)

_SHOPPING_PROMPT_TEMPLATE = """당신은 예산에 맞춰 옷을 추천하는 개인 쇼핑 도우미입니다.
사용자의 예산과 상황, 체형/취향 정보를 바탕으로 실제 구매하면 좋을 구체적인 아이템을
예산 내에서 조합해 추천해주세요.

예산: {budget}원
상황: {situation}

사용자 체형/취향 정보:
- 키: {height}
- 몸무게: {weight}
- 체형: {body_type}
- 선호 스타일: {preferred_style}

다음 JSON 형식으로만 응답해주세요. 다른 텍스트 없이 JSON만 출력하세요:
{{
  "items": [
    {{
      "item": "구체적인 아이템명 (예: 네이비 슬림핏 셔츠)",
      "reason": "이 아이템을 추천하는 이유 (1~2문장, 한국어)",
      "estimated_price": 예상 가격(원, 정수),
      "site": "무신사|지그재그|에이블리|W컨셉 중 그 아이템에 가장 어울리는 한 곳",
      "search_keyword": "그 쇼핑몰 앱/사이트에서 바로 검색하면 좋은 키워드"
    }}
  ],
  "total_estimated_price": 전체 아이템 예상 가격 합계(원, 정수),
  "usage_tip": "이 아이템들만 사면 몇 가지 코디가 가능한지와 활용법을 설명하는 한국어 문장 (예: '이 3가지 아이템만 있으면 셔츠 단독, 팬츠와 매치, 셋을 레이어드하는 등 총 4가지 코디가 가능해요')"
}}

규칙:
- 아이템들의 estimated_price 합계가 total_estimated_price와 일치해야 하고, budget을 크게 초과하지 않도록 구성하세요 (예산의 110% 이내)
- 상황에 맞는 아이템으로 구성하세요
- 체형/선호 스타일 정보가 있으면 반영하고, 없으면 무난한 조합으로 추천하세요
- 아이템 개수는 예산에 따라 2~5개 사이로 조정하세요 (예산이 작으면 적게, 크면 많이)
- usage_tip에는 반드시 구체적인 코디 가능 개수(N가지)를 명시하세요
- 실제 상품 검색 결과가 아니라 사용자가 직접 쇼핑몰에서 검색해볼 수 있도록 돕는 제안이므로, 각 아이템마다 site와 search_keyword를 구체적으로 제시하세요
- 특별한 이유가 없다면 무신사와 지그재그를 우선적으로 추천하세요"""


async def recommend_shopping(request: ShoppingRecommendRequest) -> ShoppingRecommendResponse:
    profile = request.body_profile

    prompt = _SHOPPING_PROMPT_TEMPLATE.format(
        budget=request.budget,
        situation=request.situation,
        height=f"{profile.height}cm" if profile and profile.height else "정보 없음",
        weight=f"{profile.weight}kg" if profile and profile.weight else "정보 없음",
        body_type=_BODY_TYPE_LABELS.get(profile.body_type, "정보 없음") if profile and profile.body_type else "정보 없음",
        preferred_style=_STYLE_LABELS.get(profile.preferred_style, "정보 없음") if profile and profile.preferred_style else "정보 없음",
    )

    response = await client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=1200,
        temperature=0.7,
    )

    data = _parse_json(response.choices[0].message.content)
    items = [
        ShoppingItemSuggestion(
            item=item["item"],
            reason=item["reason"],
            estimated_price=item["estimated_price"],
            site=item["site"],
            search_keyword=item["search_keyword"],
        )
        for item in data["items"]
    ]

    return ShoppingRecommendResponse(
        items=items,
        total_estimated_price=data["total_estimated_price"],
        usage_tip=data["usage_tip"],
    )
