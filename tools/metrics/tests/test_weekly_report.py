"""weekly_report.py 단위 테스트 (DB 없이 계산·출력만 본다). 실행: python3 -m pytest -q tools/metrics/tests"""

from __future__ import annotations

import sys
from datetime import date, datetime, timedelta
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import weekly_report as wr  # noqa: E402

WEEK = date(2026, 11, 16)  # Monday
NOW = datetime(2026, 11, 30, 12, 0)


def ev(user: str, type_: str, at: datetime, **props: object) -> wr.Event:
    return wr.Event(user, type_, dict(props), at)


def day(n: int, hour: int = 10) -> datetime:
    return datetime.combine(WEEK, datetime.min.time()) + timedelta(days=n, hours=hour)


def sample() -> list[wr.Event]:
    events = [ev("a", "SIGNUP", day(0)), ev("b", "SIGNUP", day(2)), ev("old", "SIGNUP", day(-3))]
    events += [ev("a", "CLOTHES_ADDED", day(1), category="TOP") for _ in range(5)]
    events += [ev("b", "CLOTHES_ADDED", day(2), category="TOP") for _ in range(4)]
    events.append(ev("b", "CLOTHES_ADDED", day(10), category="TOP"))  # 8 days after signup: too late
    events += [
        ev("a", "PURCHASE_CHECKED", day(3), verdict="BUY", plus=6),
        ev("a", "PURCHASE_DECIDED", day(3), decision="BUY", verdict="BUY"),
        ev("a", "PURCHASE_CHECKED", day(4), verdict="THINK", plus=2),
        ev("a", "PURCHASE_DECIDED", day(4), decision="SKIP", verdict="THINK"),
        ev("old", "PURCHASE_CHECKED", day(5), verdict="SIMILAR", plus=0),
        ev("a", "WEAR_LOGGED", day(5), pieces=3, inserted=3),
        ev("a", "APP_OPENED", day(7)),  # next Monday: 7 days after signup day, still counts as revisit
    ]
    return events


def test_metrics_from_sample() -> None:
    m = wr.compute(sample(), WEEK, NOW)

    assert m.signups == 2
    assert (m.closet_goal.part, m.closet_goal.whole) == (1, 2)
    assert (m.revisit.part, m.revisit.whole) == (1, 2)
    assert (m.wear_users.part, m.wear_users.whole) == (1, 3)
    assert m.checks == 3
    rows = {r.verdict: r for r in m.verdict_rows}
    assert rows["BUY"].checks == 1 and str(rows["BUY"].buy) == "1/1 (100.0%)"
    assert rows["THINK"].decided == 1 and str(rows["THINK"].skip) == "1/1 (100.0%)"
    assert rows["SIMILAR"].decided == 0 and str(rows["SIMILAR"].buy) == "0/0 (-)"
    assert m.pending_cohort == 0


def test_same_day_activity_is_not_a_revisit() -> None:
    events = [ev("a", "SIGNUP", day(0, 9)), ev("a", "APP_OPENED", day(0, 22))]
    assert wr.compute(events, WEEK, NOW).revisit.part == 0


def test_render_has_title_tables_and_metric_labels() -> None:
    report = wr.render(wr.compute(sample(), WEEK, NOW), NOW)

    assert report.startswith("# ")
    assert "| --- | --- |" in report
    for label in ("가입", "5벌", "살까 말까", "BUY", "THINK", "SIMILAR", "SKIP", "착용 기록", "재방문"):
        assert label in report
    assert "2026-11-16 ~ 2026-11-22" in report


def test_empty_week_still_renders() -> None:
    report = wr.render(wr.compute([], WEEK, NOW), NOW)
    assert "주간 가입 수 | 0" in report
    assert "0/0 (-)" in report


def test_pending_cohort_note_for_running_week() -> None:
    m = wr.compute(sample(), WEEK, day(3))
    assert m.pending_cohort == 2
    assert "아직 7일이 지나지 않은 가입자 2명" in wr.render(m, day(3))


def test_parse_events_reads_psql_csv() -> None:
    text = 'u1,PURCHASE_CHECKED,"{""plus"": 3, ""verdict"": ""BUY""}",2026-11-17T09:30:00\nbad,row\n'
    events = wr.parse_events(text)
    assert events == [wr.Event("u1", "PURCHASE_CHECKED", {"plus": 3, "verdict": "BUY"}, datetime(2026, 11, 17, 9, 30))]


def test_pg_env_splits_url_without_command_line_password() -> None:
    env = wr.pg_env("postgresql://fashionapp:p%40ss@127.0.0.1:15432/fashionapp")
    assert env == {"PGHOST": "127.0.0.1", "PGPORT": "15432", "PGDATABASE": "fashionapp",
                   "PGUSER": "fashionapp", "PGPASSWORD": "p@ss"}
    with pytest.raises(ValueError):
        wr.pg_env("mysql://x@y/z")


def test_week_start_is_monday() -> None:
    assert wr.week_start_of(date(2026, 11, 22)) == WEEK
    assert wr.week_start_of(WEEK) == WEEK


def test_query_uses_korean_week_bounds() -> None:
    sql = wr.events_query(WEEK, date(2026, 12, 1))
    assert "'2026-11-16 00:00:00+09'" in sql and "'2026-12-01 00:00:00+09'" in sql


def test_main_without_database_url_fails(monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]) -> None:
    monkeypatch.delenv("DATABASE_URL", raising=False)
    assert wr.main([]) == 2
    assert "DATABASE_URL" in capsys.readouterr().err
