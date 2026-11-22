"""FR-27 옷 상태(세탁 중)와 최근 착용 쿨다운.

가정 (NOTES.md 참고)
- PATCH /api/clothes/status {clothesIds, status: "AVAILABLE"|"LAUNDRY"} → 200
- 옷 목록에 status, available(boolean)
- 홈 코디 추천 = 기존 POST /api/outfits/recommend/closet. '후보'는 가짜 AI가 받은 clothes 목록(있으면 candidates 조합도).
  FR-28 이후 요청 본문에 lat·lon을 넣는다 (예보 캐시가 테스트끼리 섞이지 않게 매번 다른 좌표).
- '세탁 중으로 바꾼 지 4일 지남'은 clothes.status_until을 4일 앞당겨 흉내낸다 (그 값이 시작 시각이든 끝 시각이든 4일 지난 것이 된다).
- 착용 기록은 FR-10 POST /api/wears. 날짜는 백엔드가 기록하는 '오늘' 기준.
"""

from __future__ import annotations

import random
import uuid
from datetime import date, timedelta
from typing import Any

import httpx
import pytest

from acceptance.conftest import Stack, make_user

CLOSET_PATH = "/ai/outfits/recommend/closet"


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    return res.json()["data"]


def seed(client: httpx.Client, image: bytes, names: dict[str, str]) -> dict[str, str]:
    return {alias: register(client, image, filename)["id"] for alias, filename in names.items()}


def list_by_id(client: httpx.Client) -> dict[str, dict[str, Any]]:
    res = client.get("/api/clothes")
    assert res.status_code == 200, res.text
    return {c["id"]: c for c in res.json()["data"]}


def set_status(client: httpx.Client, ids: list[str], status: str) -> httpx.Response:
    return client.patch("/api/clothes/status", json={"clothesIds": ids, "status": status})


def wear_ok(client: httpx.Client, ids: list[str], day: date | None = None) -> None:
    body: dict[str, Any] = {"clothesIds": ids}
    if day is not None:
        body["date"] = day.isoformat()
    res = client.post("/api/wears", json=body)
    assert res.status_code in (200, 201), res.text


def server_today(stack: Stack, image: bytes) -> date:
    scratch = make_user(stack, "오늘확인")
    try:
        clothes_id = register(scratch.client, image, "bag_brown_tote.jpg")["id"]
        wear_ok(scratch.client, [clothes_id])
        return date.fromisoformat(str(list_by_id(scratch.client)[clothes_id]["lastWornAt"])[:10])
    finally:
        scratch.client.close()


def random_coords() -> tuple[float, float]:
    rnd = random.Random(uuid.uuid4().int)
    return round(rnd.uniform(34.0, 37.9), 2), round(rnd.uniform(126.0, 128.9), 2)


def recommend(client: httpx.Client) -> httpx.Response:
    lat, lon = random_coords()
    return client.post("/api/outfits/recommend/closet", json={
        "temperature": 12.0, "condition": "맑음", "situation": "DAILY", "lat": lat, "lon": lon})


def sent_candidates(fake_ai: httpx.Client) -> tuple[set[str], set[str]]:
    """(가짜 AI가 받은 clothes 목록의 ID, candidates 조합에 든 ID)"""
    calls = [c for c in fake_ai.get("/__calls").json() if c["path"] == CLOSET_PATH]
    assert len(calls) == 1, calls
    body = calls[0]["json"]
    clothes = {str(c["id"]) for c in body["clothes"]}
    combos = {str(i) for combo in body.get("candidates") or [] for i in combo["clothesIds"]}
    return clothes, combos


BASE = {
    "t1": "top_white_shirt.jpg", "t2": "top_navy_knit.jpg",
    "b1": "bottom_black_slacks.jpg", "b2": "bottom_beige_chino.jpg",
    "s1": "shoes_white_sneakers.jpg",
}


