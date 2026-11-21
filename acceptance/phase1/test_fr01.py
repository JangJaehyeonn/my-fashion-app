"""FR-01 AI 분류 실패 시 미분류로 저장.

가정 (NOTES.md 참고)
- 옷 응답의 분류 실패 표시: `classificationFailed` (boolean), 미분류 카테고리 값: `"UNCLASSIFIED"`
- 직접 수정: PATCH /api/clothes/{id} {category, color, name}
- 재분류: POST /api/clothes/{id}/reclassify
"""

from __future__ import annotations

from typing import Any

import httpx
import pytest

UNCLASSIFIED = "UNCLASSIFIED"
CLOSET_REQUEST = {"temperature": 12.0, "condition": "맑음", "situation": "DAILY"}


def register(client: httpx.Client, image: bytes, filename: str) -> httpx.Response:
    return client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})


def register_ok(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = register(client, image, filename)
    assert res.status_code == 201, res.text
    body = res.json()
    assert body["success"] is True
    return body["data"]


def set_mode(fake_ai: httpx.Client, mode: str) -> None:
    fake_ai.post("/__control", json={"mode": mode}).raise_for_status()


def list_by_id(client: httpx.Client) -> dict[str, dict[str, Any]]:
    res = client.get("/api/clothes")
    assert res.status_code == 200, res.text
    return {c["id"]: c for c in res.json()["data"]}


def assert_unclassified(clothes: dict[str, Any]) -> None:
    assert clothes["classificationFailed"] is True
    assert clothes["category"] == UNCLASSIFIED
    # 사진은 저장된다
    assert clothes["imageUrl"]


# ── 등록 시 분류 실패 ──────────────────────────────────────────


@pytest.mark.fr("FR-01")
def test_register_when_ai_down_saves_as_unclassified(user, fake_ai, tiny_jpg) -> None:
    set_mode(fake_ai, "down")
    data = register_ok(user.client, tiny_jpg, "top_navy_knit.jpg")
    assert_unclassified(data)

    listed = list_by_id(user.client)
    assert data["id"] in listed
    assert_unclassified(listed[data["id"]])


@pytest.mark.fr("FR-01")
def test_register_when_ai_returns_bad_format_saves_as_unclassified(user, fake_ai, tiny_jpg) -> None:
    set_mode(fake_ai, "bad")
    data = register_ok(user.client, tiny_jpg, "top_navy_knit.jpg")
    assert_unclassified(data)


@pytest.mark.fr("FR-01")
def test_register_when_ai_returns_5xx_saves_as_unclassified(user, fake_ai, tiny_jpg) -> None:
    # 가짜 AI는 fail_ 접두어 파일 이름에 500을 돌려준다
    data = register_ok(user.client, tiny_jpg, "fail_top_navy_knit.jpg")
    assert_unclassified(data)


@pytest.mark.fr("FR-01")
def test_register_when_ai_ok_is_classified(user, fake_ai, tiny_jpg) -> None:
    data = register_ok(user.client, tiny_jpg, "top_navy_knit.jpg")
    assert data["classificationFailed"] is False
    assert data["category"] == "TOP"
    assert data["color"] == "네이비"
    assert data["name"] == "네이비 knit"


# ── 직접 수정 ──────────────────────────────────────────────────


@pytest.mark.fr("FR-01")
def test_patch_unclassified_clothes_is_reflected_in_list(user, fake_ai, tiny_jpg) -> None:
    set_mode(fake_ai, "down")
    created = register_ok(user.client, tiny_jpg, "top_navy_knit.jpg")
    set_mode(fake_ai, "normal")

    res = user.client.patch(
        f"/api/clothes/{created['id']}",
        json={"category": "BOTTOM", "color": "블랙", "name": "블랙 슬랙스"},
    )
    assert res.status_code == 200, res.text
    assert res.json()["success"] is True

    listed = list_by_id(user.client)[created["id"]]
    assert listed["category"] == "BOTTOM"
    assert listed["color"] == "블랙"
    assert listed["name"] == "블랙 슬랙스"


# ── 재분류 ─────────────────────────────────────────────────────


@pytest.mark.fr("FR-01")
def test_reclassify_when_ai_up_updates_with_classification(user, fake_ai, tiny_jpg) -> None:
    set_mode(fake_ai, "down")
    created = register_ok(user.client, tiny_jpg, "top_navy_knit.jpg")
    set_mode(fake_ai, "normal")
    fake_ai.post("/__control/reset").raise_for_status()

    res = user.client.post(f"/api/clothes/{created['id']}/reclassify")
    assert res.status_code == 200, res.text
    data = res.json()["data"]

    # 백엔드가 가짜 AI로 보낸 사진 파일 이름으로 기대 분류를 정한다 (저장된 사진의 이름은 구현이 정함)
    calls = [c for c in fake_ai.get("/__calls").json() if c["path"] == "/ai/clothes/classify"]
    assert len(calls) == 1, calls
    sent_name = calls[0]["files"][0]
    expected = fake_ai.post(
        "/ai/clothes/classify", files={"image": (sent_name, tiny_jpg, "image/jpeg")}
    ).json()

    assert data["id"] == created["id"]
    assert data["classificationFailed"] is False
    assert data["category"] == expected["category"]
    assert data["color"] == expected["color"]
    assert data["name"] == expected["name"]

    listed = list_by_id(user.client)[created["id"]]
    assert listed["classificationFailed"] is False
    assert listed["category"] == expected["category"]


@pytest.mark.fr("FR-01")
def test_reclassify_when_ai_down_returns_503_and_keeps_values(user, fake_ai, tiny_jpg) -> None:
    set_mode(fake_ai, "down")
    created = register_ok(user.client, tiny_jpg, "top_navy_knit.jpg")
    before = list_by_id(user.client)[created["id"]]

    res = user.client.post(f"/api/clothes/{created['id']}/reclassify")
    assert res.status_code == 503, res.text
    body = res.json()
    assert body["success"] is False
    assert isinstance(body["message"], str) and body["message"].strip()

    after = list_by_id(user.client)[created["id"]]
    for key in ("category", "color", "name", "classificationFailed", "imageUrl"):
        assert after[key] == before[key], key


@pytest.mark.fr("FR-01")
def test_reclassify_when_ai_down_keeps_user_edited_values(user, fake_ai, tiny_jpg) -> None:
    set_mode(fake_ai, "down")
    created = register_ok(user.client, tiny_jpg, "top_navy_knit.jpg")
    res = user.client.patch(
        f"/api/clothes/{created['id']}",
        json={"category": "TOP", "color": "네이비", "name": "네이비 니트"},
    )
    assert res.status_code == 200, res.text

    res = user.client.post(f"/api/clothes/{created['id']}/reclassify")
    assert res.status_code == 503, res.text

    after = list_by_id(user.client)[created["id"]]
    assert after["category"] == "TOP"
    assert after["color"] == "네이비"
    assert after["name"] == "네이비 니트"


# ── 코디 추천에서 제외 ─────────────────────────────────────────


def closet_calls(fake_ai: httpx.Client) -> list[dict[str, Any]]:
    return [c for c in fake_ai.get("/__calls").json() if c["path"] == "/ai/outfits/recommend/closet"]


def outfit_item_ids(res: httpx.Response) -> set[str]:
    outfits = res.json()["data"]["outfits"]
    return {item["id"] for outfit in outfits for item in outfit["items"]}


@pytest.mark.fr("FR-01")
def test_closet_recommend_with_only_unclassified_clothes_does_not_fail(user, fake_ai, tiny_jpg) -> None:
    set_mode(fake_ai, "down")
    unclassified = {
        register_ok(user.client, tiny_jpg, name)["id"]
        for name in ("top_navy_knit.jpg", "bottom_black_slacks.jpg", "shoes_white_sneakers.jpg")
    }
    set_mode(fake_ai, "normal")
    fake_ai.post("/__control/reset").raise_for_status()

    res = user.client.post("/api/outfits/recommend/closet", json=CLOSET_REQUEST)
    assert res.status_code < 500, res.text
    if res.status_code == 200:
        assert not (outfit_item_ids(res) & unclassified)
    for call in closet_calls(fake_ai):
        sent = {str(c["id"]) for c in call["json"]["clothes"]}
        assert not (sent & unclassified)


@pytest.mark.fr("FR-01")
def test_closet_recommend_excludes_unclassified_clothes(user, fake_ai, tiny_jpg) -> None:
    classified = {
        register_ok(user.client, tiny_jpg, name)["id"]
        for name in ("top_navy_knit.jpg", "bottom_black_slacks.jpg", "shoes_white_sneakers.jpg")
    }
    set_mode(fake_ai, "down")
    unclassified = {
        register_ok(user.client, tiny_jpg, name)["id"]
        for name in ("top_white_shirt.jpg", "bottom_blue_jeans.jpg")
    }
    set_mode(fake_ai, "normal")
    fake_ai.post("/__control/reset").raise_for_status()

    res = user.client.post("/api/outfits/recommend/closet", json=CLOSET_REQUEST)
    assert res.status_code == 200, res.text
    ids = outfit_item_ids(res)
    assert ids, "분류된 상의·하의·신발이 있으므로 가짜 AI가 코디 1개를 돌려준다"
    assert not (ids & unclassified)

    calls = closet_calls(fake_ai)
    assert len(calls) == 1
    sent = {str(c["id"]) for c in calls[0]["json"]["clothes"]}
    assert sent == classified
