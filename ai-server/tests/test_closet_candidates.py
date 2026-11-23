"""FR-28: closet recommendation with rule-made candidates. OpenAI 호출은 목으로 바꾼다."""

from __future__ import annotations

import asyncio
import json
from types import SimpleNamespace
from typing import Any

import pytest

from app.schemas.outfit import ClosetRecommendRequest
from app.services import recommend_service

BASE: dict[str, Any] = {
    "weather": {"temperature": 12.0, "condition": "맑음"},
    "situation": "DAILY",
    "clothes": [
        {"id": "knit", "category": "TOP", "color": "네이비", "name": "네이비 니트"},
        {"id": "linen", "category": "TOP", "color": "베이지", "name": "베이지 린넨 셔츠"},
        {"id": "slacks", "category": "BOTTOM", "color": "블랙", "name": "블랙 슬랙스"},
        {"id": "loafers", "category": "SHOES", "color": "블랙", "name": "블랙 로퍼"},
    ],
}


@pytest.fixture()
def model(monkeypatch: pytest.MonkeyPatch) -> dict[str, Any]:
    state: dict[str, Any] = {"prompts": [], "output": {"outfits": []}}

    async def create(**kwargs: Any) -> SimpleNamespace:
        state["prompts"].append(kwargs["messages"][0]["content"])
        message = SimpleNamespace(content=json.dumps(state["output"], ensure_ascii=False))
        return SimpleNamespace(choices=[SimpleNamespace(message=message)])

    monkeypatch.setattr(recommend_service.client.chat.completions, "create", create)
    return state


def run(body: dict[str, Any]) -> Any:
    return asyncio.run(recommend_service.recommend_outfit_by_closet(ClosetRecommendRequest.model_validate(body)))


def test_candidates_and_reasons_are_in_prompt_and_outside_picks_are_dropped(model: dict[str, Any]) -> None:
    model["output"] = {"outfits": [
        {"items": ["C2", "C3", "C4"], "reason": "린넨", "style_tag": "캐주얼"},
        {"items": ["C1", "C3", "C4"], "reason": "니트", "style_tag": "미니멀"},
    ]}
    body = {**BASE, "candidates": [{"clothesIds": ["knit", "slacks", "loafers"]}],
            "appliedRules": ["TEMP_RANGE", "RAIN"]}

    result = run(body)

    prompt = model["prompts"][0]
    assert "- C1 + C3 + C4" in prompt
    assert "C2 + C3" not in prompt
    assert "외투를 벗어도" in prompt
    assert "스웨이드" in prompt
    assert [o.clothes_ids for o in result.outfits] == [["knit", "slacks", "loafers"]]
    assert result.outfits[0].reason == "니트"


def test_without_candidates_prompt_and_picks_are_unchanged(model: dict[str, Any]) -> None:
    model["output"] = {"outfits": [{"items": ["C2", "C3"], "reason": "자유 조합", "style_tag": "캐주얼"}]}

    result = run(BASE)

    assert "후보 조합" not in model["prompts"][0]
    assert [o.clothes_ids for o in result.outfits] == [["linen", "slacks"]]


def test_request_accepts_camel_case_fields() -> None:
    request = ClosetRecommendRequest.model_validate(
        {**BASE, "candidates": [{"clothesIds": ["knit"]}], "appliedRules": ["RAIN"]})
    assert request.candidates is not None and request.candidates[0].clothes_ids == ["knit"]
    assert request.applied_rules == ["RAIN"]
