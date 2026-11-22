"""인수 테스트용 가짜 AI 서버.

실제 AI 서버(ai-server/)와 같은 경로·응답 형식(camelCase)으로 답하되, 결과를 입력으로부터 결정적으로 만든다.
OpenAI 등 외부 서비스를 부르지 않는다.

옷 사진 분류 규칙 (업로드 파일 이름 `<종류>_<색>_<설명>.jpg`)
- 종류: top, bottom, outer, shoes → TOP/BOTTOM/OUTER/SHOES, 그 밖은 ETC
- 색: COLORS의 영어 이름 → 한국어, 모르면 "기타"
- 이름: "<한국어 색> <설명(밑줄→공백)>"
- 파일 이름이 fail_로 시작하면 500 (분류 실패)

제어 엔드포인트 (테스트 전용)
- POST /__control {"mode": "normal"|"down"|"slow"|"bad", "delayMs": 0}
  down = 모든 /ai/* 요청에 503, slow = delayMs만큼 늦게 정상 응답, bad = 200이지만 형식이 틀린 JSON
- POST /__control/reset : 모드와 호출 기록 초기화 (아래 Phase 2 제어 상태도 모두 초기화)
- GET /__calls : 지금까지 받은 /ai/* 호출 기록 [{method, path, json, files}]

새 엔드포인트(임베딩, 살까 말까 이유 문장 등)는 각 Phase의 인수 테스트 초안 PR이 여기에 함께 추가한다.

── Phase 2 추가 (FR-07·FR-08·FR-11·FR-13·FR-28) ─────────────────────────────

경로별 장애 (FR-07 임베딩만 다운, FR-11 이유 생성만 실패 등)
- POST /__control/paths {"/ai/embed": "down"|"bad"|"normal", ...}
  지정한 /ai/* 경로만 down = 503, bad = 200 + {"unexpected": true}. normal은 지정을 지운다. 전역 mode가 먼저 적용된다.

분류 속성 (FR-07) — 기본은 꺼져 있어 기존 분류 응답({category, color, name})이 그대로다.
- POST /__control/attributes {"enabled": true}
  켜면 분류 응답에 fit·material·pattern·season을 더한다. 파일 이름 설명 부분의 단어에서 표의 첫 일치 값을 쓰고, 없으면 null.
  fit: FITS, material: MATERIALS, pattern: PATTERNS(한국어), season: SEASONS(SPRING/SUMMER/FALL/WINTER).
  이름 규칙은 그대로다 (예: top_navy_wool_slim_knit.jpg → 이름 "네이비 wool slim knit", material "울", fit "슬림").

임베딩 (FR-07) POST /ai/embed
- 요청 {"text": "..."} → {"embedding": [1536개]}, 요청 {"texts": [...]} → {"embeddings": [[1536개], ...]}
- 텍스트에 단어 `probe`가 있으면 기준 벡터 P = (100, 0, 0, ...).
- 텍스트에 단어 `simNN`(NN = 0~100)이 있으면 (NN, a, b, c, d, 0, ...)이고 NN² + a² + b² + c² + d² = 100²인 정수 벡터.
  그래서 P와의 코사인 유사도가 정확히 NN/100이다 (예: sim85 → 0.85). 성분이 모두 정수라 float4로 저장해도 오차가 없다.
- 그 밖의 텍스트는 sha256(텍스트) 기반 고정 벡터(성분 -1~1). 서로 다른 텍스트끼리의 유사도는 0 근처, 같은 텍스트는 1.
- `probe`가 `simNN`보다 우선한다.

살까 말까 대표 코디·이유 문장 (FR-11) POST /ai/purchase-check/explain
- 요청: {item, verdict, plus, comboCountBefore, comboCountAfter, similar: [{id, name, similarity, wearCount, lastWornAt}],
  distribution: {카테고리: 벌 수}, candidates: [{clothesIds: [...]}]}
- 응답: {picks: [{clothesIds, label}], headline, reasons: [3개], gap, similarNotes: [{id, note}]}
  picks = candidates 앞 3개를 그대로, label은 "데일리". 문장은 고정 문구(PURCHASE_*).
- POST /__control/purchase {"verdict": "BUY", "extraPick": ["id", ...]}
  verdict를 주면 응답에 그 값의 "verdict"와 그 판정을 부추기는 headline을 넣는다 (백엔드는 무시해야 한다).
  extraPick을 주면 그 조합을 picks 맨 앞에 넣는다 (목록 밖 조합 흉내).

OpenWeatherMap 흉내 (FR-28) — 백엔드가 직접 부르는 외부 날씨 API. /ai/*가 아니라 호출 기록·전역 mode와 무관하다.
- GET /owm/data/2.5/forecast : 5일 3시간 예보 형식. 어제 0시(KST)부터 4일치 3시간 간격(KST 0·3·…·21시).
  각 칸의 기온 = temps[그 칸의 KST 시], 없으면 12.0. 강수 확률 pop(0~1), 풍속 windSpeed는 모든 칸이 같다.
- GET /owm/data/2.5/weather : 현재 날씨 형식. 기온 12.0, 맑음.
- POST /__control/forecast {"temps": {"0": 3, "3": 3, ...}, "pop": 0.7, "windSpeed": 3.0, "fail": false}
  fail=true면 예보만 500 (현재 날씨는 늘 성공). 기본값: temps 없음(모두 12.0), pop 0, windSpeed 2.0, fail false.
"""

