"""FR-05 이용약관과 가입 동의.

가정 (NOTES.md 참고)
- 아이디 가입 본문의 동의 필드: 기존 `ageOver14`에 `termsAgreed`, `privacyAgreed`를 더한다 (모두 true여야 가입).
- 소셜 첫 가입의 약관 동의는 기존 POST /api/users/me/age-confirmation이 같은 세 필드를 본문으로 받는다.
- 동의 이력: consent_records(user_id, consent_type, revoked_at) — FR-04와 같은 테이블.
"""

from __future__ import annotations

import uuid
from typing import Any

import httpx
import pytest

from acceptance.conftest import Stack, User, token_for

AGREEMENT_KEYS = ("termsAgreed", "privacyAgreed", "ageOver14")
ALL_AGREED = {key: True for key in AGREEMENT_KEYS}
PASSWORD = "wearon1234"
SITUATION_REQUEST = {"temperature": 12.0, "condition": "맑음", "situation": "DAILY"}


def new_login_id() -> str:
    return f"acc{uuid.uuid4().hex[:12]}"


def signup_body(login_id: str, **agreements: Any) -> dict[str, Any]:
    return {"loginId": login_id, "password": PASSWORD, "nickname": "약관테스트", **agreements}


def login_id_available(stack: Stack, login_id: str) -> bool:
    res = httpx.get(f"{stack.api}/api/auth/check-id", params={"loginId": login_id}, timeout=10)
    assert res.status_code == 200, res.text
    return res.json()["data"]["available"]


def consent_summary(stack: Stack, user_id: str) -> tuple[int, int, int]:
    """(전체 건수, 종류 수, 철회된 건수)"""
    row = stack.sql(
        "SELECT count(*) || ',' || count(DISTINCT consent_type) || ',' || count(revoked_at) "
        f"FROM consent_records WHERE user_id = '{user_id}'"
    )
    total, kinds, revoked = (int(v) for v in row.split(","))
    return total, kinds, revoked


def make_unconfirmed_social_user(stack: Stack, nickname: str = "약관전소셜") -> User:
    """소셜 첫 가입 직후 상태: 만 14세 확인·약관 동의 전 (age_confirmed_at 비어 있음, 동의 이력 없음)."""
    user_id = str(uuid.uuid4())
    stack.sql(
        "INSERT INTO users (id, nickname, provider, provider_id, created_at, updated_at) "
        f"VALUES ('{user_id}', '{nickname}', 'google', 'acceptance-{user_id}', now(), now())"
    )
    client = httpx.Client(
        base_url=stack.api, headers={"Authorization": f"Bearer {token_for(user_id)}"}, timeout=30
    )
    return User(user_id, nickname, client)


# ── /terms ─────────────────────────────────────────────────────


@pytest.mark.fr("FR-05")
def test_terms_page_is_public_utf8_html(stack) -> None:
    res = httpx.get(f"{stack.api}/terms", timeout=10)
    assert res.status_code == 200, res.text
    content_type = res.headers["content-type"].replace(" ", "").lower()
    assert content_type == "text/html;charset=utf-8", res.headers["content-type"]
    assert "이용약관" in res.text


# ── 아이디 가입 ────────────────────────────────────────────────


MISSING_CASES = [
    pytest.param({**ALL_AGREED, key: False}, id=f"{key}-false") for key in AGREEMENT_KEYS
] + [
    pytest.param({k: v for k, v in ALL_AGREED.items() if k != key}, id=f"{key}-absent") for key in AGREEMENT_KEYS
]


@pytest.mark.fr("FR-05")
@pytest.mark.parametrize("agreements", MISSING_CASES)
def test_signup_missing_any_agreement_is_rejected(stack, agreements: dict[str, bool]) -> None:
    login_id = new_login_id()
    res = httpx.post(f"{stack.api}/api/auth/signup", json=signup_body(login_id, **agreements), timeout=10)
    assert res.status_code == 400, res.text
    assert res.json()["success"] is False

    assert login_id_available(stack, login_id) is True
    assert stack.sql(f"SELECT count(*) FROM users WHERE login_id = '{login_id}'") == "0"


@pytest.mark.fr("FR-05")
def test_signup_with_all_agreements_succeeds_and_records_three_consents(stack) -> None:
    login_id = new_login_id()
    res = httpx.post(f"{stack.api}/api/auth/signup", json=signup_body(login_id, **ALL_AGREED), timeout=10)
    assert res.status_code in (200, 201), res.text
    body = res.json()
    assert body["success"] is True
    access_token = body["data"]["accessToken"]

    with httpx.Client(base_url=stack.api, headers={"Authorization": f"Bearer {access_token}"}, timeout=10) as c:
        me = c.get("/api/users/me")
        assert me.status_code == 200, me.text
        user_id = me.json()["data"]["id"]
        # 가입과 함께 동의를 마쳤으므로 일반 API를 바로 쓸 수 있다
        assert c.get("/api/clothes").status_code == 200

    assert consent_summary(stack, user_id) == (3, 3, 0)
    assert login_id_available(stack, login_id) is False


# ── 소셜 첫 가입 ───────────────────────────────────────────────


def assert_gated(client: httpx.Client) -> None:
    """만 14세 확인 전과 같은 범위: 프로필 조회는 되고, 그 밖의 API는 403."""
    assert client.get("/api/users/me").status_code == 200
    for method, path, kwargs in (
        ("GET", "/api/clothes", {}),
        ("POST", "/api/outfits/recommend/situation", {"json": SITUATION_REQUEST}),
        ("POST", "/api/users/me/consents/vton", {}),
    ):
        res = client.request(method, path, **kwargs)
        assert res.status_code == 403, f"{method} {path}: {res.status_code} {res.text}"


@pytest.mark.fr("FR-05")
def test_social_account_before_agreement_is_limited_like_before_age_confirmation(stack) -> None:
    u = make_unconfirmed_social_user(stack)
    try:
        assert_gated(u.client)
    finally:
        u.client.close()


@pytest.mark.fr("FR-05")
@pytest.mark.parametrize("missing", ["termsAgreed", "privacyAgreed"])
def test_social_age_confirmation_without_terms_keeps_account_limited(stack, missing: str) -> None:
    u = make_unconfirmed_social_user(stack)
    try:
        body = {k: v for k, v in ALL_AGREED.items() if k != missing}
        res = u.client.post("/api/users/me/age-confirmation", json=body)
        assert res.status_code < 500, res.text
        assert_gated(u.client)
    finally:
        u.client.close()


@pytest.mark.fr("FR-05")
def test_social_age_confirmation_with_all_agreements_opens_api_and_records(stack) -> None:
    u = make_unconfirmed_social_user(stack)
    try:
        res = u.client.post("/api/users/me/age-confirmation", json=ALL_AGREED)
        assert res.status_code == 200, res.text
        assert res.json()["data"]["ageConfirmedAt"] is not None

        assert u.client.get("/api/clothes").status_code == 200
        assert consent_summary(stack, u.id) == (3, 3, 0)
    finally:
        u.client.close()


@pytest.mark.fr("FR-05")
def test_existing_age_confirmed_user_keeps_access(user) -> None:
    # 이 기능 이전에 만 14세 확인을 마친 사용자(하네스의 make_user)는 계속 쓸 수 있어야 한다 (기존 API 동작 유지)
    assert user.client.get("/api/clothes").status_code == 200
