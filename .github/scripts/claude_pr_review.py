"""PR 변경 파일의 diff를 추출해 Claude API로 코드 리뷰를 받고 PR에 코멘트로 등록한다."""

import os
import subprocess
import sys

import anthropic
import requests

MODEL = "claude-opus-5"
MAX_DIFF_CHARS = 60000
COMMENT_MARKER = "<!-- claude-pr-review -->"

EXCLUDED_PATTERNS = (
    ".lock",
    "package-lock.json",
    "gradle-wrapper.jar",
    "gradlew",
    "gradlew.bat",
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".webp",
    ".ico",
    ".svg",
    ".pdf",
    ".jar",
    ".keystore",
    ".jks",
)


def run_git_diff(base_sha: str, head_sha: str) -> str:
    changed_files = subprocess.run(
        ["git", "diff", "--name-only", f"{base_sha}...{head_sha}"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.splitlines()

    included_files = [
        f for f in changed_files
        if f and not any(f.endswith(pat) or pat in f for pat in EXCLUDED_PATTERNS)
    ]

    if not included_files:
        return ""

    diff = subprocess.run(
        ["git", "diff", f"{base_sha}...{head_sha}", "--", *included_files],
        capture_output=True,
        text=True,
        check=True,
    ).stdout

    return diff


def build_review_prompt(diff: str) -> str:
    truncated_note = ""
    if len(diff) > MAX_DIFF_CHARS:
        diff = diff[:MAX_DIFF_CHARS]
        truncated_note = (
            "\n\n(주의: diff가 너무 커서 일부만 잘라서 전달됨 — "
            "잘린 이후 내용은 리뷰에서 다루지 못했을 수 있음)"
        )

    return (
        "다음은 Pull Request의 변경 사항(diff)입니다. 시니어 백엔드/안드로이드 개발자 "
        "관점에서 코드 리뷰를 해주세요.\n\n"
        "리뷰 시 다음을 확인해주세요:\n"
        "- 버그 가능성, 엣지 케이스 누락\n"
        "- 보안 취약점 (SQL 인젝션, 하드코딩된 시크릿, 인증/인가 누락 등)\n"
        "- 명백한 성능 문제\n"
        "- 코드 스타일/가독성 개선점 (사소한 것은 생략)\n\n"
        "문제가 없으면 억지로 지적하지 말고 '특별한 이슈 없음'이라고 답해주세요. "
        "발견한 이슈는 파일명과 대략적인 위치를 언급하며 마크다운 목록으로 간결하게 "
        "정리해주세요. 전체 응답은 한국어로 작성해주세요.\n\n"
        f"```diff\n{diff}\n```{truncated_note}"
    )


def request_review(diff: str) -> str:
    client = anthropic.Anthropic()
    response = client.messages.create(
        model=MODEL,
        max_tokens=4096,
        output_config={"effort": "medium"},
        messages=[{"role": "user", "content": build_review_prompt(diff)}],
    )

    if response.stop_reason == "refusal":
        return "Claude가 이 diff에 대한 리뷰 생성을 거부했습니다 (안전 정책)."

    text_blocks = [block.text for block in response.content if block.type == "text"]
    return "\n".join(text_blocks).strip() or "리뷰 응답이 비어 있습니다."


def find_existing_comment(repo: str, pr_number: str, headers: dict) -> int | None:
    url = f"https://api.github.com/repos/{repo}/issues/{pr_number}/comments"
    resp = requests.get(url, headers=headers, timeout=30)
    resp.raise_for_status()
    for comment in resp.json():
        if COMMENT_MARKER in comment.get("body", ""):
            return comment["id"]
    return None


def post_or_update_comment(repo: str, pr_number: str, token: str, body: str) -> None:
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
    }
    full_body = f"{COMMENT_MARKER}\n## 🤖 Claude 코드 리뷰\n\n{body}"

    existing_id = find_existing_comment(repo, pr_number, headers)
    if existing_id:
        url = f"https://api.github.com/repos/{repo}/issues/comments/{existing_id}"
        resp = requests.patch(url, headers=headers, json={"body": full_body}, timeout=30)
    else:
        url = f"https://api.github.com/repos/{repo}/issues/{pr_number}/comments"
        resp = requests.post(url, headers=headers, json={"body": full_body}, timeout=30)
    resp.raise_for_status()


def main() -> None:
    base_sha = os.environ["BASE_SHA"]
    head_sha = os.environ["HEAD_SHA"]
    repo = os.environ["REPO"]
    pr_number = os.environ["PR_NUMBER"]
    github_token = os.environ["GITHUB_TOKEN"]

    diff = run_git_diff(base_sha, head_sha)
    if not diff.strip():
        print("리뷰 대상 변경 사항 없음 — 스킵")
        return

    review = request_review(diff)
    post_or_update_comment(repo, pr_number, github_token, review)
    print("PR 코멘트 등록 완료")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print(f"리뷰 중 오류 발생: {exc}", file=sys.stderr)
        sys.exit(1)
