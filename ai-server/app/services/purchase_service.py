"""FR-11: 살까 말까 대표 코디 선택과 이유 문장.

판정(verdict)·+N·비슷한 옷은 백엔드 규칙이 이미 정했다. 여기서는 후보 조합 중 최대 3개를 고르고 문장만 쓴다.
후보 밖 조합·모르는 옷은 버리고, 문장이 모자라면 ValueError로 실패해 백엔드가 규칙 기반 기본 문장을 쓰게 한다.
"""

from __future__ import annotations

import json
import re
from typing import Any

from app.schemas.purchase import Pick, PurchaseExplainRequest, PurchaseExplainResponse, SimilarNote
from app.services.recommend_service import client

MODEL = "gpt-4o-mini"
MAX_PICKS = 3
REASON_COUNT = 3
MAX_LABEL_LENGTH = 20
MAX_SENTENCE_LENGTH = 200

CATEGORY_LABELS = {"TOP": "상의", "BOTTOM": "하의", "OUTER": "아우터", "SHOES": "신발", "ETC": "기타"}
VERDICT_LABELS = {
    "BUY": "코디가 많이 늘어나는 옷 (BUY)",
    "THINK": "늘어나는 코디가 적은 옷 (THINK)",
    "SIMILAR": "옷장에 비슷한 옷이 있음 (SIMILAR)",
}

PROMPT_TEMPLATE = """당신은 옷장 정리를 돕는 스타일리스트입니다. 사용자가 매장에서 본 옷을 자기 옷장과 비교한 결과를 설명합니다.
판정과 숫자는 이미 규칙으로 정해졌습니다. 판정을 바꾸거나 다른 판정을 암시하지 말고, 사라고 부추기거나 사지 말라고 말리는 문장도 쓰지 마세요.
숫자는 아래 값만 쓰고 새 숫자를 지어내지 마세요.

새 옷: {item}
판정: {verdict}
이 옷을 더하면 코디 {before}개 → {after}개 (+{plus}개)
옷장 분포: {distribution}

비슷한 내 옷 (코드 | 이름 | 비슷한 정도 | 착용 횟수 | 마지막 착용일):
{similar}

새 옷이 들어간 코디 후보 (코드 | 함께 입는 내 옷):
{candidates}

다음 JSON으로만 답하세요:
{{
  "picks": [{{"code": "K1", "label": "출근|데일리|데이트|주말 같은 2~4글자 상황"}}],
  "headline": "판정을 한 줄로 설명하는 문장",
  "reasons": ["이유 1", "이유 2", "이유 3"],
  "gap": "옷장 분포에서 보이는 빈틈 한 문장 (없으면 빈 문자열)",
  "similarNotes": [{{"code": "S1", "note": "새 옷과 다른 점 한 줄"}}]
}}

규칙:
- picks는 위 후보 코드 중에서만 최대 3개 고르세요. 후보가 없으면 빈 배열
- reasons는 정확히 3개, 각 한 문장, 한국어
- similarNotes는 비슷한 내 옷마다 하나씩 (없으면 빈 배열)"""


def _item_text(request: PurchaseExplainRequest) -> str:
    item = request.item
    parts = [CATEGORY_LABELS.get(item.category, item.category), item.color, item.fit, item.material,
             item.pattern, item.name]
    return " ".join(p for p in parts if p)


def _prompt(request: PurchaseExplainRequest) -> str:
    similar = "\n".join(
        f"- S{i + 1} | {s.name or '-'} | {round(s.similarity * 100)}% | {s.wear_count}회 | {s.last_worn_at or '없음'}"
        for i, s in enumerate(request.similar)
    ) or "- 없음"
    candidates = "\n".join(
        f"- K{i + 1} | {', '.join(c.labels) or '-'}"
        for i, c in enumerate(request.candidates)
    ) or "- 없음"
    distribution = ", ".join(
        f"{CATEGORY_LABELS.get(k, k)} {v}벌" for k, v in sorted(request.distribution.items())
    ) or "정보 없음"
    return PROMPT_TEMPLATE.format(
        item=_item_text(request), verdict=VERDICT_LABELS[request.verdict], before=request.combo_count_before,
        after=request.combo_count_after, plus=request.plus, distribution=distribution, similar=similar,
        candidates=candidates,
    )


def _sentence(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    text = value.strip()
    return text if text and len(text) <= MAX_SENTENCE_LENGTH else None


def _parse_json(content: str) -> dict[str, Any]:
    cleaned = re.sub(r"```(?:json)?\n?", "", content.strip()).strip()
    data = json.loads(cleaned)
    if not isinstance(data, dict):
        raise ValueError("model returned a non-object JSON")
    return data


def build_response(request: PurchaseExplainRequest, data: dict[str, Any]) -> PurchaseExplainResponse:
    """Validates the model output against the request. Raises ValueError when the sentences are unusable."""
    headline = _sentence(data.get("headline"))
    raw_reasons = data.get("reasons")
    reasons = [_sentence(r) for r in raw_reasons[:REASON_COUNT]] if isinstance(raw_reasons, list) else []
    if headline is None or len(reasons) < REASON_COUNT or any(r is None for r in reasons):
        raise ValueError("model returned incomplete sentences")

    by_code = {f"K{i + 1}": c.clothes_ids for i, c in enumerate(request.candidates)}
    picks: list[Pick] = []
    seen: set[str] = set()
    for pick in data.get("picks") or []:
        code = pick.get("code") if isinstance(pick, dict) else None
        if code not in by_code or code in seen or len(picks) == MAX_PICKS:
            continue
        seen.add(code)
        label = pick.get("label")
        label = label.strip() if isinstance(label, str) and 0 < len(label.strip()) <= MAX_LABEL_LENGTH else "데일리"
        picks.append(Pick(clothes_ids=list(by_code[code]), label=label))

    similar_by_code = {f"S{i + 1}": s.id for i, s in enumerate(request.similar)}
    notes: list[SimilarNote] = []
    for item in data.get("similarNotes") or []:
        if not isinstance(item, dict):
            continue
        clothes_id = similar_by_code.get(item.get("code"))
        note = _sentence(item.get("note"))
        if clothes_id and note and all(n.id != clothes_id for n in notes):
            notes.append(SimilarNote(id=clothes_id, note=note))

    return PurchaseExplainResponse(
        picks=picks, headline=headline, reasons=[r for r in reasons if r is not None], gap=_sentence(data.get("gap")),
        similar_notes=notes,
    )


async def explain_purchase(request: PurchaseExplainRequest) -> PurchaseExplainResponse:
    response = await client.chat.completions.create(
        model=MODEL,
        messages=[{"role": "user", "content": _prompt(request)}],
        max_tokens=800,
        temperature=0.4,
        response_format={"type": "json_object"},
    )
    return build_response(request, _parse_json(response.choices[0].message.content or ""))
