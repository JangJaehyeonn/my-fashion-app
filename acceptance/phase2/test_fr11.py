"""FR-11 살까 말까 판정 API.

가정 (NOTES.md 참고)
- POST /api/purchase-checks (multipart image, 선택 price) → 200, data:
  {id, verdict, plus, comboCountBefore, comboCountAfter, similar: [{id, name, similarity(0~1), wearCount, lastWornAt, note}],
   outfits: [{clothesIds(옷장 옷 ID만, 새 옷은 빠짐), label}], headline, reasons, gap, expectedWears, costPerWear, ...}
- 옷장 5벌 미만: 400, 응답 본문 code = "CLOSET_TOO_SMALL" (ApiResponse 오류에 code 필드 추가)
- 유사도는 가짜 AI 임베딩 규칙으로 정한다: 사진 이름의 `probe`가 기준, 옷 이름의 `simNN`이 기준과 유사도 NN/100.
- 이유 문장·대표 코디는 POST /ai/purchase-check/explain 한 번으로 받는다 (가짜 AI docstring).
- 코디 수는 FR-09 가정과 같다 (기본색끼리는 모두 어울림). 요약 API GET /api/clothes/summary의 comboCount.
- expectedWears = round(같은 카테고리 옷들의 최근 90일 착용 횟수 합 × plus ÷ (판정 전 그 카테고리 옷이 든 유효 코디 수) × 365 ÷ 90)
"""

from __future__ import annotations

from datetime import date, timedelta
from typing import Any

import httpx
import pytest

from acceptance.conftest import Stack, make_user

EXPLAIN_PATH = "/ai/purchase-check/explain"
FAKE_HEADLINE = "가짜 AI 한 줄 평"
FAKE_REASONS = ["가짜 이유 1", "가짜 이유 2", "가짜 이유 3"]
FAKE_GAP = "가짜 옷장 빈틈"
FAKE_NOTE = "가짜 다른 점"
PROBE_TOP = "top_black_knit_probe.jpg"
NEW_TOP = "top_black_hoodie.jpg"


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    return res.json()["data"]


def seed(client: httpx.Client, image: bytes, names: dict[str, str]) -> dict[str, str]:
    return {alias: register(client, image, filename)["id"] for alias, filename in names.items()}


def check(client: httpx.Client, image: bytes, filename: str, price: int | None = None) -> httpx.Response:
    data = {} if price is None else {"price": str(price)}
    return client.post("/api/purchase-checks", files={"image": (filename, image, "image/jpeg")}, data=data)


def check_ok(client: httpx.Client, image: bytes, filename: str, price: int | None = None) -> dict[str, Any]:
    res = check(client, image, filename, price)
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["success"] is True
    return body["data"]


def combo_total(client: httpx.Client) -> int:
    res = client.get("/api/clothes/summary")
    assert res.status_code == 200, res.text
    return res.json()["data"]["comboCount"]


def wear_ok(client: httpx.Client, ids: list[str], day: date | None = None) -> None:
    body: dict[str, Any] = {"clothesIds": ids}
    if day is not None:
        body["date"] = day.isoformat()
    res = client.post("/api/wears", json=body)
    assert res.status_code in (200, 201), res.text


def server_today(stack: Stack, image: bytes) -> date:
    """백엔드가 '오늘'로 기록하는 날짜 (다른 사용자로 날짜 없이 기록해 보고 lastWornAt을 읽는다)."""
    scratch = make_user(stack, "오늘확인")
    try:
        clothes_id = register(scratch.client, image, "bag_brown_tote.jpg")["id"]
        wear_ok(scratch.client, [clothes_id])
        listed = {c["id"]: c for c in scratch.client.get("/api/clothes").json()["data"]}
        return date.fromisoformat(str(listed[clothes_id]["lastWornAt"])[:10])
    finally:
        scratch.client.close()


def explain_calls(fake_ai: httpx.Client) -> list[dict[str, Any]]:
    return [c for c in fake_ai.get("/__calls").json() if c["path"] == EXPLAIN_PATH]


