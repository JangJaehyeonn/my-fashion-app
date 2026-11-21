"""하네스 자체 점검. 스택 기동, 테스트 사용자 인증, 백엔드 → 가짜 AI 연결을 확인한다."""

from __future__ import annotations

import httpx
import pytest


@pytest.mark.smoke
def test_authenticated_user_can_read_profile(user) -> None:
    res = user.client.get("/api/users/me")
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["success"] is True
    assert body["data"]["nickname"] == user.nickname


@pytest.mark.smoke
def test_unauthenticated_request_is_rejected(stack) -> None:
    res = httpx.get(f"{stack.api}/api/clothes", timeout=10)
    assert res.status_code == 401


@pytest.mark.smoke
def test_backend_reaches_fake_ai(user, fake_ai) -> None:
    # 옷장 없이 쓰는 상황 추천은 백엔드 → AI 서버 경로를 그대로 지나간다 (저장소·분류 정책과 무관).
    res = user.client.post(
        "/api/outfits/recommend/situation",
        json={"temperature": 12.0, "condition": "맑음", "situation": "DAILY"},
    )
    assert res.status_code == 200, res.text
    outfits = res.json()["data"]["outfits"]
    assert outfits[0]["reason"] == "가짜 AI 추천 이유"

    calls = fake_ai.get("/__calls").json()
    assert [c["path"] for c in calls] == ["/ai/outfits/recommend/situation"]
    assert calls[0]["json"]["situation"] == "DAILY"


@pytest.mark.smoke
def test_closet_starts_empty(user) -> None:
    res = user.client.get("/api/clothes")
    assert res.status_code == 200, res.text
    assert res.json()["data"] == []


@pytest.mark.smoke
def test_fake_ai_down_is_reported_not_crashed(user, fake_ai) -> None:
    fake_ai.post("/__control", json={"mode": "down"}).raise_for_status()
    res = user.client.post(
        "/api/outfits/recommend/situation",
        json={"temperature": 12.0, "condition": "맑음", "situation": "DAILY"},
    )
    assert res.status_code == 503, res.text
    assert res.json()["success"] is False
