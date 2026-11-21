"""가짜 AI 서버의 결정적 응답 규칙 점검 (스택 없이 실행)."""

from __future__ import annotations

import os
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

# FAKE_AI_DIR로 다른 사본(인수 테스트 초안의 acceptance_draft/fake_ai)을 점검할 수 있다.
sys.path.insert(0, os.environ.get("FAKE_AI_DIR") or str(Path(__file__).resolve().parent / "fake_ai"))
import app as fake  # noqa: E402

client = TestClient(fake.app)


@pytest.fixture(autouse=True)
def _reset() -> None:
    client.post("/__control/reset")


@pytest.mark.smoke
def test_classify_by_filename() -> None:
    res = client.post("/ai/clothes/classify", files={"image": ("top_navy_crop_knit.jpg", b"x", "image/jpeg")})
    assert res.json() == {"category": "TOP", "color": "네이비", "name": "네이비 crop knit"}
    res = client.post("/ai/clothes/classify", files={"image": ("bag_red.jpg", b"x", "image/jpeg")})
    assert res.json()["category"] == "ETC"


@pytest.mark.smoke
def test_fail_prefix_and_down_mode() -> None:
    res = client.post("/ai/clothes/classify", files={"image": ("fail_x.jpg", b"x", "image/jpeg")})
    assert res.status_code == 500
    client.post("/__control", json={"mode": "down"})
    assert client.get("/ai/weather").status_code == 503
    client.post("/__control", json={"mode": "bad"})
    assert client.get("/ai/weather").json() == {"unexpected": True}


@pytest.mark.smoke
def test_calls_are_recorded_and_closet_pick_is_deterministic() -> None:
    clothes = [
        {"id": "s1", "category": "SHOES"},
        {"id": "b1", "category": "BOTTOM"},
        {"id": "t1", "category": "TOP"},
    ]
    res = client.post("/ai/outfits/recommend/closet", json={"weather": {"temperature": 10, "condition": "맑음"},
                                                            "situation": "DAILY", "clothes": clothes})
    assert res.json()["outfits"][0]["clothesIds"] == ["t1", "b1", "s1"]
    calls = client.get("/__calls").json()
    assert calls[-1]["path"] == "/ai/outfits/recommend/closet"
    assert calls[-1]["json"]["clothes"] == clothes
