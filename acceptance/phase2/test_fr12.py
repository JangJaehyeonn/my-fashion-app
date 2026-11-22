"""FR-12 살까 말까 결정·기록·임시 사진·하루 제한.

가정 (NOTES.md 참고)
- POST /api/purchase-checks/{id}/decision {"decision": "BUY"|"SKIP"} → 200. BUY면 data가 만든 옷(ClothesResponse)
  이거나 data.clothes에 옷이 있다.
- GET /api/purchase-checks/{id} 단건 조회, GET /api/purchase-checks 최근 20건(최신순)
  항목: {id, name, category, color, price, verdict, plus, decision(BOUGHT/SKIPPED/UNDECIDED), imageUrl(BUY만), createdAt}
- 임시 사진은 하네스 백엔드 컨테이너의 저장소(STORAGE_LOCAL_ROOT=/tmp/wearon-storage) 아래 tmp/에
  checkId가 들어간 이름(파일 또는 폴더)으로 둔다.
- 만료는 purchase_checks.created_at을 31분 앞당기고 임시 사진을 지워(스케줄러가 지운 상태) 흉내낸다.
"""

from __future__ import annotations

import subprocess
from typing import Any

import httpx
import pytest

from acceptance.conftest import COMPOSE, Stack, make_user

STORAGE_ROOT = "/tmp/wearon-storage"
NEW_TOP = "top_black_hoodie.jpg"
# 상의 2·하의 2·신발 2 → 새 상의 +N = 4
CLOSET = {
    "t1": "top_white_shirt.jpg", "t2": "top_grey_tee.jpg",
    "b1": "bottom_black_slacks.jpg", "b2": "bottom_navy_pants.jpg",
    "s1": "shoes_white_sneakers.jpg", "s2": "shoes_black_loafers.jpg",
}


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    return res.json()["data"]


def seed(client: httpx.Client, image: bytes) -> dict[str, str]:
    return {alias: register(client, image, filename)["id"] for alias, filename in CLOSET.items()}


def check(client: httpx.Client, image: bytes, filename: str = NEW_TOP, price: int | None = 39000) -> httpx.Response:
    data = {} if price is None else {"price": str(price)}
    return client.post("/api/purchase-checks", files={"image": (filename, image, "image/jpeg")}, data=data)


def check_ok(client: httpx.Client, image: bytes, filename: str = NEW_TOP, price: int | None = 39000) -> dict[str, Any]:
    res = check(client, image, filename, price)
    assert res.status_code == 200, res.text
    return res.json()["data"]


def decide(client: httpx.Client, check_id: str, decision: str) -> httpx.Response:
    return client.post(f"/api/purchase-checks/{check_id}/decision", json={"decision": decision})


def combo_total(client: httpx.Client) -> int:
    res = client.get("/api/clothes/summary")
    assert res.status_code == 200, res.text
    return res.json()["data"]["comboCount"]


def history(client: httpx.Client) -> list[dict[str, Any]]:
    res = client.get("/api/purchase-checks")
    assert res.status_code == 200, res.text
    return res.json()["data"]


def backend_sh(script: str) -> str:
    result = subprocess.run([*COMPOSE, "exec", "-T", "backend", "sh", "-c", script],
                            capture_output=True, text=True, check=True)
    return result.stdout


def tmp_photos(check_id: str) -> list[str]:
    out = backend_sh(f"find {STORAGE_ROOT}/tmp -name '*{check_id}*' 2>/dev/null || true")
    return [line for line in out.splitlines() if line.strip()]


def expire(stack: Stack, check_id: str) -> None:
    stack.sql(f"UPDATE purchase_checks SET created_at = created_at - interval '31 minutes' WHERE id = '{check_id}'")
    backend_sh(f"find {STORAGE_ROOT}/tmp -name '*{check_id}*' -exec rm -rf {{}} + 2>/dev/null || true")


@pytest.mark.fr("FR-12")
def test_buy_creates_clothes_without_reclassify_and_adds_plus(stack, user, fake_ai, tiny_jpg) -> None:
    seed(user.client, tiny_jpg)
    before = combo_total(user.client)
    result = check_ok(user.client, tiny_jpg)
    assert result["plus"] == 4
    assert tmp_photos(result["id"]), "판정용 사진이 tmp/에 checkId로 있어야 한다"
    fake_ai.post("/__control/reset").raise_for_status()

    res = decide(user.client, result["id"], "BUY")
    assert res.status_code == 200, res.text
    data = res.json()["data"]
    created = data.get("clothes", data)
    assert created["category"] == "TOP"
    assert created["name"] == "블랙 hoodie"
    assert created["imageUrl"]

    listed = {c["id"]: c for c in user.client.get("/api/clothes").json()["data"]}
    assert created["id"] in listed
    assert len(listed) == len(CLOSET) + 1

    paths = [c["path"] for c in fake_ai.get("/__calls").json()]
    assert "/ai/clothes/classify" not in paths, paths
    assert combo_total(user.client) == before + result["plus"]
    # 판정 때 만든 임베딩을 그대로 쓴다
    assert stack.sql(f"SELECT embedding IS NOT NULL FROM clothes WHERE id = '{created['id']}'") == "t"
    assert tmp_photos(result["id"]) == []


