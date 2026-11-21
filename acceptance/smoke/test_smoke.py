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
def test_closet_starts_empty_and_failed_classification_saves_nothing(user, fake_ai, tiny_jpg) -> None:
    res = user.client.get("/api/clothes")
    assert res.status_code == 200, res.text
    assert res.json()["data"] == []

    # 가짜 AI는 fail_ 접두어 파일을 분류 실패(500)로 돌려준다. 백엔드는 503으로 알리고 아무것도 저장하지 않는다.
    res = user.client.post(
        "/api/clothes", files={"image": ("fail_top_white.jpg", tiny_jpg, "image/jpeg")}
    )
    assert res.status_code == 503, res.text
    assert res.json()["success"] is False

    calls = fake_ai.get("/__calls").json()
    assert [c["path"] for c in calls] == ["/ai/clothes/classify"]
    assert calls[0]["files"] == ["fail_top_white.jpg"]

    assert user.client.get("/api/clothes").json()["data"] == []