from __future__ import annotations

import asyncio
import base64
import hashlib
import json
import math
import re
from datetime import datetime, timedelta, timezone
from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response

app = FastAPI(title="wearon fake ai")

CATEGORIES = {"top": "TOP", "bottom": "BOTTOM", "outer": "OUTER", "shoes": "SHOES"}
COLORS = {
    "white": "화이트",
    "black": "블랙",
    "navy": "네이비",
    "grey": "그레이",
    "gray": "그레이",
    "beige": "베이지",
    "brown": "브라운",
    "blue": "블루",
    "lightblue": "연청",
    "darkblue": "진청",
    "red": "레드",
    "green": "그린",
    "lime": "라임",
    "pink": "핑크",
}
# 1x1 white PNG
TINY_PNG = base64.b64encode(
    bytes.fromhex(
        "89504e470d0a1a0a0000000d4948445200000001000000010806000000"
        "1f15c4890000000d49444154789c6360f8ffff3f0005fe02fea7d6a4"
        "e40000000049454e44ae426082"
    )
).decode()

STATE: dict[str, Any] = {"mode": "normal", "delay_ms": 0, "calls": []}

# ── Phase 2 상태 ────────────────────────────────────────────────

EMBEDDING_DIM = 1536
FITS = {"slim": "슬림", "regular": "레귤러", "wide": "와이드", "oversize": "오버핏", "crop": "크롭"}
MATERIALS = {
    "wool": "울", "cotton": "면", "linen": "린넨", "denim": "데님", "jeans": "데님", "suede": "스웨이드",
    "leather": "가죽", "padding": "패딩", "knit": "니트", "fleece": "플리스", "nylon": "나일론",
}
PATTERNS = {"stripe": "스트라이프", "check": "체크", "dot": "도트", "floral": "플로럴", "plain": "무지"}
SEASONS = {"spring": "SPRING", "summer": "SUMMER", "fall": "FALL", "autumn": "FALL", "winter": "WINTER"}
PURCHASE_HEADLINE = "가짜 AI 한 줄 평"
PURCHASE_REASONS = ["가짜 이유 1", "가짜 이유 2", "가짜 이유 3"]
PURCHASE_GAP = "가짜 옷장 빈틈"
PURCHASE_NOTE = "가짜 다른 점"
OVERRIDE_HEADLINES = {"BUY": "무조건 사세요", "THINK": "고민해 보세요", "SIMILAR": "비슷한 옷이 있어요"}
KST = timezone(timedelta(hours=9))
FORECAST_DEFAULT_TEMP = 12.0
PHASE2_DEFAULTS: dict[str, Any] = {
    "paths": {},
    "attributes": False,
    "purchase": {},
    "forecast": {"temps": {}, "pop": 0.0, "windSpeed": 2.0, "fail": False},
}


def reset_phase2() -> None:
    STATE.update(json.loads(json.dumps(PHASE2_DEFAULTS)))


reset_phase2()