# 상의 2·하의 5·신발 1 → 새 상의의 +N = 5×1 = 5 (BUY 경계)
BUY_CLOSET = {
    "t1": "top_white_shirt.jpg", "t2": "top_grey_tee.jpg",
    "b1": "bottom_black_slacks.jpg", "b2": "bottom_navy_pants.jpg", "b3": "bottom_beige_chino.jpg",
    "b4": "bottom_grey_jogger.jpg", "b5": "bottom_white_cotton_pants.jpg",
    "s1": "shoes_black_loafers.jpg",
}
# 상의 2·하의 2·신발 2 → 새 상의의 +N = 2×2 = 4 (THINK 경계)
THINK_CLOSET = {
    "t1": "top_white_shirt.jpg", "t2": "top_grey_tee.jpg",
    "b1": "bottom_black_slacks.jpg", "b2": "bottom_navy_pants.jpg",
    "s1": "shoes_white_sneakers.jpg", "s2": "shoes_black_loafers.jpg",
}


def assert_counts(client: httpx.Client, data: dict[str, Any], before: int, plus: int) -> None:
    assert data["plus"] == plus
    assert data["comboCountBefore"] == before
    assert data["comboCountAfter"] == before + plus
    assert data["comboCountAfter"] - data["comboCountBefore"] == data["plus"]


# ── 판정 ───────────────────────────────────────────────────────


@pytest.mark.fr("FR-11")
def test_similar_clothes_give_similar_verdict_top3_with_wear_counts(stack, user, fake_ai, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, {
        "sim95": "top_grey_knit_sim95.jpg", "sim90": "top_white_knit_sim90.jpg",
        "sim88": "top_navy_knit_sim88.jpg", "sim86": "top_beige_knit_sim86.jpg",
        "other_cat": "bottom_black_knit_sim99.jpg",  # 다른 카테고리: 유사도가 더 높아도 빠진다
        "shoes": "shoes_white_sneakers.jpg",
    })
    # 미분류 옷: 이름에 sim99를 넣어도 빠진다
    fake_ai.post("/__control", json={"mode": "down"}).raise_for_status()
    unclassified = register(user.client, tiny_jpg, "top_black_knit.jpg")["id"]
    fake_ai.post("/__control", json={"mode": "normal"}).raise_for_status()
    res = user.client.patch(f"/api/clothes/{unclassified}", json={"name": "블랙 knit sim99"})
    assert res.status_code == 200, res.text

    today = server_today(stack, tiny_jpg)
    wear_ok(user.client, [ids["sim95"], ids["sim90"]], today - timedelta(days=5))
    wear_ok(user.client, [ids["sim95"]], today - timedelta(days=6))

    data = check_ok(user.client, tiny_jpg, PROBE_TOP)
    assert data["verdict"] == "SIMILAR"
    similar = data["similar"]
    assert [s["id"] for s in similar] == [ids["sim95"], ids["sim90"], ids["sim88"]]
    for s, expected in zip(similar, (0.95, 0.90, 0.88)):
        assert s["similarity"] == pytest.approx(expected, abs=0.005)
    assert [s["wearCount"] for s in similar] == [2, 1, 0]
    assert str(similar[0]["lastWornAt"])[:10] == (today - timedelta(days=5)).isoformat()
    assert similar[2]["lastWornAt"] is None
    assert not {s["id"] for s in similar} & {ids["other_cat"], unclassified, ids["shoes"]}


@pytest.mark.fr("FR-11")
def test_similarity_exactly_at_threshold_is_similar(user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, {
        "sim85": "top_white_tee_sim85.jpg", "t2": "top_grey_tee.jpg",
        "b1": "bottom_black_slacks.jpg", "b2": "bottom_navy_pants.jpg", "s1": "shoes_white_sneakers.jpg",
    })
    data = check_ok(user.client, tiny_jpg, PROBE_TOP)
    assert data["verdict"] == "SIMILAR"
    assert data["similar"][0]["id"] == ids["sim85"]
    assert data["similar"][0]["similarity"] == pytest.approx(0.85, abs=0.005)


@pytest.mark.fr("FR-11")
def test_similarity_just_below_threshold_is_not_similar(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg, {
        "sim84": "top_white_tee_sim84.jpg", "t2": "top_grey_tee.jpg",
        "b1": "bottom_black_slacks.jpg", "b2": "bottom_navy_pants.jpg", "s1": "shoes_white_sneakers.jpg",
    })
    data = check_ok(user.client, tiny_jpg, PROBE_TOP)
    # 비슷한 옷이 없고 +N = 2×1 = 2 → THINK
    assert data["verdict"] == "THINK"
    assert data["plus"] == 2


