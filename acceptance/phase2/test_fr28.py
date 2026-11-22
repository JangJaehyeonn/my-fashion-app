"""FR-28 동선 기온과 일교차 코디 규칙.

하네스 전제 (NOTES.md 참고 — acceptance/compose.yml 변경 필요)
- 백엔드는 OpenWeatherMap 예보를 가짜 AI의 /owm/data/2.5/forecast로 부른다
  (WEATHER_API_KEY=acceptance, WEATHER_FORECAST_URL=http://fake-ai:8000/owm/data/2.5/forecast,
   WEATHER_BASE_URL=http://fake-ai:8000/owm/data/2.5/weather).
- 가짜 예보는 /__control/forecast로 KST 시각(0·3·…·21시)별 기온과 강수 확률(pop 0~1)을 정한다.
- 예보 캐시가 테스트끼리 섞이지 않게 요청마다 다른 좌표를 쓴다.

API 가정
- 프로필: PUT /api/users/me {commuteOutAt: "HH:mm", commuteBackAt: "HH:mm", travelMode: WALK|TRANSIT|CAR}
- GET /api/weather/day?lat&lon → data {out, day, back: {time: "HH:mm", temperature, precipitationProbability(0~100),
  windSpeed}, tempRange}
- 홈 코디 추천 POST /api/outfits/recommend/closet 본문에 lat·lon. 가짜 AI 요청에 candidates [{clothesIds}]와
  appliedRules(적용한 규칙: "TEMP_RANGE", "RAIN")를 넣고, 백엔드 응답 data.appliedRules에도 같은 값을 넣는다.
- 기본 규칙 파일: 소재 '린넨' 상의의 적정 기온 하한은 12도보다 높다 (낮 12도에 맞지 않음). '니트'·'면' 상의는 12도에 맞다.
"""

from __future__ import annotations

import random
import uuid
from typing import Any

import httpx
import pytest

CLOSET_PATH = "/ai/outfits/recommend/closet"
SLOT_HOURS = range(0, 24, 3)


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    return res.json()["data"]


def seed(client: httpx.Client, image: bytes, names: dict[str, str]) -> dict[str, str]:
    return {alias: register(client, image, filename)["id"] for alias, filename in names.items()}


def random_coords() -> tuple[float, float]:
    rnd = random.Random(uuid.uuid4().int)
    return round(rnd.uniform(34.0, 37.9), 2), round(rnd.uniform(126.0, 128.9), 2)


def set_forecast(fake_ai: httpx.Client, morning: float, day: float, evening: float, pop: float = 0.0) -> None:
    """KST 0~9시 = 아침, 12·15시 = 낮, 18·21시 = 저녁. 8·13·19시를 보간하든 가까운 칸을 쓰든 같은 값이 된다."""
    temps = {str(h): (morning if h <= 9 else day if h <= 15 else evening) for h in SLOT_HOURS}
    fake_ai.post("/__control/forecast", json={"temps": temps, "pop": pop}).raise_for_status()


def recommend(client: httpx.Client) -> httpx.Response:
    lat, lon = random_coords()
    return client.post("/api/outfits/recommend/closet", json={
        "temperature": 12.0, "condition": "맑음", "situation": "DAILY", "lat": lat, "lon": lon})


def closet_call(fake_ai: httpx.Client) -> dict[str, Any]:
    calls = [c for c in fake_ai.get("/__calls").json() if c["path"] == CLOSET_PATH]
    assert len(calls) == 1, calls
    return calls[0]["json"]


def enable_attributes(fake_ai: httpx.Client) -> None:
    fake_ai.post("/__control/attributes", json={"enabled": True}).raise_for_status()


# 상의 3(니트·면 셔츠·린넨 셔츠), 하의 2, 신발 1, 아우터 1
RANGE_CLOSET = {
    "knit": "top_navy_knit.jpg", "cotton": "top_white_cotton_shirt.jpg", "linen": "top_beige_linen_shirt.jpg",
    "b1": "bottom_black_slacks.jpg", "b2": "bottom_grey_wool_pants.jpg",
    "s1": "shoes_black_leather_loafers.jpg",
    "o1": "outer_navy_wool_coat.jpg",
}


