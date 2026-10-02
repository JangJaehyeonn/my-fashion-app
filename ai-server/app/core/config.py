from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    openai_api_key: str
    weather_api_key: str
    # 코디 추천(상황/옷장)에 쓰는 텍스트 모델 — OPENAI_MODEL 환경변수로 교체 가능
    openai_model: str = "gpt-4o-mini"
    # 공개 Space라 토큰 없이도 호출 가능 — 누락돼도 AI 서버 전체가 기동 실패하지 않도록 선택값으로 둠
    hf_api_token: str | None = None
    hf_vton_space_id: str = "yisol/IDM-VTON"


settings = Settings()
