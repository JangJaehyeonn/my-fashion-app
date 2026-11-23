"""AI 리뷰(ai-review 잡) 결과를 커밋 상태로 옮긴다.

리뷰 잡은 PR에 요약 코멘트를 남기고, 한 줄 전체로 숨은 마커를 쓴다 (보통 첫 줄).
  <!-- ai-review:PASS:<head sha> -->  또는  <!-- ai-review:FAIL:<head sha> -->
이 스크립트는 그 PR의 코멘트에서 **같은 head SHA**의 마지막 마커를 찾아 커밋 상태 `ai-review`를 정한다.

- PASS → success
- FAIL → failure, 종료 코드 1 (워크플로가 실패해야 review-fix가 돈다)
- 마커 없음 → error("리뷰 결과 없음"), needs-human 라벨과 알림, 종료 코드 0 (자동 수정을 돌리지 않는다)

사용: python3 scripts/ai_review_status.py <PR 번호> <head sha> [실행 URL]
환경 변수: GITHUB_REPOSITORY, GH_TOKEN
"""

from __future__ import annotations

import json
import os
import re
import sys

from _gh import ensure_label, gh

CONTEXT = "ai-review"
OWNER = "@JangJaehyeonn"
MARKER = re.compile(r"<!--\s*ai-review:(PASS|FAIL):([0-9a-f]{7,40})\s*-->")


def verdict_of(bodies: list[str], sha: str) -> str | None:
    """코멘트 본문 목록(오래된 순)에서 이 SHA의 마지막 판정을 돌려준다.

    코멘트마다 **한 줄 전체가 마커인 첫 줄**만 본다. 모델이 마커 앞에 문장을 붙여도 판정은 살리고,
    문장 속에 인용된 마커는 무시한다.
    """
    found: str | None = None
    for body in bodies:
        match = first_marker(body or "")
        if match and sha.startswith(match.group(2)) and len(match.group(2)) >= 7:
            found = match.group(1)
    return found


def first_marker(body: str) -> re.Match[str] | None:
    for line in body.splitlines():
        match = MARKER.fullmatch(line.strip())
        if match:
            return match
    return None


def state_of(verdict: str | None) -> tuple[str, str]:
    if verdict == "PASS":
        return "success", "AI 리뷰 통과"
    if verdict == "FAIL":
        return "failure", "AI 리뷰 FAIL: 지적 사항 반영 필요"
    return "error", "리뷰 결과 없음"


def bot_bodies(repo: str, pr: str) -> list[str]:
    """github-actions·claude 봇이 쓴 코멘트 본문만 모은다 (사람이 마커를 흉내내도 무시)."""
    raw = gh(
        "api",
        "--paginate",
        f"repos/{repo}/issues/{pr}/comments?per_page=100",
        "--jq",
        '.[] | select(.user.type == "Bot") | .body | @json',
    )
    return [json.loads(line) for line in raw.splitlines() if line.strip()]


def set_status(repo: str, sha: str, state: str, description: str, url: str) -> None:
    args = [
        "api",
        "-X",
        "POST",
        f"repos/{repo}/statuses/{sha}",
        "-f",
        f"state={state}",
        "-f",
        f"context={CONTEXT}",
        "-f",
        f"description={description}",
    ]
    if url:
        args += ["-f", f"target_url={url}"]
    gh(*args)


def main() -> int:
    pr, sha = sys.argv[1], sys.argv[2]
    url = sys.argv[3] if len(sys.argv) > 3 else ""
    repo = os.environ["GITHUB_REPOSITORY"]
    verdict = verdict_of(bot_bodies(repo, pr), sha)
    state, description = state_of(verdict)
    set_status(repo, sha, state, description, url)
    print(f"ai-review: {state} ({description})")
    if state == "error":
        ensure_label("needs-human", "b60205", "에이전트가 해결하지 못해 사람 확인이 필요함")
        gh("pr", "edit", pr, "--add-label", "needs-human")
        gh(
            "pr",
            "comment",
            pr,
            "--body",
            f"{OWNER} AI 리뷰가 결과 마커 없이 끝났습니다 (커밋 {sha[:7]}). "
            f"자동 수정은 돌지 않습니다. 실행 로그: {url or '(없음)'}",
        )
    return 1 if state == "failure" else 0


if __name__ == "__main__":
    sys.exit(main())