def categories(user, ids: dict[str, str]) -> dict[str, str]:
    listed = {c["id"]: c for c in user.client.get("/api/clothes").json()["data"]}
    return {i: listed[i]["category"] for i in ids.values()}


@pytest.mark.fr("FR-28")
def test_big_temperature_range_keeps_only_combos_valid_without_outer(user, fake_ai, tiny_jpg) -> None:
    enable_attributes(fake_ai)
    ids = seed(user.client, tiny_jpg, RANGE_CLOSET)
    category = categories(user, ids)
    set_forecast(fake_ai, morning=3, day=12, evening=7)

    res = recommend(user.client)
    assert res.status_code == 200, res.text
    assert "TEMP_RANGE" in res.json()["data"]["appliedRules"]

    body = closet_call(fake_ai)
    assert "TEMP_RANGE" in body["appliedRules"]
    candidates = body["candidates"]
    assert candidates, "낮 12도에 맞는 상의(니트·면)가 있으므로 후보가 있다"
    for combo in candidates:
        cats = sorted(category[str(i)] for i in combo["clothesIds"])
        without_outer = [c for c in cats if c != "OUTER"]
        # 아우터를 빼도 상의 1·하의 1·신발 1이 남는다
        assert sorted(without_outer) == ["BOTTOM", "SHOES", "TOP"], combo
        assert cats.count("OUTER") <= 1, combo
        # 낮 기온에 맞지 않는 린넨 상의는 아우터를 벗으면 성립하지 않는다
        assert ids["linen"] not in map(str, combo["clothesIds"]), combo


@pytest.mark.fr("FR-28")
@pytest.mark.parametrize(("morning", "evening", "applied"), [(4, 9, True), (5, 9, False), (10, 11, False)])
def test_temperature_range_threshold_is_eight_degrees(user, fake_ai, tiny_jpg, morning, evening, applied) -> None:
    # 낮 12도: 아침 4도면 일교차 8(적용), 5도면 7(미적용)
    enable_attributes(fake_ai)
    seed(user.client, tiny_jpg, {k: v for k, v in RANGE_CLOSET.items() if k != "linen"})
    set_forecast(fake_ai, morning=morning, day=12, evening=evening)

    res = recommend(user.client)
    assert res.status_code == 200, res.text
    body = closet_call(fake_ai)
    assert ("TEMP_RANGE" in body["appliedRules"]) is applied
    assert ("TEMP_RANGE" in res.json()["data"]["appliedRules"]) is applied
    assert body["candidates"]


@pytest.mark.fr("FR-28")
def test_rain_removes_suede_and_white_shoes(user, fake_ai, tiny_jpg) -> None:
    enable_attributes(fake_ai)
    ids = seed(user.client, tiny_jpg, {
        "white_top": "top_white_cotton_shirt.jpg", "knit": "top_navy_knit.jpg",
        "b1": "bottom_black_slacks.jpg", "b2": "bottom_beige_chino.jpg",
        "white_shoes": "shoes_white_sneakers.jpg", "suede_shoes": "shoes_brown_suede_loafers.jpg",
        "boots": "shoes_black_leather_boots.jpg",
        "suede_outer": "outer_beige_suede_jacket.jpg",
    })
    set_forecast(fake_ai, morning=12, day=13, evening=12, pop=0.7)

    res = recommend(user.client)
    assert res.status_code == 200, res.text
    assert "RAIN" in res.json()["data"]["appliedRules"]
    body = closet_call(fake_ai)
    assert "RAIN" in body["appliedRules"]
    assert "TEMP_RANGE" not in body["appliedRules"]

    removed = {ids["white_shoes"], ids["suede_shoes"], ids["suede_outer"]}
    sent = {str(c["id"]) for c in body["clothes"]}
    in_combos = {str(i) for combo in body["candidates"] for i in combo["clothesIds"]}
    assert not sent & removed, sent
    assert not in_combos & removed
    assert body["candidates"]
    # 흰색이어도 신발이 아니면 남는다
    assert ids["white_top"] in sent
    assert ids["boots"] in sent
    returned = {item["id"] for o in res.json()["data"]["outfits"] for item in o["items"]}
    assert not returned & removed


