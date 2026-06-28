import httpx

from app.core.config import settings
from app.schemas.weather import WeatherResponse

_OWM_URL = "https://api.openweathermap.org/data/2.5/weather"

_CONDITION_MAP = {
    "Clear": "맑음",
    "Clouds": "구름많음",
    "Rain": "비",
    "Drizzle": "비",
    "Thunderstorm": "천둥번개",
    "Snow": "눈",
    "Mist": "안개",
    "Fog": "안개",
    "Haze": "안개",
    "Dust": "황사",
    "Sand": "황사",
}


async def get_current_weather(lat: float = 37.5665, lon: float = 126.9780) -> WeatherResponse:
    """OpenWeatherMap API로 현재 날씨를 조회합니다. 기본값은 서울시청 좌표."""
    params = {
        "lat": lat,
        "lon": lon,
        "appid": settings.weather_api_key,
        "units": "metric",
        "lang": "kr",
    }

    async with httpx.AsyncClient(timeout=10.0) as http_client:
        response = await http_client.get(_OWM_URL, params=params)
        response.raise_for_status()

    data = response.json()
    main_weather = data["weather"][0]["main"]

    return WeatherResponse(
        temperature=data["main"]["temp"],
        condition=_CONDITION_MAP.get(main_weather, main_weather),
        humidity=data["main"]["humidity"],
        wind_speed=data["wind"]["speed"],
    )
