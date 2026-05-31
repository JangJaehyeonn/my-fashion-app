from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import httpx

from app.core.config import settings
from app.schemas.weather import WeatherResponse

KST = ZoneInfo("Asia/Seoul")
_KMA_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
_BASE_TIMES = ["0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300"]

_SKY_MAP = {1: "맑음", 3: "구름많음", 4: "흐림"}
_PTY_MAP = {1: "비", 2: "비/눈", 3: "눈", 4: "소나기"}


def _get_base_datetime() -> tuple[str, str]:
    """기상청 API 호출에 사용할 base_date, base_time을 반환합니다."""
    now = datetime.now(tz=KST) - timedelta(minutes=10)
    current_hhmm = int(now.strftime("%H%M"))

    selected_time = None
    for bt in _BASE_TIMES:
        if int(bt) <= current_hhmm:
            selected_time = bt

    if selected_time is None:
        # 자정~02:10 사이에는 전날 23:00 예보 사용
        yesterday = now - timedelta(days=1)
        return yesterday.strftime("%Y%m%d"), "2300"

    return now.strftime("%Y%m%d"), selected_time


async def get_current_weather(nx: int = 60, ny: int = 127) -> WeatherResponse:
    """기상청 단기예보 API로 현재 날씨를 조회합니다. nx/ny 기본값은 서울시청 기준."""
    base_date, base_time = _get_base_datetime()

    params = {
        "serviceKey": settings.weather_api_key,
        "pageNo": 1,
        "numOfRows": 1000,
        "dataType": "JSON",
        "base_date": base_date,
        "base_time": base_time,
        "nx": nx,
        "ny": ny,
    }

    async with httpx.AsyncClient(timeout=10.0) as http_client:
        response = await http_client.get(_KMA_URL, params=params)
        response.raise_for_status()

    items = response.json()["response"]["body"]["items"]["item"]

    # (fcst_date, fcst_time, category) → value 인덱싱
    fcst: dict[tuple[str, str, str], str] = {}
    for item in items:
        key = (item["fcstDate"], item["fcstTime"], item["category"])
        fcst[key] = item["fcstValue"]

    now_kst = datetime.now(tz=KST)

    def _nearest_value(category: str) -> str | None:
        for hour_offset in range(24):
            t = now_kst + timedelta(hours=hour_offset)
            val = fcst.get((t.strftime("%Y%m%d"), t.strftime("%H") + "00", category))
            if val is not None:
                return val
        return None

    tmp = _nearest_value("TMP")
    sky = _nearest_value("SKY")
    pty = _nearest_value("PTY")
    reh = _nearest_value("REH")
    wsd = _nearest_value("WSD")

    pty_val = int(pty) if pty else 0
    sky_val = int(sky) if sky else 1

    condition = _PTY_MAP.get(pty_val) if pty_val != 0 else _SKY_MAP.get(sky_val, "맑음")

    return WeatherResponse(
        temperature=float(tmp) if tmp else 20.0,
        condition=condition or "맑음",
        humidity=int(float(reh)) if reh else 50,
        wind_speed=float(wsd) if wsd else 0.0,
    )