@pytest.mark.fr("FR-28")
def test_low_rain_probability_keeps_white_shoes(user, fake_ai, tiny_jpg) -> None:
    ids = seed(user.client, tiny_jpg, {
        "t1": "top_navy_knit.jpg", "b1": "bottom_black_slacks.jpg", "white_shoes": "shoes_white_sneakers.jpg",
    })
    set_forecast(fake_ai, morning=12, day=13, evening=12, pop=0.5)
    assert recommend(user.client).status_code == 200
    body = closet_call(fake_ai)
    assert "RAIN" not in body["appliedRules"]
    assert ids["white_shoes"] in {str(c["id"]) for c in body["clothes"]}


def get_day(client: httpx.Client) -> dict[str, Any]:
    lat, lon = random_coords()
    res = client.get("/api/weather/day", params={"lat": lat, "lon": lon})
    assert res.status_code == 200, res.text
    return res.json()["data"]


@pytest.mark.fr("FR-28")
def test_weather_day_defaults_to_8_13_19(user, fake_ai) -> None:
    set_forecast(fake_ai, morning=3, day=12, evening=7, pop=0.3)
    data = get_day(user.client)
    assert [data[k]["time"] for k in ("out", "day", "back")] == ["08:00", "13:00", "19:00"]
    assert [data[k]["temperature"] for k in ("out", "day", "back")] == pytest.approx([3, 12, 7])
    assert data["tempRange"] == pytest.approx(9)
    for key in ("out", "day", "back"):
        assert data[key]["precipitationProbability"] == pytest.approx(30)
        assert data[key]["windSpeed"] == pytest.approx(2.0)


@pytest.mark.fr("FR-28")
def test_weather_day_uses_commute_times(user, fake_ai) -> None:
    res = user.client.put("/api/users/me", json={
        "commuteOutAt": "06:00", "commuteBackAt": "21:00", "travelMode": "TRANSIT"})
    assert res.status_code == 200, res.text
    profile = res.json()["data"]
    assert str(profile["commuteOutAt"]).startswith("06:00")
    assert str(profile["commuteBackAt"]).startswith("21:00")
    assert profile["travelMode"] == "TRANSIT"

    # 칸마다 기온 = KST 시각 (6시 칸 6도, 21시 칸 21도). 정확히 칸 시각이라 보간·근사 방식과 무관하다
    fake_ai.post("/__control/forecast", json={"temps": {str(h): h for h in SLOT_HOURS}}).raise_for_status()
    data = get_day(user.client)
    assert data["out"]["time"] == "06:00"
    assert data["back"]["time"] == "21:00"
    assert data["out"]["temperature"] == pytest.approx(6)
    assert data["back"]["temperature"] == pytest.approx(21)
    assert data["day"]["time"] == "13:00"
    assert 12 <= data["day"]["temperature"] <= 15


@pytest.mark.fr("FR-28")
def test_forecast_failure_falls_back_to_current_weather_recommendation(user, fake_ai, tiny_jpg) -> None:
    seed(user.client, tiny_jpg, {
        "t1": "top_navy_knit.jpg", "b1": "bottom_black_slacks.jpg", "s1": "shoes_white_sneakers.jpg",
    })
    fake_ai.post("/__control/forecast", json={"fail": True}).raise_for_status()

    res = recommend(user.client)
    assert res.status_code == 200, res.text
    assert res.json()["data"]["outfits"], "현재 날씨 기반 기존 추천이 온다"
    body = closet_call(fake_ai)
    assert body["weather"]["temperature"] == pytest.approx(12.0)
    assert "TEMP_RANGE" not in (body.get("appliedRules") or [])
