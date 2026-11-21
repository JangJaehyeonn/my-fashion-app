"""이슈 공급기: Claude가 다음에 작업할 이슈 하나를 고른다.

출력 (GITHUB_OUTPUT 형식):
  issue=<번호 또는 빈 값>
  fr=<요구사항 ID 또는 빈 값>

규칙
- Claude 브랜치(claude/*)의 PR이 열려 있으면 고르지 않는다 (한 번에 하나).
- `ready` 라벨이 있고, `needs-human`·`in-progress`가 없는 열린 이슈만 후보.
  `human-verify`(앱 화면) 이슈도 구현 대상이다. 확인만 사람이 한다.
- 본문의 `Depends on:` 요구사항이 모두 완료(닫힘)된 이슈만 후보.
- Phase가 낮은 순, 이슈 번호가 작은 순.
- 모델은 항상 Opus다 (워크플로에 고정). 이 스크립트는 모델을 고르지 않는다.
"""

from __future__ import annotations

import sys
from typing import Any

from _gh import closed_frs, depends_of, fr_of, gh_json, label_names, phase_of

BLOCKING = {"needs-human", "in-progress"}


def emit(issue: str = "", fr: str = "") -> None:
    print(f"issue={issue}")
    print(f"fr={fr}")


def choose(issues: list[dict[str, Any]], done: set[str]) -> tuple[int, str] | None:
    """후보 중 다음 이슈를 고른다. 없으면 None."""
    candidates: list[tuple[int, int, str]] = []
    for issue in issues:
        labels = label_names(issue)
        if labels & BLOCKING:
            continue
        fr = fr_of(labels)
        if not fr:
            continue
        if not set(depends_of(issue.get("body", ""))) <= done:
            continue
        candidates.append((phase_of(labels), int(issue["number"]), fr))
    if not candidates:
        return None
    _, number, fr = sorted(candidates)[0]
    return number, fr


def main() -> int:
    open_prs = gh_json("pr", "list", "--state", "open", "--json", "headRefName")
    if any(pr["headRefName"].startswith("claude/") for pr in open_prs):
        print("Claude PR이 아직 열려 있어 이번에는 건너뜁니다.", file=sys.stderr)
        emit()
        return 0

    issues = gh_json(
        "issue",
        "list",
        "--state",
        "open",
        "--label",
        "ready",
        "--limit",
        "200",
        "--json",
        "number,labels,body",
    )
    picked = choose(issues, closed_frs())
    if picked is None:
        print("작업할 수 있는 이슈가 없습니다.", file=sys.stderr)
        emit()
        return 0
    number, fr = picked
    print(f"다음 작업: #{number} ({fr})", file=sys.stderr)
    emit(str(number), fr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
