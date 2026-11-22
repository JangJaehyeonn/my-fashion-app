"""FR-07: POST /ai/embed (OpenAI text-embedding-3-small, 1536차원). OpenAI 호출은 목으로 바꾼다."""

from __future__ import annotations

from types import SimpleNamespace
from typing import Any

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.routers import embedding as embedding_router
from app.services import embedding_service

DIMS = embedding_service.EMBEDDING_DIMENSIONS


@pytest.fixture()
def calls(monkeypatch: pytest.MonkeyPatch) -> list[dict[str, Any]]:
    recorded: list[dict[str, Any]] = []

    async def create(**kwargs: Any) -> SimpleNamespace:
        recorded.append(kwargs)
        inputs: list[str] = kwargs["input"]
        # 순서가 뒤섞여 와도 index로 맞추는지 보려고 역순으로 돌려준다
        data = [SimpleNamespace(index=i, embedding=[float(i)] * DIMS) for i in range(len(inputs))]
        return SimpleNamespace(data=list(reversed(data)))

    monkeypatch.setattr(embedding_service.client.embeddings, "create", create)
    return recorded


@pytest.fixture()
def client() -> TestClient:
    app = FastAPI()
    app.include_router(embedding_router.router)
    return TestClient(app)


def test_single_text(client: TestClient, calls: list[dict[str, Any]]) -> None:
    res = client.post("/ai/embed", json={"text": "상의 네이비 울 니트"})
    assert res.status_code == 200
    body = res.json()
    assert list(body) == ["embedding"]
    assert len(body["embedding"]) == DIMS
    assert calls[0]["model"] == "text-embedding-3-small"
    assert calls[0]["input"] == ["상의 네이비 울 니트"]


def test_batch_keeps_order(client: TestClient, calls: list[dict[str, Any]]) -> None:
    res = client.post("/ai/embed", json={"texts": ["a", "b", "c"]})
    assert res.status_code == 200
    embeddings = res.json()["embeddings"]
    assert [v[0] for v in embeddings] == [0.0, 1.0, 2.0]
    assert all(len(v) == DIMS for v in embeddings)


@pytest.mark.parametrize("payload", [{}, {"text": ""}, {"text": "a", "texts": ["b"]}, {"texts": []}, {"texts": [""]}])
def test_invalid_request_is_422(client: TestClient, calls: list[dict[str, Any]], payload: dict[str, Any]) -> None:
    assert client.post("/ai/embed", json=payload).status_code == 422
    assert calls == []


def test_wrong_dimensions_raise(monkeypatch: pytest.MonkeyPatch) -> None:
    import asyncio

    async def create(**_: Any) -> SimpleNamespace:
        return SimpleNamespace(data=[SimpleNamespace(index=0, embedding=[0.0] * 3)])

    monkeypatch.setattr(embedding_service.client.embeddings, "create", create)
    with pytest.raises(ValueError):
        asyncio.run(embedding_service.embed_texts(["a"]))
