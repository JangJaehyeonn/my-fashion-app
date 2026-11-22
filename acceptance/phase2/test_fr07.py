"""FR-07 pgvector와 옷 속성·임베딩.

가정 (NOTES.md 참고)
- 옷 응답에 fit·material·pattern·season 키가 늘 있다 (모르면 null).
- 임베딩은 clothes.embedding(vector(1536))에 저장한다. 임베딩 요청은 POST /ai/embed {"text": ...}.
- 속성 수정은 FR-01의 PATCH /api/clothes/{id}가 fit·material·pattern·season도 받는다 (null은 그대로 둠).
- 가짜 AI의 속성 추출은 /__control/attributes로 켠다 (기본은 기존 분류 응답 그대로).
"""

from __future__ import annotations

from typing import Any

import httpx
import pytest

from acceptance.conftest import Stack

ATTRIBUTE_KEYS = ("fit", "material", "pattern", "season")
# FR-07 이전 ClothesResponse 필드. 새 필드 추가만 허용된다.
CLOTHES_RESPONSE_KEYS = {"id", "imageUrl", "category", "color", "name", "classificationFailed", "createdAt"}
EMBED_PATH = "/ai/embed"


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    body = res.json()
    assert body["success"] is True
    return body["data"]


def list_by_id(client: httpx.Client) -> dict[str, dict[str, Any]]:
    res = client.get("/api/clothes")
    assert res.status_code == 200, res.text
    return {c["id"]: c for c in res.json()["data"]}


def enable_attributes(fake_ai: httpx.Client) -> None:
    fake_ai.post("/__control/attributes", json={"enabled": True}).raise_for_status()


def embedding_text(stack: Stack, clothes_id: str) -> str:
    """임베딩 값(텍스트 표현). 비어 있으면 빈 문자열."""
    return stack.sql(f"SELECT coalesce(embedding::text, '') FROM clothes WHERE id = '{clothes_id}'")


def embed_texts(fake_ai: httpx.Client) -> list[str]:
    texts: list[str] = []
    for call in fake_ai.get("/__calls").json():
        if call["path"] != EMBED_PATH or not call["json"]:
            continue
        if isinstance(call["json"].get("text"), str):
            texts.append(call["json"]["text"])
        texts.extend(str(t) for t in call["json"].get("texts") or [])
    return texts


@pytest.mark.fr("FR-07")
def test_register_returns_attributes_and_stores_embedding(stack, user, fake_ai, tiny_jpg) -> None:
    enable_attributes(fake_ai)
    data = register(user.client, tiny_jpg, "top_navy_wool_slim_stripe_knit_winter.jpg")

    assert data["category"] == "TOP"
    assert data["fit"] == "슬림"
    assert data["material"] == "울"
    assert data["pattern"] == "스트라이프"
    assert data["season"] == "WINTER"

    assert stack.sql(f"SELECT vector_dims(embedding) FROM clothes WHERE id = '{data['id']}'") == "1536"

    # 임베딩 텍스트는 '카테고리 색상 핏 소재 패턴 이름'(null 제외)
    texts = embed_texts(fake_ai)
    assert texts, "옷 등록 때 임베딩을 요청해야 한다"
    text = texts[-1]
    for part in ("네이비", "슬림", "울", "스트라이프", data["name"]):
        assert part in text, (part, text)


@pytest.mark.fr("FR-07")
def test_unknown_attributes_are_null(user, fake_ai, tiny_jpg) -> None:
    enable_attributes(fake_ai)
    data = register(user.client, tiny_jpg, "shoes_white_sneakers.jpg")
    for key in ATTRIBUTE_KEYS:
        assert key in data, key
        assert data[key] is None, key


@pytest.mark.fr("FR-07")
def test_attributes_are_null_when_ai_does_not_send_them(user, tiny_jpg) -> None:
    # 속성 추출을 켜지 않으면 가짜 AI는 기존 응답({category, color, name})만 준다
    data = register(user.client, tiny_jpg, "top_navy_knit.jpg")
    assert data["category"] == "TOP"
    for key in ATTRIBUTE_KEYS:
        assert key in data and data[key] is None, key


@pytest.mark.fr("FR-07")
def test_embedding_down_still_registers_without_embedding(stack, user, fake_ai, tiny_jpg) -> None:
    enable_attributes(fake_ai)
    fake_ai.post("/__control/paths", json={EMBED_PATH: "down"}).raise_for_status()

    data = register(user.client, tiny_jpg, "top_navy_wool_knit.jpg")
    # 분류는 살아 있으므로 정상 분류된다
    assert data["classificationFailed"] is False
    assert data["category"] == "TOP"
    assert data["material"] == "울"
    assert embedding_text(stack, data["id"]) == ""
    assert data["id"] in list_by_id(user.client)


@pytest.mark.fr("FR-07")
def test_embedding_bad_response_still_registers_without_embedding(stack, user, fake_ai, tiny_jpg) -> None:
    fake_ai.post("/__control/paths", json={EMBED_PATH: "bad"}).raise_for_status()
    data = register(user.client, tiny_jpg, "bottom_black_slacks.jpg")
    assert data["category"] == "BOTTOM"
    assert embedding_text(stack, data["id"]) == ""


@pytest.mark.fr("FR-07")
def test_patching_attributes_recomputes_embedding(stack, user, fake_ai, tiny_jpg) -> None:
    enable_attributes(fake_ai)
    data = register(user.client, tiny_jpg, "top_navy_wool_knit.jpg")
    before = embedding_text(stack, data["id"])
    assert before

    fake_ai.post("/__control/reset").raise_for_status()
    res = user.client.patch(f"/api/clothes/{data['id']}", json={"material": "면", "fit": "오버핏"})
    assert res.status_code == 200, res.text
    patched = res.json()["data"]
    assert patched["material"] == "면"
    assert patched["fit"] == "오버핏"

    texts = embed_texts(fake_ai)
    assert texts, "속성을 고치면 임베딩을 다시 요청해야 한다"
    assert "면" in texts[-1] and "오버핏" in texts[-1], texts[-1]

    after = embedding_text(stack, data["id"])
    assert after and after != before

    listed = list_by_id(user.client)[data["id"]]
    assert listed["material"] == "면"
    assert listed["fit"] == "오버핏"


@pytest.mark.fr("FR-07")
def test_reclassify_recomputes_embedding(stack, user, fake_ai, tiny_jpg) -> None:
    fake_ai.post("/__control", json={"mode": "down"}).raise_for_status()
    data = register(user.client, tiny_jpg, "top_navy_knit.jpg")
    fake_ai.post("/__control/reset").raise_for_status()

    res = user.client.post(f"/api/clothes/{data['id']}/reclassify")
    assert res.status_code == 200, res.text
    assert embed_texts(fake_ai), "재분류 후 임베딩을 요청해야 한다"
    assert embedding_text(stack, data["id"]) != ""


@pytest.mark.fr("FR-07")
def test_clothes_list_keeps_existing_fields(user, fake_ai, tiny_jpg) -> None:
    enable_attributes(fake_ai)
    register(user.client, tiny_jpg, "top_navy_wool_knit.jpg")
    fake_ai.post("/__control", json={"mode": "down"}).raise_for_status()
    register(user.client, tiny_jpg, "bottom_black_slacks.jpg")

    for clothes in list_by_id(user.client).values():
        missing = CLOTHES_RESPONSE_KEYS - set(clothes)
        assert not missing, f"기존 응답 필드가 빠졌습니다: {missing}"
        for key in ATTRIBUTE_KEYS:
            assert key in clothes, key
