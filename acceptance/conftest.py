"""인수 테스트 공통 설정.

- 모든 테스트는 `@pytest.mark.fr("FR-xx")` 또는 `@pytest.mark.smoke`가 있어야 한다 (없으면 수집 실패).
- `WEARON_ACTIVE_FRS`(쉼표 구분)에 든 요구사항의 테스트만 실행한다. 스모크 테스트는 항상 실행한다.
- 실행할 테스트가 있으면 `acceptance/compose.yml` 스택(postgres+pgvector, redis, 백엔드, 가짜 AI)을 띄우고 끝나면 내린다.
  이미 떠 있는 스택을 쓰려면 `WEARON_ACCEPTANCE_URL`(백엔드)과 `WEARON_FAKE_AI_URL`을 지정한다 (그때는 띄우지도 내리지도 않는다).
- 테스트 사용자는 DB에 직접 만들고, 스택과 같은 테스트 전용 키로 JWT를 서명한다 (로그인 방식과 무관하게 동작).
"""

from __future__ import annotations

import base64
import os
import subprocess
import time
import uuid
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

import httpx
import jwt
import pytest

HERE = Path(__file__).resolve().parent
COMPOSE = ["docker", "compose", "-f", str(HERE / "compose.yml")]
JWT_SECRET_B64 = "d2Vhcm9uLWFjY2VwdGFuY2UtdGVzdC1vbmx5LXNlY3JldC1rZXktMzJieXRlcyE="
STARTUP_TIMEOUT_S = 240


def pytest_configure(config: pytest.Config) -> None:
    config.addinivalue_line("markers", "fr(id): 이 테스트가 검증하는 요구사항 ID (FR-xx)")
    config.addinivalue_line("markers", "smoke: 하네스 자체 점검 (항상 실행)")


def active_frs() -> set[str]:
    raw = os.environ.get("WEARON_ACTIVE_FRS", "")
    return {fr.strip().upper() for fr in raw.split(",") if fr.strip()}


def pytest_collection_modifyitems(config: pytest.Config, items: list[pytest.Item]) -> None:
    active = active_frs()
    selected: list[pytest.Item] = []
    deselected: list[pytest.Item] = []
    for item in items:
        fr_marks = list(item.iter_markers("fr"))
        is_smoke = item.get_closest_marker("smoke") is not None
        if not fr_marks and not is_smoke:
            raise pytest.UsageError(f"{item.nodeid}: @pytest.mark.fr(\"FR-xx\") 표시가 없습니다.")
        frs = {str(m.args[0]).upper() for m in fr_marks}
        if is_smoke or frs & active:
            selected.append(item)
        else:
            deselected.append(item)
    if deselected:
        config.hook.pytest_deselected(items=deselected)
    items[:] = selected


@dataclass
class Stack:
    api: str
    fake_ai: str

    def sql(self, statement: str) -> str:
        """스택의 postgres에서 SQL을 실행하고 결과(값만)를 돌려준다."""
        result = subprocess.run(
            [*COMPOSE, "exec", "-T", "postgres", "psql", "-U", "fashionapp", "-d", "fashionapp",
             "-tA", "-v", "ON_ERROR_STOP=1", "-c", statement],
            capture_output=True, text=True, check=True,
        )
        return result.stdout.strip()


def _wait_ready(api: str, fake_ai: str) -> None:
    deadline = time.monotonic() + STARTUP_TIMEOUT_S
    last = ""
    while time.monotonic() < deadline:
        try:
            ai_ok = httpx.get(f"{fake_ai}/health", timeout=3).status_code == 200
            # 인증 없는 보호 API는 401이면 백엔드가 떠 있는 것이다.
            api_code = httpx.get(f"{api}/api/users/me", timeout=3).status_code
            if ai_ok and api_code == 401:
                return
            last = f"fake-ai={ai_ok} backend={api_code}"
        except httpx.HTTPError as exc:
            last = repr(exc)
        time.sleep(2)
    raise RuntimeError(f"인수 테스트 스택이 {STARTUP_TIMEOUT_S}초 안에 준비되지 않았습니다: {last}")


@pytest.fixture(scope="session")
def stack() -> Iterator[Stack]:
    external = os.environ.get("WEARON_ACCEPTANCE_URL")
    if external:
        s = Stack(external.rstrip("/"), os.environ.get("WEARON_FAKE_AI_URL", "http://127.0.0.1:18000"))
        _wait_ready(s.api, s.fake_ai)
        yield s
        return
    subprocess.run([*COMPOSE, "up", "-d", "--build"], check=True)
    try:
        s = Stack("http://127.0.0.1:18080", "http://127.0.0.1:18000")
        try:
            _wait_ready(s.api, s.fake_ai)
        except RuntimeError:
            subprocess.run([*COMPOSE, "logs", "--tail", "200", "backend"], check=False)
            raise
        yield s
    finally:
        if not os.environ.get("WEARON_ACCEPTANCE_KEEP"):
            subprocess.run([*COMPOSE, "down", "-v"], check=False)


@pytest.fixture(autouse=True)
def _reset_fake_ai(request: pytest.FixtureRequest) -> None:
    if "stack" in request.fixturenames:
        s: Stack = request.getfixturevalue("stack")
        httpx.post(f"{s.fake_ai}/__control/reset", timeout=5).raise_for_status()


def token_for(user_id: str) -> str:
    now = int(time.time())
    key = base64.b64decode(JWT_SECRET_B64)
    return jwt.encode({"sub": user_id, "iat": now, "exp": now + 3600}, key, algorithm="HS256")


@dataclass
class User:
    id: str
    nickname: str
    client: httpx.Client


def make_user(stack: Stack, nickname: str = "인수테스트") -> User:
    """DB에 테스트 사용자를 만들고 그 사용자로 인증된 HTTP 클라이언트를 돌려준다."""
    user_id = str(uuid.uuid4())
    safe = nickname.replace("'", "''")
    stack.sql(
        "INSERT INTO users (id, nickname, provider, provider_id, created_at, updated_at) "
        f"VALUES ('{user_id}', '{safe}', 'google', 'acceptance-{user_id}', now(), now())"
    )
    client = httpx.Client(
        base_url=stack.api,
        headers={"Authorization": f"Bearer {token_for(user_id)}"},
        timeout=30,
    )
    return User(user_id, nickname, client)


@pytest.fixture
def user(stack: Stack) -> Iterator[User]:
    u = make_user(stack)
    yield u
    u.client.close()


@pytest.fixture
def fake_ai(stack: Stack) -> Iterator[httpx.Client]:
    with httpx.Client(base_url=stack.fake_ai, timeout=10) as client:
        yield client


@pytest.fixture
def tiny_jpg() -> bytes:
    """업로드용 작은 JPEG 바이트 (내용은 가짜 AI가 보지 않는다. 파일 이름이 분류를 정한다)."""
    return (HERE / "fixtures" / "tiny.jpg").read_bytes()
