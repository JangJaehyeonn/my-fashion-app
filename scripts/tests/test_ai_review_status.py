from ai_review_status import state_of, verdict_of

SHA = "0123456789abcdef0123456789abcdef01234567"


def test_pass_marker_for_same_sha() -> None:
    assert verdict_of([f"<!-- ai-review:PASS:{SHA} -->\n요약"], SHA) == "PASS"


def test_short_sha_marker_accepted() -> None:
    assert verdict_of([f"<!-- ai-review:FAIL:{SHA[:7]} -->"], SHA) == "FAIL"


def test_marker_for_other_sha_ignored() -> None:
    other = "f" * 40
    assert verdict_of([f"<!-- ai-review:PASS:{other} -->"], SHA) is None


def test_last_marker_wins() -> None:
    bodies = [f"<!-- ai-review:FAIL:{SHA} -->", f"<!-- ai-review:PASS:{SHA} -->"]
    assert verdict_of(bodies, SHA) == "PASS"


def test_marker_not_on_first_line_ignored() -> None:
    assert verdict_of([f"요약\n<!-- ai-review:PASS:{SHA} -->"], SHA) is None


def test_marker_embedded_in_text_ignored() -> None:
    assert verdict_of([f"인용: <!-- ai-review:PASS:{SHA} --> 라고 쓰세요"], SHA) is None


def test_too_short_sha_ignored() -> None:
    assert verdict_of([f"<!-- ai-review:PASS:{SHA[:6]} -->"], SHA) is None


def test_states() -> None:
    assert state_of("PASS")[0] == "success"
    assert state_of("FAIL")[0] == "failure"
    assert state_of(None) == ("error", "리뷰 결과 없음")