def classify_name(filename: str) -> dict[str, str] | None:
    """파일 이름으로 분류 결과를 만든다. fail_ 접두어면 None."""
    stem = filename.rsplit("/", 1)[-1].rsplit(".", 1)[0].lower()
    if stem.startswith("fail_"):
        return None
    parts = stem.split("_")
    category = CATEGORIES.get(parts[0], "ETC")
    color = COLORS.get(parts[1], "기타") if len(parts) > 1 else "기타"
    desc = " ".join(parts[2:]) if len(parts) > 2 else parts[0]
    return {"category": category, "color": color, "name": f"{color} {desc}".strip()}


def first_match(words: list[str], table: dict[str, str]) -> str | None:
    for word in words:
        if word in table:
            return table[word]
    return None


def attributes_from_name(filename: str) -> dict[str, str | None]:
    """파일 이름 설명 부분(세 번째 조각부터)의 단어로 fit·material·pattern·season을 정한다. 없으면 None."""
    stem = filename.rsplit("/", 1)[-1].rsplit(".", 1)[0].lower()
    words = stem.split("_")[2:]
    return {
        "fit": first_match(words, FITS),
        "material": first_match(words, MATERIALS),
        "pattern": first_match(words, PATTERNS),
        "season": first_match(words, SEASONS),
    }


def four_squares(n: int) -> list[int]:
    """a²+b²+c²+d² = n인 음 아닌 정수 4개 (사전순 가장 큰 a부터 찾는다, 결정적)."""
    for a in range(math.isqrt(n), -1, -1):
        ra = n - a * a
        for b in range(math.isqrt(ra), -1, -1):
            rb = ra - b * b
            for c in range(math.isqrt(rb), -1, -1):
                rc = rb - c * c
                d = math.isqrt(rc)
                if d * d == rc:
                    return [a, b, c, d]
    raise ValueError(n)


def hash_vector(text: str) -> list[float]:
    values: list[float] = []
    counter = 0
    while len(values) < EMBEDDING_DIM:
        digest = hashlib.sha256(f"{counter}:{text}".encode()).digest()
        values.extend(round(b / 127.5 - 1.0, 6) for b in digest)
        counter += 1
    return values[:EMBEDDING_DIM]


def embed_text(text: str) -> list[float]:
    """임베딩 규칙 (docstring 참고): probe → 기준 벡터, simNN → 기준과 유사도 NN/100, 그 밖은 해시 벡터."""
    words = re.findall(r"[a-z0-9]+", text.lower())
    vector = [0.0] * EMBEDDING_DIM
    if "probe" in words:
        vector[0] = 100.0
        return vector
    for word in words:
        m = re.fullmatch(r"sim(\d{1,3})", word)
        if m and int(m.group(1)) <= 100:
            nn = int(m.group(1))
            vector[0] = float(nn)
            for i, v in enumerate(four_squares(10000 - nn * nn), start=1):
                vector[i] = float(v)
            return vector
    return hash_vector(text)


@app.middleware("http")
async def control(request: Request, call_next: Any) -> Response:
    if not request.url.path.startswith("/ai/"):
        return await call_next(request)
    raw = await request.body()
    record: dict[str, Any] = {"method": request.method, "path": request.url.path, "json": None, "files": []}
    ctype = request.headers.get("content-type", "")
    if ctype.startswith("application/json") and raw:
        try:
            record["json"] = json.loads(raw)
        except json.JSONDecodeError:
            record["json"] = None
    elif ctype.startswith("multipart/"):
        record["files"] = [
            part.split(b'filename="', 1)[1].split(b'"', 1)[0].decode("utf-8", "replace")
            for part in raw.split(b"\r\n")
            if b'filename="' in part
        ]
    STATE["calls"].append(record)

    async def receive() -> dict[str, Any]:
        return {"type": "http.request", "body": raw, "more_body": False}

    request = Request(request.scope, receive)
    mode = STATE["mode"]
    if mode == "down":
        return JSONResponse({"detail": "fake ai down"}, status_code=503)
    if mode == "slow":
        await asyncio.sleep(STATE["delay_ms"] / 1000)
    if mode == "bad":
        return JSONResponse({"unexpected": True})
    # Phase 2: 경로별 장애 (지정이 없으면 기존 동작 그대로)
    path_mode = STATE["paths"].get(request.url.path)
    if path_mode == "down":
        return JSONResponse({"detail": "fake ai path down"}, status_code=503)
    if path_mode == "bad":
        return JSONResponse({"unexpected": True})
    return await call_next(request)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/__control")