@pytest.mark.fr("FR-11")
def test_plus_five_without_similar_is_buy(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg, BUY_CLOSET)
    before = combo_total(user.client)
    assert before == 10
    data = check_ok(user.client, tiny_jpg, NEW_TOP)
    assert data["verdict"] == "BUY"
    assert_counts(user.client, data, before=10, plus=5)


@pytest.mark.fr("FR-11")
def test_plus_four_without_similar_is_think(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg, THINK_CLOSET)
    assert combo_total(user.client) == 8
    data = check_ok(user.client, tiny_jpg, NEW_TOP)
    assert data["verdict"] == "THINK"
    assert_counts(user.client, data, before=8, plus=4)


@pytest.mark.fr("FR-11")
def test_plus_matches_engine_for_new_outer(user, tiny_jpg) -> None:
    # 상의 2·하의 2·신발 1에 아우터를 사면 기존 4개 조합 각각에 아우터를 더한 4개가 늘어난다
    seed(user.client, tiny_jpg, {
        "t1": "top_white_shirt.jpg", "t2": "top_navy_knit.jpg",
        "b1": "bottom_black_slacks.jpg", "b2": "bottom_beige_chino.jpg", "s1": "shoes_white_sneakers.jpg",
    })
    before = combo_total(user.client)
    assert before == 4
    data = check_ok(user.client, tiny_jpg, "outer_grey_coat.jpg")
    assert_counts(user.client, data, before=4, plus=4)
    assert data["verdict"] == "THINK"
    # 판정만 했으므로 옷장 코디 수는 그대로다
    assert combo_total(user.client) == 4


@pytest.mark.fr("FR-11")
def test_ai_verdict_in_reason_response_is_ignored(user, fake_ai, tiny_jpg) -> None:
    seed(user.client, tiny_jpg, THINK_CLOSET)
    fake_ai.post("/__control/purchase", json={"verdict": "BUY"}).raise_for_status()
    data = check_ok(user.client, tiny_jpg, NEW_TOP)
    assert data["verdict"] == "THINK"
    assert data["plus"] == 4


@pytest.mark.fr("FR-11")
def test_ai_receives_rule_verdict_and_its_sentences_are_used(user, fake_ai, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, {
        "sim90": "top_white_knit_sim90.jpg", "t2": "top_grey_tee.jpg",
        "b1": "bottom_black_slacks.jpg", "b2": "bottom_navy_pants.jpg", "s1": "shoes_white_sneakers.jpg",
    })
    data = check_ok(user.client, tiny_jpg, PROBE_TOP)

    calls = explain_calls(fake_ai)
    assert len(calls) == 1
    sent = calls[0]["json"]
    assert sent["verdict"] == data["verdict"] == "SIMILAR"
    assert sent["plus"] == data["plus"]
    assert ids["sim90"] in [str(s["id"]) for s in sent["similar"]]

    assert data["headline"] == FAKE_HEADLINE
    assert data["reasons"] == FAKE_REASONS
    assert data["gap"] == FAKE_GAP
    assert data["similar"][0]["note"] == FAKE_NOTE


@pytest.mark.fr("FR-11")
def test_ai_pick_outside_valid_combos_is_dropped(user, fake_ai, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, THINK_CLOSET)
    # 하의 2벌 + 신발은 새 상의가 들어간 유효 조합이 아니다
    invalid = [ids["b1"], ids["b2"], ids["s1"]]
    fake_ai.post("/__control/purchase", json={"extraPick": invalid}).raise_for_status()

    data = check_ok(user.client, tiny_jpg, NEW_TOP)

    closet_ids = set(ids.values())
    valid = {frozenset({b, s}) for b in (ids["b1"], ids["b2"]) for s in (ids["s1"], ids["s2"])}
    sent = explain_calls(fake_ai)[0]["json"]["candidates"]
    assert {frozenset(set(map(str, c["clothesIds"])) & closet_ids) for c in sent} == valid

    outfits = data["outfits"]
    assert 1 <= len(outfits) <= 3
    for outfit in outfits:
        picked = frozenset(set(map(str, outfit["clothesIds"])) & closet_ids)
        assert picked in valid, outfit
        assert picked != frozenset(invalid)


