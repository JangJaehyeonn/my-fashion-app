"""FR-07: 옷 분류 응답의 fit·material·pattern·season (모르면 null)."""

from __future__ import annotations

import asyncio
import json
from types import SimpleNamespace
from typing import Any

import pytest

from app.services import clothes_service


def fake_completion(payload: dict[str, Any]) -> SimpleNamespace:
    message = SimpleNamespace(content=json.dumps(payload, ensure_ascii=False))
    return SimpleNamespace(choices=[SimpleNamespace(message=message, finish_reason="stop")])


def classify_with(monkeypatch: pytest.MonkeyPatch, payload: dict[str, Any]) -> dict[str, Any]:
    async def create(**_: Any) -> SimpleNamespace:
        return fake_completion(payload)

    monkeypatch.setattr(clothes_service.client.chat.completions, "create", create)
    result = asyncio.run(clothes_service.classify_clothes_image(b"img", "image/jpeg"))
    return result.model_dump(by_alias=True)


def test_attributes_are_returned(monkeypatch: pytest.MonkeyPatch) -> None:
    body = classify_with(monkeypatch, {
        "category": "TOP", "color": "네이비", "name": "네이비 울 니트",
        "fit": "슬림", "material": "울", "pattern": "스트라이프", "season": "winter",
    })
    assert body["fit"] == "슬림"
    assert body["material"] == "울"
    assert body["pattern"] == "스트라이프"
    assert body["season"] == "WINTER"


def test_missing_attributes_are_null_keys(monkeypatch: pytest.MonkeyPatch) -> None:
    body = classify_with(monkeypatch, {"category": "SHOES", "color": "화이트", "name": "화이트 스니커즈"})
    for key in ("fit", "material", "pattern", "season"):
        assert key in body
        assert body[key] is None


@pytest.mark.parametrize("value", [None, "", "  ", "null", "모름", "Unknown", 3, "가" * 51])
def test_unknown_attribute_values_become_null(monkeypatch: pytest.MonkeyPatch, value: Any) -> None:
    body = classify_with(monkeypatch, {"category": "TOP", "color": "블랙", "name": "티셔츠", "material": value})
    assert body["material"] is None


@pytest.mark.parametrize("value", ["AUTUMN", "사계절", "", None, 1])
def test_season_outside_list_is_null(monkeypatch: pytest.MonkeyPatch, value: Any) -> None:
    body = classify_with(monkeypatch, {"category": "TOP", "color": "블랙", "name": "티셔츠", "season": value})
    assert body["season"] is None


def test_prompt_asks_for_attributes_or_null() -> None:
    for key in ('"fit"', '"material"', '"pattern"', '"season"', "null"):
        assert key in clothes_service._CLASSIFY_PROMPT
