"""FR-09 코디 수 계산 엔진 (+N 코디).

가정 (NOTES.md 참고)
- 요약 API: GET /api/clothes/summary → data.comboCount (옷장 전체 유효 코디 수)
- 옷 목록의 각 옷에 comboCount. 미분류 옷은 0 또는 null.
- 기본 규칙 파일: 무채색·기본색(화이트·블랙·네이비·그레이·베이지)은 어떤 색과도 어울린다.
  예시 충돌 색 조합에 '레드–그린'이 있다. 속성(계절·스타일)이 null이면 충돌로 보지 않는다.
- 코디 = 상의 1 + 하의 1 + 신발 1 (+ 아우터 0~1). 그래서 어울리는 옷장의 코디 수 = 상의×하의×신발×(1+아우터).
"""

from __future__ import annotations

from typing import Any

import httpx
import pytest


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    return res.json()["data"]


def combo_total(client: httpx.Client) -> int:
    res = client.get("/api/clothes/summary")
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["success"] is True
    return body["data"]["comboCount"]


def combo_counts(client: httpx.Client) -> dict[str, Any]:
    res = client.get("/api/clothes")
    assert res.status_code == 200, res.text
    return {c["id"]: c["comboCount"] for c in res.json()["data"]}


def seed(client: httpx.Client, image: bytes, names: dict[str, str]) -> dict[str, str]:
    """{별칭: 파일 이름} → {별칭: 옷 ID}"""
    return {alias: register(client, image, filename)["id"] for alias, filename in names.items()}


BASE = {
    "top1": "top_white_shirt.jpg",
    "top2": "top_navy_knit.jpg",
    "bottom1": "bottom_black_slacks.jpg",
    "bottom2": "bottom_beige_chino.jpg",
    "shoes1": "shoes_white_sneakers.jpg",
}


@pytest.mark.fr("FR-09")
def test_two_tops_two_bottoms_one_shoes_make_four(user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, BASE)
    assert combo_total(user.client) == 4

    counts = combo_counts(user.client)
    assert counts[ids["top1"]] == 2
    assert counts[ids["top2"]] == 2
    assert counts[ids["bottom1"]] == 2
    assert counts[ids["bottom2"]] == 2
    assert counts[ids["shoes1"]] == 4


@pytest.mark.fr("FR-09")
def test_adding_one_outer_doubles_to_eight(user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, {**BASE, "outer1": "outer_grey_coat.jpg"})
    assert combo_total(user.client) == 8

    counts = combo_counts(user.client)
    assert counts[ids["outer1"]] == 4
    assert counts[ids["top1"]] == 4
    assert counts[ids["bottom2"]] == 4
    assert counts[ids["shoes1"]] == 8


@pytest.mark.fr("FR-09")
def test_conflicting_colors_are_not_counted(user, tiny_jpg) -> None:
    # 레드 상의 + 그린 하의는 규칙 파일의 예시 충돌 색 조합
    ids = seed(user.client, tiny_jpg, {
        "red_top": "top_red_tee.jpg",
        "white_top": "top_white_shirt.jpg",
        "green_bottom": "bottom_green_pants.jpg",
        "black_bottom": "bottom_black_slacks.jpg",
        "shoes": "shoes_black_loafers.jpg",
    })
    # 2×2×1 = 4 중 (레드, 그린) 1개가 빠진다
    assert combo_total(user.client) == 3

    counts = combo_counts(user.client)
    assert counts[ids["red_top"]] == 1
    assert counts[ids["green_bottom"]] == 1
    assert counts[ids["white_top"]] == 2
    assert counts[ids["black_bottom"]] == 2
    assert counts[ids["shoes"]] == 3


@pytest.mark.fr("FR-09")
def test_unclassified_clothes_are_not_counted(user, fake_ai, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, BASE)
    fake_ai.post("/__control", json={"mode": "down"}).raise_for_status()
    unclassified = seed(user.client, tiny_jpg, {
        "u_top": "top_grey_tee.jpg",
        "u_bottom": "bottom_navy_pants.jpg",
        "u_shoes": "shoes_black_boots.jpg",
    })
    fake_ai.post("/__control", json={"mode": "normal"}).raise_for_status()

    assert combo_total(user.client) == 4
    counts = combo_counts(user.client)
    for alias, clothes_id in unclassified.items():
        assert counts[clothes_id] in (0, None), alias
    assert counts[ids["shoes1"]] == 4
    assert counts[ids["top1"]] == 2


@pytest.mark.fr("FR-09")
def test_combo_count_of_each_clothes_matches_valid_combos(user, tiny_jpg) -> None:
    # 상의 3·하의 2·신발 2·아우터 1: 전체 3×2×2×2 = 24
    ids = seed(user.client, tiny_jpg, {
        "t1": "top_white_shirt.jpg", "t2": "top_navy_knit.jpg", "t3": "top_grey_tee.jpg",
        "b1": "bottom_black_slacks.jpg", "b2": "bottom_beige_chino.jpg",
        "s1": "shoes_white_sneakers.jpg", "s2": "shoes_black_loafers.jpg",
        "o1": "outer_navy_jacket.jpg",
    })
    assert combo_total(user.client) == 24
    counts = combo_counts(user.client)
    expected = {"t1": 8, "t2": 8, "t3": 8, "b1": 12, "b2": 12, "s1": 12, "s2": 12, "o1": 12}
    assert {alias: counts[ids[alias]] for alias in expected} == expected


@pytest.mark.fr("FR-09")
def test_counts_change_after_add_delete_and_update(user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, BASE)
    assert combo_total(user.client) == 4

    added = register(user.client, tiny_jpg, "top_grey_tee.jpg")["id"]
    assert combo_total(user.client) == 6
    assert combo_counts(user.client)[ids["shoes1"]] == 6

    res = user.client.delete(f"/api/clothes/{added}")
    assert res.status_code == 200, res.text
    assert combo_total(user.client) == 4

    # 하의 하나를 상의로 고치면 상의 3·하의 1·신발 1 = 3
    res = user.client.patch(f"/api/clothes/{ids['bottom2']}", json={"category": "TOP"})
    assert res.status_code == 200, res.text
    assert combo_total(user.client) == 3
    counts = combo_counts(user.client)
    assert counts[ids["bottom1"]] == 3
    assert counts[ids["bottom2"]] == 1

    # 색을 충돌 조합으로 고쳐도 바뀐다: 하의를 그린으로, 상의 하나를 레드로 → (레드, 그린) 1개 빠짐
    assert user.client.patch(f"/api/clothes/{ids['bottom1']}", json={"color": "그린"}).status_code == 200
    assert user.client.patch(f"/api/clothes/{ids['top1']}", json={"color": "레드"}).status_code == 200
    assert combo_total(user.client) == 2
    assert combo_counts(user.client)[ids["top1"]] == 0


@pytest.mark.fr("FR-09")
def test_same_closet_gives_same_result(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg, {**BASE, "outer1": "outer_grey_coat.jpg", "red": "top_red_tee.jpg"})
    first_total, first_counts = combo_total(user.client), combo_counts(user.client)
    second_total, second_counts = combo_total(user.client), combo_counts(user.client)
    assert first_total == second_total
    assert first_counts == second_counts