@pytest.mark.fr("FR-12")
def test_skip_deletes_temp_photo_and_records_skipped(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg)
    before = combo_total(user.client)
    result = check_ok(user.client, tiny_jpg)
    assert tmp_photos(result["id"])

    res = decide(user.client, result["id"], "SKIP")
    assert res.status_code == 200, res.text
    assert tmp_photos(result["id"]) == []

    entry = {h["id"]: h for h in history(user.client)}[result["id"]]
    assert entry["decision"] == "SKIPPED"
    assert entry["imageUrl"] is None
    assert len(user.client.get("/api/clothes").json()["data"]) == len(CLOSET)
    assert combo_total(user.client) == before


@pytest.mark.fr("FR-12")
@pytest.mark.parametrize("decision", ["BUY", "SKIP"])
def test_expired_check_is_gone(stack, user, tiny_jpg, decision: str) -> None:
    seed(user.client, tiny_jpg)
    result = check_ok(user.client, tiny_jpg)
    expire(stack, result["id"])

    res = decide(user.client, result["id"], decision)
    assert res.status_code == 410, res.text
    assert res.json()["success"] is False
    assert len(user.client.get("/api/clothes").json()["data"]) == len(CLOSET)


@pytest.mark.fr("FR-12")
def test_other_users_check_is_not_found(stack, user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg)
    result = check_ok(user.client, tiny_jpg)
    other = make_user(stack, "남의판정")
    try:
        assert other.client.get(f"/api/purchase-checks/{result['id']}").status_code == 404
        for decision in ("BUY", "SKIP"):
            res = decide(other.client, result["id"], decision)
            assert res.status_code == 404, res.text
        assert result["id"] not in {h["id"] for h in history(other.client)}
    finally:
        other.client.close()

    # 주인은 그대로 조회·결정할 수 있다
    own = user.client.get(f"/api/purchase-checks/{result['id']}")
    assert own.status_code == 200, own.text
    assert tmp_photos(result["id"])
    assert decide(user.client, result["id"], "SKIP").status_code == 200


@pytest.mark.fr("FR-12")
def test_history_lists_recent_checks_with_plus_and_photo_only_for_buy(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg)
    skipped = check_ok(user.client, tiny_jpg, "top_beige_cardigan.jpg", price=None)
    undecided = check_ok(user.client, tiny_jpg, "bottom_grey_jogger.jpg")
    bought = check_ok(user.client, tiny_jpg, NEW_TOP)
    assert decide(user.client, skipped["id"], "SKIP").status_code == 200
    assert decide(user.client, bought["id"], "BUY").status_code == 200

    entries = history(user.client)
    assert [e["id"] for e in entries] == [bought["id"], undecided["id"], skipped["id"]]
    by_id = {e["id"]: e for e in entries}
    for result, decision in ((bought, "BOUGHT"), (undecided, "UNDECIDED"), (skipped, "SKIPPED")):
        entry = by_id[result["id"]]
        assert entry["decision"] == decision
        assert entry["plus"] == result["plus"]
        assert entry["verdict"] == result["verdict"]
    assert by_id[bought["id"]]["imageUrl"]
    assert by_id[undecided["id"]]["imageUrl"] is None
    assert by_id[skipped["id"]]["imageUrl"] is None
    assert by_id[bought["id"]]["category"] == "TOP"
    assert by_id[bought["id"]]["price"] == 39000
    assert by_id[skipped["id"]]["price"] is None


@pytest.mark.fr("FR-12")
def test_thirty_first_check_of_the_day_is_rate_limited(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg)
    ids = []
    for i in range(30):
        res = check(user.client, tiny_jpg)
        assert res.status_code == 200, f"{i + 1}번째: {res.status_code} {res.text}"
        ids.append(res.json()["data"]["id"])

    # 최근 20건만 최신순으로
    assert [e["id"] for e in history(user.client)] == list(reversed(ids))[:20]

    res = check(user.client, tiny_jpg)
    assert res.status_code == 429, res.text
    assert res.json()["success"] is False


@pytest.mark.fr("FR-12")
def test_account_deletion_removes_checks_and_temp_photos(stack, user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg)
    undecided = check_ok(user.client, tiny_jpg)
    skipped = check_ok(user.client, tiny_jpg, "top_beige_cardigan.jpg")
    assert decide(user.client, skipped["id"], "SKIP").status_code == 200
    assert tmp_photos(undecided["id"])

    res = user.client.delete("/api/users/me")
    assert res.status_code == 200, res.text
    assert stack.sql(f"SELECT count(*) FROM purchase_checks WHERE user_id = '{user.id}'") == "0"
    assert tmp_photos(undecided["id"]) == []
    assert backend_sh(f"find {STORAGE_ROOT}/tmp -path '*{user.id}*' 2>/dev/null || true").strip() == ""
