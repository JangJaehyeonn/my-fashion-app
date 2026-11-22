"""가짜 AI 서버 Phase 2 추가 규칙 점검 (스택 없이 실행).

이 파일 옆의 fake_ai/app.py를 본다 (acceptance_draft/에서는 초안 사본, acceptance/로 옮기면 하네스 원본).
FAKE_AI_DIR로 다른 사본을 지정할 수 있다. 기존 test_fake_ai.py의 `app` 모듈과 섞이지 않게 이름을 따로 붙여 읽는다.
"""

from __future__ import annotations

import importlib.util
import math
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

_DIR = Path(os.environ.get("FAKE_AI_DIR") or Path(__file__).resolve().parent / "fake_ai")
_spec = importlib.util.spec_from_file_location("fake_ai_phase2", _DIR / "app.py")
assert _spec and _spec.loader
fake = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(fake)

client = TestClient(fake.app)
KST = timezone(timedelta(hours=9))


@pytest.fixture(autouse=True)
def _reset() -> None:
    client.post("/__control/reset")


def embed(text: str) -> list[float]:
    res = client.post("/ai/embed", json={"text": text})
    assert res.status_code == 200, res.text
    return res.json()["embedding"]


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    return dot / math.sqrt(sum(x * x for x in a) * sum(y * y for y in b))


@pytest.mark.smoke
def test_classify_attributes_are_off_by_default_and_parsed_when_on() -> None:
    files = {"image": ("top_navy_wool_slim_stripe_knit_winter.jpg", b"x", "image/jpeg")}
    assert set(client.post("/ai/clothes/classify", files=files).json()) == {"category", "color", "name"}

    client.post("/__control/attributes", json={"enabled": True})
    res = client.post("/ai/clothes/classify", files=files).json()
    assert res == {
        "category": "TOP", "color": "네이비", "name": "네이비 wool slim stripe knit winter",
        "fit": "슬림", "material": "울", "pattern": "스트라이프", "season": "WINTER",
    }
    plain = client.post("/ai/clothes/classify", files={"image": ("shoes_white_sneakers.jpg", b"x", "image/jpeg")})
    assert plain.json() == {"category": "SHOES", "color": "화이트", "name": "화이트 sneakers",
                            "fit": None, "material": None, "pattern": None, "season": None}

    client.post("/__control/reset")
    assert set(client.post("/ai/clothes/classify", files=files).json()) == {"category", "color", "name"}


@pytest.mark.smoke
def test_embedding_is_deterministic_1536_and_similarity_tokens_are_exact() -> None:
    a = embed("TOP 네이비 니트 네이비 knit")
    assert len(a) == fake.EMBEDDING_DIM == 1536
    assert a == embed("TOP 네이비 니트 네이비 knit")
    assert abs(cosine(a, embed("BOTTOM 블랙 블랙 slacks"))) < 0.2

    probe = embed("TOP 블랙 블랙 knit probe")
    for nn in (100, 95, 90, 88, 86, 85, 84, 50, 0):
        vec = embed(f"TOP 화이트 화이트 tee sim{nn}")
        assert all(float(v).is_integer() for v in vec[:5])
        assert cosine(probe, vec) == nn / 100, nn
    # probe가 simNN보다 우선한다
    assert embed("sim50 probe") == probe

    batch = client.post("/ai/embed", json={"texts": ["probe", "x sim85"]}).json()["embeddings"]
    assert batch == [probe, embed("sim85")]


@pytest.mark.smoke
def test_path_control_only_affects_given_path() -> None:
    client.post("/__control/paths", json={"/ai/embed": "down"})
    assert client.post("/ai/embed", json={"text": "x"}).status_code == 503
    ok = client.post("/ai/clothes/classify", files={"image": ("top_navy_knit.jpg", b"x", "image/jpeg")})
    assert ok.json() == {"category": "TOP", "color": "네이비", "name": "네이비 knit"}

    client.post("/__control/paths", json={"/ai/embed": "bad"})
    assert client.post("/ai/embed", json={"text": "x"}).json() == {"unexpected": True}
    client.post("/__control/paths", json={"/ai/embed": "normal"})
    assert "embedding" in client.post("/ai/embed", json={"text": "x"}).json()

    client.post("/__control/paths", json={"/ai/embed": "down"})
    client.post("/__control/reset")
    assert client.post("/ai/embed", json={"text": "x"}).status_code == 200
    # 경로별 장애 중에도 호출은 기록된다
    client.post("/__control/paths", json={"/ai/purchase-check/explain": "down"})
    assert client.post("/ai/purchase-check/explain", json={}).status_code == 503
    assert client.get("/__calls").json()[-1]["path"] == "/ai/purchase-check/explain"


@pytest.mark.smoke
def test_purchase_explain_picks_first_three_candidates_and_obeys_control() -> None:
    body = {
        "verdict": "THINK", "plus": 2,
        "similar": [{"id": "s1", "name": "x", "similarity": 0.5, "wearCount": 0, "lastWornAt": None}],
        "candidates": [{"clothesIds": [f"t{i}", "b1", "s1"]} for i in range(5)],
    }
    res = client.post("/ai/purchase-check/explain", json=body).json()
    assert [p["clothesIds"] for p in res["picks"]] == [["t0", "b1", "s1"], ["t1", "b1", "s1"], ["t2", "b1", "s1"]]
    assert res["headline"] == fake.PURCHASE_HEADLINE
    assert res["reasons"] == fake.PURCHASE_REASONS and len(res["reasons"]) == 3
    assert res["gap"] == fake.PURCHASE_GAP
    assert res["similarNotes"] == [{"id": "s1", "note": fake.PURCHASE_NOTE}]
    assert "verdict" not in res

    client.post("/__control/purchase", json={"verdict": "BUY", "extraPick": ["x1", "x2", "x3"]})
    res = client.post("/ai/purchase-check/explain", json=body).json()
    assert res["verdict"] == "BUY"
    assert res["picks"][0]["clothesIds"] == ["x1", "x2", "x3"]
    assert len(res["picks"]) == 4

    client.post("/__control/reset")
    assert "verdict" not in client.post("/ai/purchase-check/explain", json=body).json()


@pytest.mark.smoke
def test_forecast_follows_kst_hour_temps_and_fail_flag() -> None:
    default = client.get("/owm/data/2.5/forecast").json()
    assert default["cnt"] == len(default["list"]) == 32
    assert {s["main"]["temp"] for s in default["list"]} == {12.0}
    assert {s["pop"] for s in default["list"]} == {0.0}

    temps = {str(h): float(h) for h in range(0, 24, 3)}
    client.post("/__control/forecast", json={"temps": temps, "pop": 0.7, "windSpeed": 5.0})
    slots = client.get("/owm/data/2.5/forecast").json()["list"]
    today = datetime.now(KST).date()
    hours_today = set()
    for s in slots:
        at = datetime.fromtimestamp(s["dt"], KST)
        assert s["main"]["temp"] == float(at.hour)
        assert s["pop"] == 0.7 and s["wind"]["speed"] == 5.0
        assert s["weather"][0]["main"] == "Rain"
        assert datetime.strptime(s["dt_txt"], "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc) == at
        if at.date() == today:
            hours_today.add(at.hour)
    assert hours_today == set(range(0, 24, 3))

    client.post("/__control/forecast", json={"fail": True})
    assert client.get("/owm/data/2.5/forecast").status_code == 500
    assert client.get("/owm/data/2.5/weather").status_code == 200
    # 외부 날씨 API 흉내는 /ai/* 호출 기록에 남지 않는다
    assert all(not c["path"].startswith("/owm") for c in client.get("/__calls").json())
