"""Shopping-mall search keyword prompt, kept from the removed shopping helper (FR-23).

Only the prompt pieces live here so a later feature (FR-25, next item to buy) can reuse them.
There is no router or OpenAI call in this module.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Sequence

SHOPPING_SITES: tuple[str, ...] = ("무신사", "지그재그", "에이블리", "W컨셉")

SEARCH_KEYWORD_RULES = (
    "- 실제 상품 검색 결과가 아니라 사용자가 직접 쇼핑몰에서 검색해볼 수 있도록 돕는 제안이므로, "
    "각 아이템마다 site와 search_keyword를 구체적으로 제시하세요\n"
    "- 특별한 이유가 없다면 무신사와 지그재그를 우선적으로 추천하세요"
)

_PROMPT_TEMPLATE = """아래 아이템마다 쇼핑몰 앱/사이트에서 바로 검색할 수 있는 검색어를 만들어주세요.

아이템:
{items}

다음 JSON 형식으로만 응답해주세요. 다른 텍스트 없이 JSON만 출력하세요:
{{
  "suggestions": [
    {{
      "item": "입력한 아이템명 그대로",
      "site": "{sites} 중 그 아이템에 가장 어울리는 한 곳",
      "search_keyword": "그 쇼핑몰 앱/사이트에서 바로 검색하면 좋은 키워드"
    }}
  ]
}}

규칙:
{rules}"""


@dataclass(frozen=True)
class SearchKeyword:
    item: str
    site: str
    search_keyword: str


def build_search_keyword_prompt(items: Sequence[str]) -> str:
    """Build the prompt asking for one mall + search keyword per item description."""
    lines = "\n".join(f"- {item}" for item in items)
    return _PROMPT_TEMPLATE.format(items=lines, sites="|".join(SHOPPING_SITES), rules=SEARCH_KEYWORD_RULES)


def parse_search_keywords(data: dict[str, Any]) -> list[SearchKeyword]:
    """Keep well-formed suggestions whose site is a known mall; drop the rest."""
    result: list[SearchKeyword] = []
    for raw in data.get("suggestions") or []:
        if not isinstance(raw, dict):
            continue
        item, site, keyword = raw.get("item"), raw.get("site"), raw.get("search_keyword")
        if not all(isinstance(v, str) and v.strip() for v in (item, site, keyword)):
            continue
        if site not in SHOPPING_SITES:
            continue
        result.append(SearchKeyword(item=item.strip(), site=site, search_keyword=keyword.strip()))
    return result


def parse_search_keywords_text(text: str) -> list[SearchKeyword]:
    """Parse raw LLM text (optionally fenced) into suggestions; invalid JSON gives an empty list."""
    cleaned = text.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError:
        return []
    return parse_search_keywords(data) if isinstance(data, dict) else []
