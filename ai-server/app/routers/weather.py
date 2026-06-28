import httpx
from fastapi import APIRouter, HTTPException

from app.schemas.weather import WeatherResponse
from app.services.weather_service import get_current_weather

router = APIRouter()


@router.get("/ai/weather", response_model=WeatherResponse, response_model_by_alias=True)
async def get_weather(lat: float = 37.5665, lon: float = 126.9780):
    """현재 날씨를 조회합니다. lat/lon은 위경도 좌표 (기본값: 서울시청)."""
    try:
        return await get_current_weather(lat=lat, lon=lon)
    except httpx.HTTPStatusError as e:
        raise HTTPException(status_code=502, detail=f"OpenWeatherMap API 오류: {e.response.status_code}")
    except httpx.RequestError as e:
        raise HTTPException(status_code=502, detail=f"OpenWeatherMap API 연결 실패: {str(e)}")
    except (KeyError, ValueError) as e:
        raise HTTPException(status_code=502, detail=f"날씨 데이터 파싱 실패: {str(e)}")