async def set_control(request: Request) -> dict[str, Any]:
    body = await request.json()
    STATE["mode"] = body.get("mode", "normal")
    STATE["delay_ms"] = int(body.get("delayMs", 0))
    return {"mode": STATE["mode"], "delayMs": STATE["delay_ms"]}


@app.post("/__control/reset")
async def reset() -> dict[str, str]:
    STATE.update(mode="normal", delay_ms=0, calls=[])
    reset_phase2()
    return {"status": "reset"}


@app.get("/__calls")
async def calls() -> list[dict[str, Any]]:
    return list(STATE["calls"])


@app.post("/__control/paths")
async def set_paths(request: Request) -> dict[str, Any]:
    body: dict[str, str] = await request.json()
    for path, mode in body.items():
        if mode == "normal":
            STATE["paths"].pop(path, None)
        else:
            STATE["paths"][path] = mode
    return {"paths": dict(STATE["paths"])}


@app.post("/__control/attributes")
async def set_attributes(request: Request) -> dict[str, bool]:
    body = await request.json()
    STATE["attributes"] = bool(body.get("enabled", False))
    return {"enabled": STATE["attributes"]}


@app.post("/__control/purchase")
async def set_purchase(request: Request) -> dict[str, Any]:
    body = await request.json()
    STATE["purchase"] = {k: body[k] for k in ("verdict", "extraPick") if body.get(k) is not None}
    return dict(STATE["purchase"])


@app.post("/__control/forecast")
async def set_forecast(request: Request) -> dict[str, Any]:
    body = await request.json()
    forecast = STATE["forecast"]
    if "temps" in body:
        forecast["temps"] = {int(h): float(t) for h, t in body["temps"].items()}
    for key in ("pop", "windSpeed"):
        if key in body:
            forecast[key] = float(body[key])
    if "fail" in body:
        forecast["fail"] = bool(body["fail"])
    return {**forecast, "temps": {str(h): t for h, t in forecast["temps"].items()}}


@app.post("/ai/clothes/classify")
async def classify(request: Request) -> Response:
    form = await request.form()
    image = form.get("image")
    filename = getattr(image, "filename", "") or ""
    result = classify_name(filename)
    if result is None:
        return JSONResponse({"detail": "classify failed"}, status_code=500)
    if STATE["attributes"]:
        return JSONResponse({**result, **attributes_from_name(filename)})
    return JSONResponse(result)


@app.post("/ai/outfits/recommend/closet")
async def recommend_closet(request: Request) -> dict[str, Any]:
    body = await request.json()
    by_cat: dict[str, list[str]] = {}
    for item in body.get("clothes", []):
        by_cat.setdefault(item.get("category", "ETC"), []).append(str(item["id"]))
    outfits = []
    if by_cat.get("TOP") and by_cat.get("BOTTOM") and by_cat.get("SHOES"):
        ids = [by_cat["TOP"][0], by_cat["BOTTOM"][0], by_cat["SHOES"][0]]
        outfits.append({"clothesIds": ids, "reason": "가짜 AI 추천 이유", "styleTag": "캐주얼"})
        if by_cat.get("OUTER"):
            outfits.append(
                {"clothesIds": [by_cat["OUTER"][0], *ids], "reason": "가짜 AI 추천 이유 2", "styleTag": "미니멀"}
            )
    return {"outfits": outfits}


@app.post("/ai/outfits/recommend/situation")
async def recommend_situation() -> dict[str, Any]:
    return {
        "outfits": [
            {
                "description": "화이트 셔츠, 블랙 슬랙스, 로퍼",
                "reason": "가짜 AI 추천 이유",
                "styleTag": "미니멀",
                "shoppingSuggestions": [
                    {"item": "블랙 슬랙스", "site": "무신사", "searchKeyword": "블랙 슬랙스"}
                ],
            }
        ]
    }


