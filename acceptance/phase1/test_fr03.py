"""FR-03 비밀값 기본값 제거.

- 설정 파일 검사: backend/src/main/resources/application*.yml의 `${X:값}`에서 X가 비밀값 이름이면 기본값이 비어 있어야 한다.
  JWT_SECRET은 기본값 자리 자체가 없어야 한다 (`${JWT_SECRET}`), 빈 기본값이면 '빠진 변수'로 기동이 멈추지 않기 때문.
- 기동 실패 검사: 인수 테스트 스택의 백엔드 이미지를 JWT_SECRET 없이 따로 띄워 종료 코드와 로그를 본다 (Docker 필요).
"""

from __future__ import annotations

import re
import subprocess
import uuid
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "backend" / "src" / "main" / "resources"
COMPOSE_FILE = ROOT / "acceptance" / "compose.yml"
PROJECT = "wearon-acceptance"
BACKEND_IMAGE = f"{PROJECT}-backend"
NETWORK = f"{PROJECT}_default"
STARTUP_FAIL_TIMEOUT_S = 180

PLACEHOLDER = re.compile(r"\$\{([A-Za-z0-9_.\-]+)(:([^}]*))?\}")
SECRET_NAME = re.compile(r"(SECRET|PASSWORD|TOKEN|API_KEY|ACCESS_KEY|PRIVATE_KEY)", re.IGNORECASE)

# acceptance/compose.yml의 백엔드 환경 변수에서 JWT_SECRET만 뺀 값 (모두 테스트 전용 가짜 값)
BASE_ENV = {
    "DB_URL": "jdbc:postgresql://postgres:5432/fashionapp",
    "DB_USERNAME": "fashionapp",
    "DB_PASSWORD": "fashionapp",
    "REDIS_HOST": "redis",
    "REDIS_PORT": "6379",
    "AI_SERVER_URL": "http://fake-ai:8000",
    "GOOGLE_CLIENT_ID": "acceptance",
    "GOOGLE_CLIENT_SECRET": "acceptance",
    "KAKAO_CLIENT_ID": "acceptance",
    "KAKAO_CLIENT_SECRET": "acceptance",
    "AWS_ACCESS_KEY": "acceptance",
    "AWS_SECRET_KEY": "acceptance",
    "AWS_REGION": "ap-northeast-2",
    "S3_BUCKET": "acceptance",
    "STORAGE_TYPE": "local",
    "STORAGE_LOCAL_ROOT": "/tmp/wearon-storage",
    "JAVA_TOOL_OPTIONS": "-Xmx512m",
}
# 운영 프로필 검사(ProdConfigValidator)는 통과하는 값 — 빠진 것이 JWT_SECRET뿐이게 한다
PROD_ENV = {
    "SPRING_PROFILES_ACTIVE": "prod",
    "PUBLIC_BASE_URL": "https://acceptance.example.com",
    "FRONTEND_URL": "https://acceptance.example.com",
    "CORS_ALLOWED_ORIGINS": "https://acceptance.example.com",
}


def yml_files() -> list[Path]:
    files = sorted(RESOURCES.glob("application*.yml")) + sorted(RESOURCES.glob("application*.yaml"))
    assert files, f"설정 파일이 없습니다: {RESOURCES}"
    return files


def placeholders(path: Path) -> list[tuple[int, str, str | None]]:
    found: list[tuple[int, str, str | None]] = []
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        code = line.split(" #", 1)[0]
        for m in PLACEHOLDER.finditer(code):
            found.append((lineno, m.group(1), m.group(3) if m.group(2) else None))
    return found


@pytest.mark.fr("FR-03")
def test_no_secret_defaults_in_application_yml() -> None:
    offenders = [
        f"{path.name}:{lineno} ${{{name}:{default}}}"
        for path in yml_files()
        for lineno, name, default in placeholders(path)
        if SECRET_NAME.search(name) and default is not None and default.strip()
    ]
    assert offenders == [], "비밀값에 기본값이 남아 있습니다: " + ", ".join(offenders)


@pytest.mark.fr("FR-03")
def test_jwt_secret_placeholder_has_no_default() -> None:
    jwt = [(p.name, lineno, default) for p in yml_files() for lineno, name, default in placeholders(p)
           if name == "JWT_SECRET"]
    assert jwt, "JWT_SECRET을 환경 변수로 받는 설정이 없습니다"
    assert all(default is None for _, _, default in jwt), jwt


def run_backend_without_jwt_secret(extra_env: dict[str, str]) -> subprocess.CompletedProcess[str]:
    subprocess.run(["docker", "compose", "-f", str(COMPOSE_FILE), "build", "backend"], check=True)
    name = f"wearon-fr03-{uuid.uuid4().hex[:8]}"
    env_args = [arg for k, v in {**BASE_ENV, **extra_env}.items() for arg in ("-e", f"{k}={v}")]
    cmd = ["docker", "run", "--name", name, "--network", NETWORK, *env_args, BACKEND_IMAGE]
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=STARTUP_FAIL_TIMEOUT_S)
    except subprocess.TimeoutExpired:
        pytest.fail(f"JWT_SECRET 없이도 {STARTUP_FAIL_TIMEOUT_S}초 동안 기동이 멈추지 않았습니다")
    finally:
        subprocess.run(["docker", "rm", "-f", name], capture_output=True, check=False)


@pytest.mark.fr("FR-03")
@pytest.mark.parametrize("profile_env", [{}, PROD_ENV], ids=["local", "prod"])
def test_startup_without_jwt_secret_fails_with_variable_name(stack, profile_env: dict[str, str]) -> None:
    # stack: 이미지 빌드와 postgres·redis 네트워크를 준비한다 (같은 DB에 붙어도 기동 실패만 본다)
    result = run_backend_without_jwt_secret(profile_env)
    output = result.stdout + result.stderr
    assert result.returncode != 0, output[-3000:]
    assert "JWT_SECRET" in output, output[-3000:]


@pytest.mark.fr("FR-03")
def test_harness_passes_test_secret_by_env_and_backend_runs(user) -> None:
    compose = COMPOSE_FILE.read_text(encoding="utf-8")
    assert re.search(r"^\s+JWT_SECRET:\s*\S+", compose, re.MULTILINE), "compose.yml이 JWT_SECRET을 넘기지 않습니다"

    res = user.client.get("/api/users/me")
    assert res.status_code == 200, res.text
    assert res.json()["data"]["id"] == user.id