@pytest.mark.fr("FR-11")
def test_closet_of_four_is_too_small(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg, {
        "t1": "top_white_shirt.jpg", "b1": "bottom_black_slacks.jpg",
        "b2": "bottom_navy_pants.jpg", "s1": "shoes_white_sneakers.jpg",
    })
    res = check(user.client, tiny_jpg, NEW_TOP)
    assert res.status_code == 400, res.text
    body = res.json()
    assert body["success"] is False
    assert body["code"] == "CLOSET_TOO_SMALL"


@pytest.mark.fr("FR-11")
@pytest.mark.parametrize("failure", ["down", "bad"])
def test_reason_failure_falls_back_to_rule_sentences(user, fake_ai, tiny_jpg, failure: str) -> None:
    seed(user.client, tiny_jpg, THINK_CLOSET)
    fake_ai.post("/__control/paths", json={EXPLAIN_PATH: failure}).raise_for_status()

    data = check_ok(user.client, tiny_jpg, NEW_TOP)
    assert data["verdict"] == "THINK"
    assert data["plus"] == 4
    assert isinstance(data["headline"], str) and data["headline"].strip()
    assert data["reasons"] and all(isinstance(r, str) and r.strip() for r in data["reasons"])


# ── 예상 착용 횟수·1회당 가격 ──────────────────────────────────

# 상의 2(A·B)·하의 2(C·D)·신발 1 → 판정 전 상의가 든 유효 코디 4개, 새 상의 +N = 2
WEAR_CLOSET = {
    "A": "top_white_shirt.jpg", "B": "top_grey_tee.jpg",
    "C": "bottom_black_slacks.jpg", "D": "bottom_navy_pants.jpg", "E": "shoes_white_sneakers.jpg",
}


def seed_long_history(stack: Stack, user, image: bytes) -> dict[str, str]:
    ids = seed(user.client, image, WEAR_CLOSET)
    today = server_today(stack, image)
    for days_ago in (40, 20, 10):
        wear_ok(user.client, [ids["A"]], today - timedelta(days=days_ago))
    wear_ok(user.client, [ids["B"]], today - timedelta(days=5))
    wear_ok(user.client, [ids["A"]], today - timedelta(days=120))  # 90일 밖: 빠진다
    wear_ok(user.client, [ids["C"]], today - timedelta(days=40))  # 다른 카테고리: 빠진다
    wear_ok(user.client, [ids["C"]], today - timedelta(days=15))
    return ids


@pytest.mark.fr("FR-11")
def test_expected_wears_and_cost_per_wear_follow_formula(stack, user, tiny_jpg) -> None:
    seed_long_history(stack, user, tiny_jpg)
    data = check_ok(user.client, tiny_jpg, NEW_TOP, price=39000)
    assert data["plus"] == 2
    # 최근 90일 상의 착용 4회 × (2 ÷ 4) × 365 ÷ 90 = 8.11 → 8
    assert data["expectedWears"] == 8
    # 39000 ÷ 8 = 4875
    assert data["costPerWear"] == 4875


@pytest.mark.fr("FR-11")
def test_cost_per_wear_is_null_without_price(stack, user, tiny_jpg) -> None:
    seed_long_history(stack, user, tiny_jpg)
    data = check_ok(user.client, tiny_jpg, NEW_TOP)
    assert data["expectedWears"] == 8
    assert data["costPerWear"] is None


@pytest.mark.fr("FR-11")
def test_short_wear_history_gives_null_expected_wears(stack, user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, WEAR_CLOSET)
    today = server_today(stack, tiny_jpg)
    wear_ok(user.client, [ids["A"]], today - timedelta(days=20))
    wear_ok(user.client, [ids["B"]], today - timedelta(days=10))

    data = check_ok(user.client, tiny_jpg, NEW_TOP, price=39000)
    assert data["expectedWears"] is None
    assert data["costPerWear"] is None


@pytest.mark.fr("FR-11")
def test_no_wear_history_gives_null_expected_wears(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg, WEAR_CLOSET)
    data = check_ok(user.client, tiny_jpg, NEW_TOP, price=39000)
    assert data["expectedWears"] is None
    assert data["costPerWear"] is None
