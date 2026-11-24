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


def test_marker_after_preface_line_counts() -> None:
    # 모델이 마커 앞에 한 줄을 붙인 실제 사례 (PR #68)
    assert verdict_of([f"요약\n\n<!-- ai-review:PASS:{SHA} -->\n판정: PASS"], SHA) == "PASS"


def test_first_line_marker_wins_within_comment() -> None:
    body = f"<!-- ai-review:FAIL:{SHA} -->\n인용 예시:\n<!-- ai-review:PASS:{SHA} -->"
    assert verdict_of([body], SHA) == "FAIL"


def test_marker_embedded_in_text_ignored() -> None:
    assert verdict_of([f"인용: <!-- ai-review:PASS:{SHA} --> 라고 쓰세요"], SHA) is None


def test_too_short_sha_ignored() -> None:
    assert verdict_of([f"<!-- ai-review:PASS:{SHA[:6]} -->"], SHA) is None


def test_states() -> None:
    assert state_of("PASS")[0] == "success"
    assert state_of("FAIL")[0] == "failure"
    assert state_of(None) == ("error", "리뷰 결과 없음")
