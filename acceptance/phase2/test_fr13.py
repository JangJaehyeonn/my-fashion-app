"""FR-13 속성 수정 후 재판정.

가정 (NOTES.md 참고)
- PATCH /api/purchase-checks/{id}/attributes {category, color, name, fit, material, pattern, season} (null·생략은 그대로)
  → 200, data는 FR-11 판정 응답과 같은 형식의 새 결과.
- 유사도는 가짜 AI 임베딩 규칙(`probe` 기준, `simNN`)으로 정한다. 이름을 고치지 않으므로 사진 이름의 probe가 남는다.
- 결정 API는 FR-12의 POST /api/purchase-checks/{id}/decision.
"""

from __future__ import annotations

from typing import Any

import httpx
import pytest

PROBE_TOP = "top_black_knit_probe.jpg"
# 상의 2·하의 2·신발 1, 상의 하나와 하의 하나가 사진과 비슷하다
CLOSET = {
    "top_sim": "top_white_knit_sim95.jpg", "t2": "top_grey_tee.jpg",
    "bottom_sim": "bottom_black_wide_sim90.jpg", "b2": "bottom_navy_pants.jpg",
    "s1": "shoes_white_sneakers.jpg",
}


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    return res.json()["data"]


def seed(client: httpx.Client, image: bytes) -> dict[str, str]:
    return {alias: register(client, image, filename)["id"] for alias, filename in CLOSET.items()}


def check_ok(client: httpx.Client, image: bytes, filename: str = PROBE_TOP) -> dict[str, Any]:
    res = client.post("/api/purchase-checks", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 200, res.text
    return res.json()["data"]


def patch_attributes(client: httpx.Client, check_id: str, body: dict[str, Any]) -> httpx.Response:
    return client.patch(f"/api/purchase-checks/{check_id}/attributes", json=body)


@pytest.mark.fr("FR-13")
def test_changing_category_compares_against_new_category(user, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg)
    first = check_ok(user.client, tiny_jpg)
    assert first["verdict"] == "SIMILAR"
    assert first["similar"][0]["id"] == ids["top_sim"]
    assert first["plus"] == 2  # 새 상의 × 하의 2 × 신발 1

    res = patch_attributes(user.client, first["id"], {"category": "BOTTOM"})
    assert res.status_code == 200, res.text
    second = res.json()["data"]

    similar_ids = [s["id"] for s in second["similar"]]
    assert similar_ids[0] == ids["bottom_sim"]
    assert second["similar"][0]["similarity"] == pytest.approx(0.90, abs=0.005)
    assert not set(similar_ids) & {ids["top_sim"], ids["t2"], ids["s1"]}
    assert second["verdict"] == "SIMILAR"
    # 새 하의 × 상의 2 × 신발 1
    assert second["plus"] == 2
    assert second["comboCountBefore"] == 4
    assert second["comboCountAfter"] == 6


@pytest.mark.fr("FR-13")
def test_rejudge_does_not_call_classify(user, fake_ai, tiny_jpg) -> None:
    seed(user.client, tiny_jpg)
    first = check_ok(user.client, tiny_jpg)
    fake_ai.post("/__control/reset").raise_for_status()

    res = patch_attributes(user.client, first["id"], {"category": "BOTTOM", "color": "블랙"})
    assert res.status_code == 200, res.text

    paths = [c["path"] for c in fake_ai.get("/__calls").json()]
    assert "/ai/clothes/classify" not in paths, paths
    # 임베딩부터 다시 판정한다
    assert "/ai/embed" in paths, paths


@pytest.mark.fr("FR-13")
def test_rejudge_without_similar_uses_rules(user, tiny_jpg) -> None:
    seed(user.client, tiny_jpg)
    first = check_ok(user.client, tiny_jpg, "top_black_hoodie.jpg")
    assert first["verdict"] == "THINK"

    # 아우터로 고치면 기존 4개 조합 각각에 아우터를 더한 4개가 늘어난다
    res = patch_attributes(user.client, first["id"], {"category": "OUTER"})
    assert res.status_code == 200, res.text
    second = res.json()["data"]
    assert second["plus"] == 4
    assert second["comboCountAfter"] - second["comboCountBefore"] == 4
    assert second["verdict"] == "THINK"


@pytest.mark.fr("FR-13")
@pytest.mark.parametrize("decision", ["BUY", "SKIP"])
def test_decided_check_cannot_be_changed(user, tiny_jpg, decision: str) -> None:
    seed(user.client, tiny_jpg)
    first = check_ok(user.client, tiny_jpg)
    res = user.client.post(f"/api/purchase-checks/{first['id']}/decision", json={"decision": decision})
    assert res.status_code == 200, res.text

    res = patch_attributes(user.client, first["id"], {"category": "BOTTOM"})
    assert res.status_code == 409, res.text
    assert res.json()["success"] is False
