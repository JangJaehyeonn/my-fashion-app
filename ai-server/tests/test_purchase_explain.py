"""FR-11: POST /ai/purchase-check/explain. OpenAI 호출은 목으로 바꾼다."""

from __future__ import annotations

import json
from types import SimpleNamespace
from typing import Any

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.routers import purchase as purchase_router
from app.services import purchase_service

REQUEST: dict[str, Any] = {
    "item": {"category": "TOP", "color": "블랙", "name": "블랙 후드", "fit": None},
    "verdict": "SIMILAR",
    "plus": 4,
    "comboCountBefore": 8,
    "comboCountAfter": 12,
    "similar": [{"id": "s-1", "name": "그레이 후드", "similarity": 0.92, "wearCount": 3, "lastWornAt": "2026-11-01"}],
    "distribution": {"TOP": 2, "BOTTOM": 2, "SHOES": 2},
    "candidates": [
        {"clothesIds": ["b1", "s1"], "labels": ["하의 블랙 슬랙스", "신발 화이트 스니커즈"]},
        {"clothesIds": ["b2", "s1"]},
    ],
}


def model_output(**overrides: Any) -> dict[str, Any]:
    data: dict[str, Any] = {
        "picks": [{"code": "K2", "label": "출근"}, {"code": "K9", "label": "데일리"}, {"code": "K2", "label": "중복"}],
        "headline": "옷장에 비슷한 후드가 있어요",
        "reasons": ["이유 1", "이유 2", "이유 3", "이유 4"],
        "gap": "아우터가 없어요",
        "similarNotes": [{"code": "S1", "note": "색만 달라요"}, {"code": "S7", "note": "없는 옷"}],
    }
    data.update(overrides)
    return data


@pytest.fixture()
def calls(monkeypatch: pytest.MonkeyPatch) -> list[dict[str, Any]]:
    recorded: list[dict[str, Any]] = []
    output: dict[str, Any] = {"content": json.dumps(model_output(), ensure_ascii=False)}

    async def create(**kwargs: Any) -> SimpleNamespace:
        recorded.append(kwargs)
        message = SimpleNamespace(content=output["content"])
        return SimpleNamespace(choices=[SimpleNamespace(message=message)])

    monkeypatch.setattr(purchase_service.client.chat.completions, "create", create)
    recorded.append({"_output": output})
    return recorded


def set_output(calls: list[dict[str, Any]], content: str) -> None:
    calls[0]["_output"]["content"] = content


@pytest.fixture()
def client() -> TestClient:
    app = FastAPI()
    app.include_router(purchase_router.router)
    return TestClient(app)


def test_picks_only_candidates_and_keeps_sentences(client: TestClient, calls: list[dict[str, Any]]) -> None:
    res = client.post("/ai/purchase-check/explain", json=REQUEST)
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["picks"] == [{"clothesIds": ["b2", "s1"], "label": "출근"}]
    assert body["headline"] == "옷장에 비슷한 후드가 있어요"
    assert body["reasons"] == ["이유 1", "이유 2", "이유 3"]
    assert body["gap"] == "아우터가 없어요"
    assert body["similarNotes"] == [{"id": "s-1", "note": "색만 달라요"}]
    assert "verdict" not in body


def test_prompt_carries_rule_results(client: TestClient, calls: list[dict[str, Any]]) -> None:
    client.post("/ai/purchase-check/explain", json=REQUEST)
    sent = calls[1]
    assert sent["model"] == "gpt-4o-mini"
    assert sent["response_format"] == {"type": "json_object"}
    prompt = sent["messages"][0]["content"]
    assert "SIMILAR" in prompt and "8개 → 12개 (+4개)" in prompt
    assert "S1 | 그레이 후드 | 92% | 3회 | 2026-11-01" in prompt
    assert "K1 | 하의 블랙 슬랙스, 신발 화이트 스니커즈" in prompt
    assert "부추기거나" in prompt


@pytest.mark.parametrize(
    "output",
    [
        "not json",
        json.dumps(model_output(reasons=["이유 1", "이유 2"])),
        json.dumps(model_output(headline=" ")),
        json.dumps(model_output(reasons=["이유 1", "", "이유 3"])),
        json.dumps(["list"]),
    ],
)
def test_unusable_output_is_502(client: TestClient, calls: list[dict[str, Any]], output: str) -> None:
    set_output(calls, output)
    res = client.post("/ai/purchase-check/explain", json=REQUEST)
    assert res.status_code == 502


def test_empty_gap_becomes_null(client: TestClient, calls: list[dict[str, Any]]) -> None:
    set_output(calls, json.dumps(model_output(gap="", picks=[])))
    body = client.post("/ai/purchase-check/explain", json=REQUEST).json()
    assert body["gap"] is None
    assert body["picks"] == []


def test_invalid_verdict_is_422(client: TestClient, calls: list[dict[str, Any]]) -> None:
    res = client.post("/ai/purchase-check/explain", json={**REQUEST, "verdict": "MAYBE"})
    assert res.status_code == 422
    assert len(calls) == 1  # only the output holder, OpenAI not called
