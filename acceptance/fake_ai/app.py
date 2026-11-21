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
- POST /__control/reset : 모드와 호출 기록 초기화
- GET /__calls : 지금까지 받은 /ai/* 호출 기록 [{method, path, json, files}]

새 엔드포인트(임베딩, 살까 말까 이유 문장 등)는 각 Phase의 인수 테스트 초안 PR이 여기에 함께 추가한다.
"""

from __future__ import annotations

import asyncio
import base64
import json
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
    return {"status": "reset"}


@app.get("/__calls")
async def calls() -> list[dict[str, Any]]:
    return list(STATE["calls"])


@app.post("/ai/clothes/classify")
async def classify(request: Request) -> Response:
    form = await request.form()
    image = form.get("image")
    filename = getattr(image, "filename", "") or ""
    result = classify_name(filename)
    if result is None:
        return JSONResponse({"detail": "classify failed"}, status_code=500)
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
