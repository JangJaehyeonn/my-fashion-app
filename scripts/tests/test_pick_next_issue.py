from pick_next_issue import choose


def issue(number: int, labels: list[str], depends: str = "없음") -> dict:
    return {
        "number": number,
        "labels": [{"name": n} for n in labels],
        "body": f"## 의존\nDepends on: {depends}\n",
    }


def test_lowest_phase_then_number() -> None:
    issues = [issue(5, ["FR-07", "phase-2"]), issue(9, ["FR-01", "phase-1"])]
    assert choose(issues, set()) == (9, "FR-01")


def test_blocked_and_unmet_dependencies_skipped() -> None:
    issues = [
        issue(1, ["FR-01", "phase-1", "needs-human"]),
        issue(2, ["FR-02", "phase-1"], "FR-01"),
        issue(3, ["FR-03", "phase-1", "in-progress"]),
    ]
    assert choose(issues, set()) is None
    assert choose(issues, {"FR-01"}) == (2, "FR-02")


def test_human_verify_issue_is_still_implemented() -> None:
    assert choose([issue(4, ["FR-16", "phase-3", "human-verify"])], set()) == (4, "FR-16")
