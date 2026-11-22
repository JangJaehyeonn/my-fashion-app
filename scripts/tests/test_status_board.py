from status_board import running_lines


def run(name: str, status: str, started: str, title: str = "t") -> dict:
    return {
        "name": name,
        "status": status,
        "run_started_at": started,
        "display_title": title,
        "html_url": f"https://example.test/{name}",
    }


def test_running_lines_lists_pipeline_runs_oldest_first() -> None:
    lines = running_lines(
        [
            run("AI review", "in_progress", "2026-11-22T10:10:00Z", "PR 40"),
            run("Claude", "in_progress", "2026-11-22T10:00:00Z", "FR-07"),
            run("CI", "queued", "2026-11-22T10:20:00Z"),
        ]
    )
    assert lines[0].startswith("Opus 구현 · 실행 중 · 11/22 19:00 시작")
    assert "Sonnet 리뷰" in lines[1]
    assert lines[2].startswith("CI 검사 · 대기 중")


def test_running_lines_skips_unknown_and_finished_runs() -> None:
    lines = running_lines(
        [
            run("Status board", "in_progress", "2026-11-22T10:00:00Z"),
            run("CI", "completed", "2026-11-22T10:00:00Z"),
        ]
    )
    assert lines == []
