"""커밋 전 품질 검사를 한 번에 돌린다 (백엔드, AI 서버, 앱, 인수 테스트).

사용: 저장소 루트에서 `python3 scripts/check.py` (Windows는 `python scripts/check.py`)

인수 테스트 범위(WEARON_ACTIVE_FRS)가 비어 있으면 브랜치 이름 `claude/<이슈번호>-...`에서
이슈의 요구사항 ID를 찾아 '완료된 요구사항 + 이 이슈의 요구사항'으로 채운다.
Android SDK가 없으면(ANDROID_HOME·ANDROID_SDK_ROOT·android/local.properties 모두 없음) 앱 검사는 경고만 하고 건너뛴다.
CI에는 SDK가 있으므로 여기서 건너뛴 검사는 PR에서 다시 걸린다.
Windows·macOS·Linux 모두에서 동작하도록 셸 스크립트 대신 Python으로 작성했다.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

from _gh import closed_frs, fr_of, gh_json, label_names

ROOT = Path(__file__).resolve().parent.parent
WINDOWS = os.name == "nt"


def gradlew(project: str) -> list[str]:
    script = ROOT / project / ("gradlew.bat" if WINDOWS else "gradlew")
    return [str(script)] if WINDOWS else ["sh", str(script)]


def android_sdk_available() -> bool:
    if os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"):
        return True
    local = ROOT / "android" / "local.properties"
    return local.exists() and "sdk.dir" in local.read_text(encoding="utf-8")


def checks() -> list[tuple[str, list[str]]]:
    items: list[tuple[str, list[str]]] = [
        ("scripts pytest", [sys.executable, "-m", "pytest", "-q", "scripts/tests"]),
        ("backend test",[*gradlew("backend"), "-p", "backend", "test", "--no-daemon"]),
    ]
    if (ROOT / "ai-server" / "tests").is_dir():
        items.append(("ai-server pytest", [sys.executable, "-m", "pytest", "-q", "ai-server/tests"]))
    if android_sdk_available():
        items.append(
            (
                "android build+unit test",
                [
                    *gradlew("android"),
                    "-p",
                    "android",
                    "assembleDebug",
                    "testDebugUnitTest",
                    "--no-daemon",
                ],
            )
        )
    else:
        print("경고: Android SDK가 없어 앱 검사를 건너뜁니다 (CI에서는 실행됩니다).", file=sys.stderr)
    items.append(("acceptance", [sys.executable, "-m", "pytest", "-q", "acceptance"]))
    return items


def infer_active_frs() -> str | None:
    """현재 브랜치의 이슈에서 인수 테스트 범위를 추론한다. 알 수 없으면 None."""
    branch = subprocess.run(
        ["git", "rev-parse", "--abbrev-ref", "HEAD"],
        capture_output=True,
        text=True,
        check=False,
        cwd=ROOT,
    ).stdout.strip()
    match = re.match(r"^claude/(\d+)-", branch)
    if not match:
        return None
    try:
        issue = gh_json("issue", "view", match.group(1), "--json", "labels")
        fr = fr_of(label_names(issue))
        done = closed_frs()
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        print(f"경고: 인수 테스트 범위를 계산하지 못했습니다 ({exc}).", file=sys.stderr)
        return None
    if fr:
        done.add(fr)
    return ",".join(sorted(done))


def main() -> int:
    env = dict(os.environ)
    # Windows defaults to cp949; force UTF-8 for tools and captured output.
    env["PYTHONUTF8"] = "1"
    if not env.get("WEARON_ACTIVE_FRS"):
        inferred = infer_active_frs()
        if inferred is not None:
            env["WEARON_ACTIVE_FRS"] = inferred
    print(f"인수 테스트 범위: {env.get('WEARON_ACTIVE_FRS') or '스모크만'}", file=sys.stderr)

    failed: list[str] = []
    for name, cmd in checks():
        print(f"[{name}] 실행: {' '.join(cmd)}", file=sys.stderr)
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
            env=env,
            cwd=ROOT,
        )
        if result.returncode != 0:
            failed.append(name)
            tail = (result.stdout + result.stderr).strip().splitlines()[-40:]
            print(f"[{name}] 실패\n" + "\n".join(tail), file=sys.stderr)
        else:
            print(f"[{name}] 통과", file=sys.stderr)
    if failed:
        print(
            f"\n품질 검사 실패: {', '.join(failed)}. 테스트가 아니라 구현을 고친 뒤 다시 실행하세요.",
            file=sys.stderr,
        )
        return 1
    print("\n모든 검사 통과", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
