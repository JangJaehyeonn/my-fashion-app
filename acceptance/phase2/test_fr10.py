"""FR-10 착용 기록 API.

가정 (NOTES.md 참고)
- POST /api/wears {clothesIds, date: "YYYY-MM-DD"(선택)} → 200 또는 201
- 옷 목록의 wearCount(정수), lastWornAt("YYYY-MM-DD"로 시작하는 문자열 또는 null)
- GET /api/wears/summary → data.weekCount, data.monthCount, data.mostWorn{id, name}
- 착용 기록 테이블: wear_records(user_id, ...)
- '오늘'은 백엔드 시계가 정한다. 날짜를 직접 넣는 테스트는 백엔드가 기록한 오늘(scratch 사용자로 확인)을 기준으로 한다.
- 요약의 '횟수'가 옷 단위인지 날짜 단위인지 요구사항에 없어서, 요약 테스트는 날짜마다 옷 1벌만 기록해 두 해석이 같은 값이 되게 한다.
  주의 시작 요일(월·일)도 정해져 있지 않아, 어느 쪽으로 보든 같은 결과가 나오는 날짜만 쓴다.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
from typing import Any

import httpx
import pytest

from acceptance.conftest import Stack, make_user


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    return res.json()["data"]


def wear(client: httpx.Client, ids: list[str], day: date | None = None) -> httpx.Response:
    body: dict[str, Any] = {"clothesIds": ids}
    if day is not None:
        body["date"] = day.isoformat()
    return client.post("/api/wears", json=body)


def wear_ok(client: httpx.Client, ids: list[str], day: date | None = None) -> None:
    res = wear(client, ids, day)
    assert res.status_code in (200, 201), res.text
    assert res.json()["success"] is True


def list_by_id(client: httpx.Client) -> dict[str, dict[str, Any]]:
    res = client.get("/api/clothes")
    assert res.status_code == 200, res.text
    return {c["id"]: c for c in res.json()["data"]}


def worn_on(clothes: dict[str, Any]) -> str | None:
    value = clothes["lastWornAt"]
    return None if value is None else str(value)[:10]


def server_today(stack: Stack, image: bytes) -> date:
    """백엔드가 '오늘'로 기록하는 날짜. 다른 사용자로 날짜 없이 기록해 보고 lastWornAt을 읽는다."""
    scratch = make_user(stack, "오늘확인")
    try:
        clothes_id = register(scratch.client, image, "bag_brown_tote.jpg")["id"]
        wear_ok(scratch.client, [clothes_id])
        return date.fromisoformat(worn_on(list_by_id(scratch.client)[clothes_id]))
    finally:
        scratch.client.close()


def wear_rows(stack: Stack, user_id: str) -> int:
    return int(stack.sql(f"SELECT count(*) FROM wear_records WHERE user_id = '{user_id}'"))


@pytest.mark.fr("FR-10")
def test_logging_an_outfit_increments_each_wear_count(user, tiny_jpg) -> None:
    ids = [
        register(user.client, tiny_jpg, name)["id"]
        for name in ("top_navy_knit.jpg", "bottom_black_slacks.jpg", "shoes_white_sneakers.jpg")
    ]
    other = register(user.client, tiny_jpg, "top_white_shirt.jpg")["id"]
    before = list_by_id(user.client)
    assert all(before[i]["wearCount"] == 0 and before[i]["lastWornAt"] is None for i in ids)

    wear_ok(user.client, ids)

    after = list_by_id(user.client)
    utc_today = datetime.now(timezone.utc).date()
    kst_today = datetime.now(timezone(timedelta(hours=9))).date()
    for clothes_id in ids:
        assert after[clothes_id]["wearCount"] == 1
        # 백엔드 시간대가 정해져 있지 않아 UTC·KST 오늘 중 하나면 된다
        assert worn_on(after[clothes_id]) in {utc_today.isoformat(), kst_today.isoformat()}
    assert after[other]["wearCount"] == 0
    assert after[other]["lastWornAt"] is None


@pytest.mark.fr("FR-10")
def test_same_clothes_same_day_counts_once(stack, user, tiny_jpg) -> None:
    today = server_today(stack, tiny_jpg)
    clothes_id = register(user.client, tiny_jpg, "top_navy_knit.jpg")["id"]

    wear_ok(user.client, [clothes_id])
    wear_ok(user.client, [clothes_id])
    wear_ok(user.client, [clothes_id], today)
    assert list_by_id(user.client)[clothes_id]["wearCount"] == 1

    # 다른 날은 따로 센다
    wear_ok(user.client, [clothes_id], today - timedelta(days=3))
    wear_ok(user.client, [clothes_id], today - timedelta(days=3))
    clothes = list_by_id(user.client)[clothes_id]
    assert clothes["wearCount"] == 2
    assert worn_on(clothes) == today.isoformat()


@pytest.mark.fr("FR-10")
def test_other_users_clothes_id_is_rejected_and_nothing_recorded(stack, user, tiny_jpg) -> None:
    mine = register(user.client, tiny_jpg, "top_navy_knit.jpg")["id"]
    other = make_user(stack, "남의옷장")
    try:
        theirs = register(other.client, tiny_jpg, "bottom_black_slacks.jpg")["id"]
        res = wear(user.client, [mine, theirs])
        assert res.status_code == 400, res.text
        assert res.json()["success"] is False

        assert list_by_id(user.client)[mine]["wearCount"] == 0
        assert list_by_id(other.client)[theirs]["wearCount"] == 0
        assert wear_rows(stack, user.id) == 0
    finally:
        other.client.close()


def week_starts(day: date) -> tuple[date, date]:
    """(월요일 시작 주의 첫날, 일요일 시작 주의 첫날)"""
    monday = day - timedelta(days=day.weekday())
    sunday = day - timedelta(days=(day.weekday() + 1) % 7)
    return monday, sunday


def in_week(day: date, today: date) -> bool | None:
    """두 주 정의 모두에서 이번 주면 True, 모두 아니면 False, 엇갈리면 None."""
    flags = {start <= day <= today for start in week_starts(today)}
    return flags.pop() if len(flags) == 1 else None


@pytest.mark.fr("FR-10")
def test_summary_matches_records(stack, user, tiny_jpg) -> None:
    today = server_today(stack, tiny_jpg)
    a = register(user.client, tiny_jpg, "top_navy_knit.jpg")
    b = register(user.client, tiny_jpg, "bottom_black_slacks.jpg")

    # 주 정의와 무관한 날짜만 고른다. A는 오늘 포함 3일, B는 1일 (날짜마다 옷 1벌)
    candidates = [today - timedelta(days=k) for k in range(1, 80)]
    unambiguous = [d for d in candidates if in_week(d, today) is not None]
    a_days = [today, unambiguous[0], unambiguous[len(unambiguous) // 2]]
    b_days = [d for d in unambiguous if d not in a_days][-1:]
    for d in a_days:
        wear_ok(user.client, [a["id"]], d)
    for d in b_days:
        wear_ok(user.client, [b["id"]], d)

    records = a_days + b_days
    expected_week = sum(1 for d in records if in_week(d, today))
    expected_month = sum(1 for d in records if (d.year, d.month) == (today.year, today.month))

    res = user.client.get("/api/wears/summary")
    assert res.status_code == 200, res.text
    data = res.json()["data"]
    assert data["weekCount"] == expected_week, (records, today)
    assert data["monthCount"] == expected_month, (records, today)
    # A는 어느 기간으로 봐도 가장 많이 입었다 (오늘 입었고, 전체 3회 > 1회)
    assert data["mostWorn"]["id"] == a["id"]
    assert data["mostWorn"]["name"] == a["name"]

    listed = list_by_id(user.client)
    assert listed[a["id"]]["wearCount"] == 3
    assert listed[b["id"]]["wearCount"] == 1
    assert worn_on(listed[a["id"]]) == today.isoformat()
    assert worn_on(listed[b["id"]]) == max(b_days).isoformat()


@pytest.mark.fr("FR-10")
def test_summary_is_empty_without_records(user, tiny_jpg) -> None:
    register(user.client, tiny_jpg, "top_navy_knit.jpg")
    res = user.client.get("/api/wears/summary")
    assert res.status_code == 200, res.text
    data = res.json()["data"]
    assert data["weekCount"] == 0
    assert data["monthCount"] == 0
    assert data.get("mostWorn") is None


@pytest.mark.fr("FR-10")
def test_account_deletion_removes_wear_records(stack, user, tiny_jpg) -> None:
    ids = [register(user.client, tiny_jpg, n)["id"] for n in ("top_navy_knit.jpg", "bottom_black_slacks.jpg")]
    wear_ok(user.client, ids)
    assert wear_rows(stack, user.id) == 2

    res = user.client.delete("/api/users/me")
    assert res.status_code == 200, res.text
    assert wear_rows(stack, user.id) == 0
