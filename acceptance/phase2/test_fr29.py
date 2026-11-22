"""FR-29 안 입는 옷 살리기.

가정 (NOTES.md 참고)
- 홈 코디 추천 = POST /api/outfits/recommend/closet (본문에 lat·lon).
- 응답 data.outfits[]에 revived(boolean, 없으면 false로 봄)와 revivedClothesId. revived 조합은 응답에 꼭 들어간다
  (가짜 AI는 기존 규칙대로 clothes 목록의 첫 상의·하의·신발을 고르므로, AI가 고르지 않아도 백엔드가 넣어야 한다).
- 가짜 AI에 보내는 candidates의 맨 앞 조합에 오래 안 입은 옷이 든다.
- '60일 넘게 안 입은 옷'은 등록일(clothes.created_at)을 90일 앞당기고 70일 전 착용 기록 하나만 둔 옷으로 만든다
  (착용 기록만 보든 등록일도 보든 오래 안 입은 옷이 된다). 나머지 옷은 5일 전에 입어 쿨다운(상의 3·하의 2일)에 걸리지 않는다.
- 색 규칙은 FR-09 가정과 같다 (기본색끼리 어울림, 레드–그린 충돌).
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
        listed = {c["id"]: c for c in scratch.client.get("/api/clothes").json()["data"]}
        return date.fromisoformat(str(listed[clothes_id]["lastWornAt"])[:10])
    finally:
        scratch.client.close()


def recommend(client: httpx.Client) -> httpx.Response:
    rnd = random.Random(uuid.uuid4().int)
    return client.post("/api/outfits/recommend/closet", json={
        "temperature": 12.0, "condition": "맑음", "situation": "DAILY",
        "lat": round(rnd.uniform(34.0, 37.9), 2), "lon": round(rnd.uniform(126.0, 128.9), 2)})


def revived_outfits(res: httpx.Response) -> list[dict[str, Any]]:
    assert res.status_code == 200, res.text
    return [o for o in res.json()["data"]["outfits"] if o.get("revived")]


def closet_calls(fake_ai: httpx.Client) -> list[dict[str, Any]]:
    return [c["json"] for c in fake_ai.get("/__calls").json() if c["path"] == CLOSET_PATH]


def make_old_closet(stack: Stack, user, image: bytes, names: dict[str, str], old: str) -> dict[str, str]:
    ids = seed(user.client, image, names)
    today = server_today(stack, image)
    wear_ok(user.client, [i for alias, i in ids.items() if alias != old], today - timedelta(days=5))
    wear_ok(user.client, [ids[old]], today - timedelta(days=70))
    stack.sql(f"UPDATE clothes SET created_at = now() - interval '90 days' WHERE id = '{ids[old]}'")
    return ids


CLOSET = {
    "old_top": "top_white_shirt.jpg", "t2": "top_navy_knit.jpg",
    "b1": "bottom_black_slacks.jpg", "b2": "bottom_beige_chino.jpg",
    "s1": "shoes_white_sneakers.jpg",
}


@pytest.mark.fr("FR-29")
def test_first_recommendation_revives_long_unworn_clothes(stack, user, fake_ai, tiny_jpg) -> None:
    ids = make_old_closet(stack, user, tiny_jpg, CLOSET, old="old_top")
    fake_ai.post("/__control/reset").raise_for_status()

    res = recommend(user.client)
    revived = revived_outfits(res)
    assert len(revived) >= 1, res.text
    outfit = revived[0]
    assert outfit["revived"] is True
    assert outfit["revivedClothesId"] == ids["old_top"]
    assert ids["old_top"] in {item["id"] for item in outfit["items"]}

    calls = closet_calls(fake_ai)
    assert len(calls) == 1
    first = [str(i) for i in calls[0]["candidates"][0]["clothesIds"]]
    assert ids["old_top"] in first


@pytest.mark.fr("FR-29")
def test_second_recommendation_in_same_week_has_no_revived(stack, user, tiny_jpg) -> None:
    make_old_closet(stack, user, tiny_jpg, CLOSET, old="old_top")
    assert revived_outfits(recommend(user.client))
    assert revived_outfits(recommend(user.client)) == []


@pytest.mark.fr("FR-29")
def test_recently_worn_closet_has_no_revived(stack, user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, CLOSET)
    wear_ok(user.client, list(ids.values()), server_today(stack, tiny_jpg) - timedelta(days=5))
    assert revived_outfits(recommend(user.client)) == []


@pytest.mark.fr("FR-29")
@pytest.mark.parametrize(("names", "old"), [
    # 기타 옷은 코디(상의·하의·신발·아우터)에 들어가지 않는다
    pytest.param({**CLOSET, "bag": "bag_brown_tote.jpg"}, "bag", id="etc"),
    # 레드 상의의 조합은 모두 레드–그린 충돌이다
    pytest.param({"red_top": "top_red_tee.jpg", "t2": "top_white_shirt.jpg",
                  "green": "bottom_green_pants.jpg", "s1": "shoes_black_loafers.jpg"}, "red_top", id="color-conflict"),
])
def test_no_revived_when_old_clothes_has_no_valid_combo(stack, user, fake_ai, tiny_jpg, names, old) -> None:
    ids = make_old_closet(stack, user, tiny_jpg, names, old=old)
    fake_ai.post("/__control/reset").raise_for_status()

    res = recommend(user.client)
    assert revived_outfits(res) == []
    for body in closet_calls(fake_ai):
        for combo in body.get("candidates") or []:
            assert ids[old] not in map(str, combo["clothesIds"])