@app.post("/ai/shopping/recommend")
async def recommend_shopping() -> dict[str, Any]:
    return {
        "items": [
            {
                "item": "블랙 슬랙스",
                "reason": "가짜 AI 이유",
                "estimatedPrice": 50000,
                "site": "무신사",
                "searchKeyword": "블랙 슬랙스",
            }
        ],
        "totalEstimatedPrice": 50000,
        "usageTip": "가짜 활용법",
    }


@app.post("/ai/diagnosis")
async def diagnosis() -> dict[str, Any]:
    return {
        "score": 70,
        "feedback": "가짜 진단",
        "similarStyles": [{"styleTag": "미니멀", "description": "가짜 설명"}],
    }


@app.post("/ai/vton")
async def vton() -> dict[str, str]:
    return {"resultImageBase64": TINY_PNG}


@app.get("/ai/weather")
async def weather() -> dict[str, Any]:
    return {"temperature": 12.0, "condition": "맑음", "humidity": 40, "windSpeed": 2.0}


# ── Phase 2 엔드포인트 ─────────────────────────────────────────


@app.post("/ai/embed")
async def embed(request: Request) -> Response:
    body = await request.json()
    if isinstance(body.get("texts"), list):
        return JSONResponse({"embeddings": [embed_text(str(t)) for t in body["texts"]]})
    if isinstance(body.get("text"), str):
        return JSONResponse({"embedding": embed_text(body["text"])})
    return JSONResponse({"detail": "text or texts required"}, status_code=422)


@app.post("/ai/purchase-check/explain")
async def purchase_explain(request: Request) -> dict[str, Any]:
    body = await request.json()
    ctl = STATE["purchase"]
    picks: list[dict[str, Any]] = []
    if ctl.get("extraPick"):
        picks.append({"clothesIds": [str(i) for i in ctl["extraPick"]], "label": "데일리"})
    for candidate in (body.get("candidates") or [])[:3]:
        picks.append({"clothesIds": [str(i) for i in candidate.get("clothesIds", [])], "label": "데일리"})
    result: dict[str, Any] = {
        "picks": picks,
        "headline": PURCHASE_HEADLINE,
        "reasons": list(PURCHASE_REASONS),
        "gap": PURCHASE_GAP,
        "similarNotes": [{"id": str(s.get("id")), "note": PURCHASE_NOTE} for s in body.get("similar") or []],
    }
    if ctl.get("verdict"):
        result["verdict"] = ctl["verdict"]
        result["headline"] = OVERRIDE_HEADLINES.get(ctl["verdict"], ctl["verdict"])
    return result


def forecast_slots() -> list[dict[str, Any]]:
    forecast = STATE["forecast"]
    start = datetime.now(KST).replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(days=1)
    pop = forecast["pop"]
    slots = []
    for i in range(4 * 8):
        at = start + timedelta(hours=3 * i)
        temp = forecast["temps"].get(at.hour, FORECAST_DEFAULT_TEMP)
        main = "Rain" if pop >= 0.6 else "Clear"
        slots.append({
            "dt": int(at.timestamp()),
            "main": {"temp": temp, "feels_like": temp, "temp_min": temp, "temp_max": temp, "humidity": 50},
            "weather": [{"id": 500 if main == "Rain" else 800, "main": main, "description": main.lower()}],
            "wind": {"speed": forecast["windSpeed"]},
            "pop": pop,
            "dt_txt": at.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
        })
    return slots


@app.get("/owm/data/2.5/forecast")
async def owm_forecast() -> Response:
    if STATE["forecast"]["fail"]:
        return JSONResponse({"cod": "500", "message": "fake forecast failure"}, status_code=500)
    slots = forecast_slots()
    return JSONResponse({
        "cod": "200",
        "message": 0,
        "cnt": len(slots),
        "list": slots,
        "city": {"name": "Fake", "country": "KR", "timezone": 32400},
    })


@app.get("/owm/data/2.5/weather")
async def owm_weather() -> Response:
    # 예보 실패(fail)와 무관하게 늘 성공한다: FR-28 "예보가 실패하면 현재 날씨 기반 추천으로 돌아간다"
    return JSONResponse({
        "weather": [{"id": 800, "main": "Clear", "description": "clear sky"}],
        "main": {"temp": 12.0, "feels_like": 12.0, "humidity": 40},
        "wind": {"speed": 2.0},
        "timezone": 32400,
        "cod": 200,
    })
