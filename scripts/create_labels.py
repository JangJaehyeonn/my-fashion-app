"""자동화에 필요한 GitHub 라벨을 한 번에 만든다 (이미 있으면 갱신).

사용: gh auth login 후 저장소 루트에서 `python3 scripts/create_labels.py`
"""

from __future__ import annotations

import sys
import tomllib
from pathlib import Path

from _gh import ensure_label

BASE_LABELS = [
    ("ready", "0e8a16", "Phase가 시작되어 Claude가 작업해도 되는 이슈"),
    ("claude", "5319e7", "이 라벨을 붙이면 Claude가 바로 구현을 시작함"),
    ("in-progress", "1d76db", "Claude가 작업 중"),
    ("needs-human", "b60205", "에이전트가 해결하지 못해 사람 확인이 필요함"),
    ("human-verify", "d4c5f9", "앱 화면: 인수 테스트 대신 사람이 에뮬레이터로 확인"),
    ("status-board", "0e8a16", "진행 현황판"),
    ("phase-1", "c5def5", "출시 준비"),
    ("phase-2", "c5def5", "서버 (살까 말까·오늘 뭐 입지)"),
    ("phase-3", "c5def5", "앱 화면"),
    ("phase-4", "c5def5", "정리·평가"),
    ("phase-5", "c5def5", "출시 후 (자동 시작 안 함)"),
    ("필수", "d93f0b", "출시 조건"),
    ("권장", "fbca04", "출시 범위 안에서 가능하면 구현"),
    ("선택", "cccccc", "출시 이후"),
]


def main() -> int:
    spec = tomllib.loads(Path("docs/requirements/issues.toml").read_text(encoding="utf-8"))
    labels = BASE_LABELS + [(req["id"], "bfd4f2", req["title"]) for req in spec["requirement"]]
    failed: list[str] = []
    for name, color, description in labels:
        if ensure_label(name, color, description[:100]):
            print(f"라벨 준비: {name}")
        else:
            failed.append(name)
    if failed:
        print(f"라벨 {len(failed)}개 생성 실패: {', '.join(failed)}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