@pytest.mark.fr("FR-27")
def test_laundry_clothes_are_not_recommendation_candidates(user, fake_ai, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, BASE)
    res = set_status(user.client, [ids["t1"], ids["b2"]], "LAUNDRY")
    assert res.status_code == 200, res.text

    listed = list_by_id(user.client)
    for alias in ("t1", "b2"):
        assert listed[ids[alias]]["status"] == "LAUNDRY"
        assert listed[ids[alias]]["available"] is False
    for alias in ("t2", "b1", "s1"):
        assert listed[ids[alias]]["status"] == "AVAILABLE"
        assert listed[ids[alias]]["available"] is True

    fake_ai.post("/__control/reset").raise_for_status()
    res = recommend(user.client)
    assert res.status_code == 200, res.text
    clothes, combos = sent_candidates(fake_ai)
    assert clothes == {ids["t2"], ids["b1"], ids["s1"]}
    assert not combos & {ids["t1"], ids["b2"]}
    returned = {item["id"] for o in res.json()["data"]["outfits"] for item in o["items"]}
    assert not returned & {ids["t1"], ids["b2"]}

    # 세탁이 끝나면 다시 후보가 된다
    assert set_status(user.client, [ids["t1"], ids["b2"]], "AVAILABLE").status_code == 200
    fake_ai.post("/__control/reset").raise_for_status()
    assert recommend(user.client).status_code == 200
    clothes, _ = sent_candidates(fake_ai)
    assert clothes == set(ids.values())


@pytest.mark.fr("FR-27")
def test_top_worn_yesterday_is_on_cooldown_but_four_days_ago_is_not(stack, user, fake_ai, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, {**BASE, "t3": "top_grey_tee.jpg"})
    today = server_today(stack, tiny_jpg)
    wear_ok(user.client, [ids["t1"]], today - timedelta(days=1))
    wear_ok(user.client, [ids["t2"]], today - timedelta(days=4))

    listed = list_by_id(user.client)
    assert listed[ids["t1"]]["available"] is False
    assert listed[ids["t1"]]["status"] == "AVAILABLE"  # 쿨다운은 상태를 바꾸지 않는다
    assert listed[ids["t2"]]["available"] is True

    fake_ai.post("/__control/reset").raise_for_status()
    res = recommend(user.client)
    assert res.status_code == 200, res.text
    clothes, combos = sent_candidates(fake_ai)
    assert ids["t1"] not in clothes and ids["t1"] not in combos
    assert {ids["t2"], ids["t3"]} <= clothes


@pytest.mark.fr("FR-27")
def test_shoes_and_outer_have_no_cooldown(stack, user, fake_ai, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, {**BASE, "o1": "outer_grey_coat.jpg"})
    today = server_today(stack, tiny_jpg)
    wear_ok(user.client, [ids["s1"], ids["o1"]], today - timedelta(days=1))

    listed = list_by_id(user.client)
    assert listed[ids["s1"]]["available"] is True
    assert listed[ids["o1"]]["available"] is True
    fake_ai.post("/__control/reset").raise_for_status()
    assert recommend(user.client).status_code == 200
    clothes, _ = sent_candidates(fake_ai)
    assert {ids["s1"], ids["o1"]} <= clothes


@pytest.mark.fr("FR-27")
def test_laundry_becomes_available_after_four_days(stack, user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, BASE)
    assert set_status(user.client, [ids["t1"]], "LAUNDRY").status_code == 200
    assert list_by_id(user.client)[ids["t1"]]["available"] is False

    stack.sql(f"UPDATE clothes SET status_until = status_until - interval '4 days' WHERE id = '{ids['t1']}'")
    assert list_by_id(user.client)[ids["t1"]]["available"] is True


@pytest.mark.fr("FR-27")
def test_status_change_with_other_users_clothes_is_rejected_atomically(stack, user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, BASE)
    other = make_user(stack, "남의세탁")
    try:
        theirs = register(other.client, tiny_jpg, "top_grey_tee.jpg")["id"]
        res = set_status(user.client, [ids["t1"], theirs], "LAUNDRY")
        assert res.status_code == 400, res.text
        assert res.json()["success"] is False
        assert list_by_id(user.client)[ids["t1"]]["status"] == "AVAILABLE"
        assert list_by_id(other.client)[theirs]["status"] == "AVAILABLE"
    finally:
        other.client.close()


@pytest.mark.fr("FR-27")
def test_combo_count_ignores_laundry_and_cooldown(stack, user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, BASE)
    summary = user.client.get("/api/clothes/summary")
    assert summary.status_code == 200, summary.text
    assert summary.json()["data"]["comboCount"] == 4
    counts_before = {i: c["comboCount"] for i, c in list_by_id(user.client).items()}

    assert set_status(user.client, [ids["t1"], ids["b1"]], "LAUNDRY").status_code == 200
    wear_ok(user.client, [ids["t2"]], server_today(stack, tiny_jpg) - timedelta(days=1))

    assert user.client.get("/api/clothes/summary").json()["data"]["comboCount"] == 4
    assert {i: c["comboCount"] for i, c in list_by_id(user.client).items()} == counts_before
