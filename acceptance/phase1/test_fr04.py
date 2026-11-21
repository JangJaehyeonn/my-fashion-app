"""FR-04 동의 이력 저장.

동의 이력은 조회 API가 요구사항에 없어 `stack.sql`로 consent_records를 본다.
가정 (NOTES.md 참고): 컬럼 user_id, version, agreed_at(동의 시각), revoked_at(철회 시각).
"""

from __future__ import annotations

import uuid

import httpx
import pytest

from acceptance.conftest import Stack, User, token_for

# FR-04 이전부터 있던 UserResponse 필드. 응답 형식 유지 = 이 필드가 모두 그대로 있다 (새 필드 추가는 허용).
USER_RESPONSE_KEYS = {
    "id", "email", "nickname", "profileImageUrl", "provider", "loginId", "height", "weight",
    "bodyType", "preferredStyle", "preferredStyles", "ageConfirmedAt", "vtonConsentAt",
}
# FR-05에서 만 14세 확인 API가 약관 동의도 함께 받으므로 모두 동의한 본문을 보낸다 (이전 API는 본문을 무시한다).
AGE_CONFIRM_BODY = {"ageOver14": True, "termsAgreed": True, "privacyAgreed": True}


def make_unconfirmed_social_user(stack: Stack, nickname: str = "미확인소셜") -> User:
    """만 14세 확인 전인 소셜 가입 사용자 (conftest.make_user와 같지만 age_confirmed_at이 비어 있다)."""
    user_id = str(uuid.uuid4())
    stack.sql(
        "INSERT INTO users (id, nickname, provider, provider_id, created_at, updated_at) "
        f"VALUES ('{user_id}', '{nickname}', 'google', 'acceptance-{user_id}', now(), now())"
    )
    client = httpx.Client(
        base_url=stack.api, headers={"Authorization": f"Bearer {token_for(user_id)}"}, timeout=30
    )
    return User(user_id, nickname, client)


def consent_count(stack: Stack, user_id: str) -> int:
    return int(stack.sql(f"SELECT count(*) FROM consent_records WHERE user_id = '{user_id}'"))


def assert_user_response_format(res: httpx.Response) -> dict:
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["success"] is True
    data = body["data"]
    missing = USER_RESPONSE_KEYS - set(data)
    assert not missing, f"기존 응답 필드가 빠졌습니다: {missing}"
    return data


@pytest.mark.fr("FR-04")
def test_vton_consent_grant_revoke_grant_keeps_two_records(stack, user) -> None:
    assert user.client.post("/api/users/me/consents/vton").status_code == 200
    assert user.client.delete("/api/users/me/consents/vton").status_code == 200
    assert user.client.post("/api/users/me/consents/vton").status_code == 200

    # make_user 사용자는 SQL로 만들어져 다른 동의 이력이 없으므로, 이 사용자의 이력 = 가상 피팅 동의 이력
    assert consent_count(stack, user.id) == 2
    rows = stack.sql(
        "SELECT (revoked_at IS NOT NULL)::text || ',' || (version IS NOT NULL)::text "
        f"FROM consent_records WHERE user_id = '{user.id}' ORDER BY agreed_at, revoked_at NULLS LAST"
    ).splitlines()
    assert rows == ["true,true", "false,true"], rows


@pytest.mark.fr("FR-04")
def test_vton_request_after_revoke_is_forbidden(user, tiny_jpg) -> None:
    assert user.client.post("/api/users/me/consents/vton").status_code == 200
    assert user.client.delete("/api/users/me/consents/vton").status_code == 200

    res = user.client.post(
        "/api/vton",
        files={
            "personImage": ("person.jpg", tiny_jpg, "image/jpeg"),
            "garmentImage": ("top_navy_knit.jpg", tiny_jpg, "image/jpeg"),
        },
    )
    assert res.status_code == 403, res.text
    assert res.json()["success"] is False


@pytest.mark.fr("FR-04")
def test_vton_consent_api_response_format_unchanged(user) -> None:
    granted = assert_user_response_format(user.client.post("/api/users/me/consents/vton"))
    assert granted["id"] == user.id
    assert granted["vtonConsentAt"] is not None

    revoked = assert_user_response_format(user.client.delete("/api/users/me/consents/vton"))
    assert revoked["vtonConsentAt"] is None

    me = assert_user_response_format(user.client.get("/api/users/me"))
    assert me["vtonConsentAt"] is None


@pytest.mark.fr("FR-04")
def test_age_confirmation_api_response_format_unchanged_and_recorded(stack) -> None:
    u = make_unconfirmed_social_user(stack)
    try:
        before = assert_user_response_format(u.client.get("/api/users/me"))
        assert before["ageConfirmedAt"] is None

        data = assert_user_response_format(u.client.post("/api/users/me/age-confirmation", json=AGE_CONFIRM_BODY))
        assert data["id"] == u.id
        assert data["ageConfirmedAt"] is not None
        assert consent_count(stack, u.id) >= 1
    finally:
        u.client.close()


@pytest.mark.fr("FR-04")
def test_account_deletion_removes_consent_records(stack, user) -> None:
    assert user.client.post("/api/users/me/consents/vton").status_code == 200
    assert user.client.delete("/api/users/me/consents/vton").status_code == 200
    assert consent_count(stack, user.id) >= 1

    res = user.client.delete("/api/users/me")
    assert res.status_code == 200, res.text
    assert consent_count(stack, user.id) == 0
