"""FR-14 주간 지표 리포트.

events 테이블을 읽어 Markdown 리포트를 표준 출력에 쓴다.

사용:
    DATABASE_URL=postgresql://user:pass@127.0.0.1:5432/fashionapp python3 tools/metrics/weekly_report.py
    DATABASE_URL=... python3 tools/metrics/weekly_report.py --week 2026-11-16   # 그 날짜가 든 주

- 주는 한국 시각 월요일 00:00부터 7일이다. 인자가 없으면 오늘이 든 주(진행 중인 주)다.
- DB 접속은 PostgreSQL 클라이언트 `psql`을 쓴다 (파이썬 DB 드라이버 의존성 없음, docs/adr/0013-event-log.md).
  접속 정보는 명령줄 인자가 아니라 PG* 환경 변수로 넘겨 비밀번호가 프로세스 목록에 보이지 않게 한다.
- 코호트 지표(옷 5벌, 7일 재방문)는 그 주에 가입한 사용자를 가입 시각부터 7일 동안 본다.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import os
import shutil
import subprocess
import sys
from collections import Counter
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from urllib.parse import unquote, urlparse

KST = timezone(timedelta(hours=9))
VERDICTS: tuple[str, ...] = ("BUY", "THINK", "SIMILAR")
DECISIONS: tuple[str, ...] = ("BUY", "SKIP")
CLOSET_GOAL = 5
COHORT_DAYS = 7


@dataclass(frozen=True)
class Event:
    user_id: str
    type: str
    props: dict[str, object]
    at: datetime  # Korean local time (naive)


@dataclass(frozen=True)
class Ratio:
    part: int
    whole: int

    def __str__(self) -> str:
        if self.whole == 0:
            return "0/0 (-)"
        return f"{self.part}/{self.whole} ({self.part * 100 / self.whole:.1f}%)"


@dataclass(frozen=True)
class VerdictRow:
    verdict: str
    checks: int
    share: Ratio
    decided: int
    buy: Ratio
    skip: Ratio


@dataclass(frozen=True)
class WeeklyMetrics:
    week_start: date
    signups: int
    closet_goal: Ratio
    revisit: Ratio
    pending_cohort: int
    wear_users: Ratio
    checks: int
    verdict_rows: list[VerdictRow]


def week_start_of(day: date) -> date:
    return day - timedelta(days=day.weekday())


def in_range(event: Event, start: datetime, end: datetime) -> bool:
    return start <= event.at < end


def first_signups(events: Iterable[Event], start: datetime, end: datetime) -> dict[str, datetime]:
    """User id → signup time, for users who signed up in [start, end)."""
    signups: dict[str, datetime] = {}
    for event in events:
        if event.type == "SIGNUP" and in_range(event, start, end):
            if event.user_id not in signups or event.at < signups[event.user_id]:
                signups[event.user_id] = event.at
    return signups


def reached_closet_goal(events: Sequence[Event], signups: dict[str, datetime]) -> int:
    """Users who added at least CLOSET_GOAL pieces within COHORT_DAYS after signing up."""
    added: Counter[str] = Counter()
    for event in events:
        joined = signups.get(event.user_id)
        if event.type == "CLOTHES_ADDED" and joined is not None and joined <= event.at < joined + timedelta(days=COHORT_DAYS):
            added[event.user_id] += 1
    return sum(1 for count in added.values() if count >= CLOSET_GOAL)


def revisited(events: Sequence[Event], signups: dict[str, datetime]) -> int:
    """Users with any event on a later Korean day within COHORT_DAYS days after the signup day."""
    users: set[str] = set()
    for event in events:
        joined = signups.get(event.user_id)
        if joined is None:
            continue
        days_after = (event.at.date() - joined.date()).days
        if 1 <= days_after <= COHORT_DAYS:
            users.add(event.user_id)
    return len(users)


def verdict_rows(week_events: Sequence[Event]) -> list[VerdictRow]:
    checks = Counter(str(e.props.get("verdict")) for e in week_events if e.type == "PURCHASE_CHECKED")
    decided: dict[str, Counter[str]] = {v: Counter() for v in VERDICTS}
    for event in week_events:
        verdict = str(event.props.get("verdict"))
        if event.type == "PURCHASE_DECIDED" and verdict in decided:
            decided[verdict][str(event.props.get("decision"))] += 1
    total = sum(checks[v] for v in VERDICTS)
    rows: list[VerdictRow] = []
    for verdict in VERDICTS:
        n_decided = sum(decided[verdict][d] for d in DECISIONS)
        rows.append(VerdictRow(verdict, checks[verdict], Ratio(checks[verdict], total), n_decided,
                               Ratio(decided[verdict]["BUY"], n_decided), Ratio(decided[verdict]["SKIP"], n_decided)))
    return rows


def compute(events: Sequence[Event], week_start: date, now: datetime) -> WeeklyMetrics:
    start = datetime.combine(week_start, datetime.min.time())
    end = start + timedelta(days=7)
    week_events = [e for e in events if in_range(e, start, end)]
    signups = first_signups(events, start, end)
    active = {e.user_id for e in week_events}
    wear = {e.user_id for e in week_events if e.type == "WEAR_LOGGED"}
    pending = sum(1 for joined in signups.values() if joined + timedelta(days=COHORT_DAYS) > now)
    rows = verdict_rows(week_events)
    return WeeklyMetrics(
        week_start=week_start,
        signups=len(signups),
        closet_goal=Ratio(reached_closet_goal(events, signups), len(signups)),
        revisit=Ratio(revisited(events, signups), len(signups)),
        pending_cohort=pending,
        wear_users=Ratio(len(wear), len(active)),
        checks=sum(r.checks for r in rows),
        verdict_rows=rows,
    )


def render(metrics: WeeklyMetrics, generated_at: datetime) -> str:
    week_end = metrics.week_start + timedelta(days=6)
    lines = [
        f"# Wearon 주간 지표 리포트 ({metrics.week_start.isoformat()} ~ {week_end.isoformat()})",
        "",
        f"- 기준: 한국 시각, 월요일 시작 주. 생성: {generated_at.strftime('%Y-%m-%d %H:%M')} (KST)",
        f"- 가입 코호트 지표(옷 5벌, 7일 재방문)는 이 주 가입자를 가입 후 {COHORT_DAYS}일 동안 본다.",
    ]
    if metrics.pending_cohort:
        lines.append(f"- 아직 {COHORT_DAYS}일이 지나지 않은 가입자 {metrics.pending_cohort}명이 포함되어 코호트 지표가 더 오를 수 있다.")
    lines += [
        "",
        "## 가입·활동",
        "",
        "| 지표 | 값 |",
        "| --- | --- |",
        f"| 주간 가입 수 | {metrics.signups} |",
        f"| 가입 후 7일 안에 옷 5벌 이상 등록한 비율 | {metrics.closet_goal} |",
        f"| 7일 재방문율 (가입 다음 날부터 7일 안에 다시 사용) | {metrics.revisit} |",
        f"| 착용 기록 사용자 비율 (이 주 활동 사용자 중) | {metrics.wear_users} |",
        "",
        "## 살까 말까",
        "",
        f"이 주 살까 말까 수: {metrics.checks}",
        "",
        "| 판정 | 살까 말까 수 | 판정 비율 | 결정 수 | BUY 비율 | SKIP 비율 |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in metrics.verdict_rows:
        lines.append(f"| {row.verdict} | {row.checks} | {row.share} | {row.decided} | {row.buy} | {row.skip} |")
    lines += ["", "BUY·SKIP 비율은 그 판정을 받고 이 주에 결정한 건 중의 비율이다.", ""]
    return "\n".join(lines)


def parse_events(csv_text: str) -> list[Event]:
    events: list[Event] = []
    for row in csv.reader(io.StringIO(csv_text)):
        if len(row) != 4:
            continue
        user_id, event_type, raw_props, at = row
        try:
            props = json.loads(raw_props) if raw_props else {}
        except json.JSONDecodeError:
            props = {}
        events.append(Event(user_id, event_type, props if isinstance(props, dict) else {}, datetime.fromisoformat(at)))
    return events


def pg_env(database_url: str) -> dict[str, str]:
    """postgresql://user:pass@host:port/db → PG* variables for psql."""
    url = urlparse(database_url)
    if url.scheme not in ("postgresql", "postgres"):
        raise ValueError("DATABASE_URL은 postgresql://로 시작해야 합니다.")
    env = {"PGHOST": url.hostname or "127.0.0.1", "PGPORT": str(url.port or 5432),
           "PGDATABASE": unquote(url.path.lstrip("/")) or "postgres"}
    if url.username:
        env["PGUSER"] = unquote(url.username)
    if url.password:
        env["PGPASSWORD"] = unquote(url.password)
    return env


def events_query(start: date, end: date) -> str:
    # Dates come from date objects (never user text), so formatting them into SQL is safe
    return ("SELECT user_id, type, coalesce(props::text, '{}'), "
            "to_char(created_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD\"T\"HH24:MI:SS') "
            "FROM events "
            f"WHERE created_at >= '{start.isoformat()} 00:00:00+09' AND created_at < '{end.isoformat()} 00:00:00+09' "
            "ORDER BY created_at, id")


def fetch_events(database_url: str, start: date, end: date) -> list[Event]:
    psql = shutil.which("psql")
    if psql is None:
        raise RuntimeError("psql을 찾을 수 없습니다. PostgreSQL 클라이언트를 설치하세요.")
    env = {**os.environ, **pg_env(database_url)}
    result = subprocess.run([psql, "-X", "-q", "--csv", "-t", "-v", "ON_ERROR_STOP=1", "-c", events_query(start, end)],
                            capture_output=True, text=True, env=env, check=False, timeout=120)
    if result.returncode != 0:
        raise RuntimeError(f"events 조회에 실패했습니다: {result.stderr.strip()}")
    return parse_events(result.stdout)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Wearon 주간 지표 리포트 (Markdown)")
    parser.add_argument("--week", type=date.fromisoformat, help="이 날짜가 든 주 (YYYY-MM-DD, 기본: 오늘)")
    args = parser.parse_args(argv)
    database_url = os.environ.get("DATABASE_URL")
    if not database_url:
        print("DATABASE_URL 환경 변수가 필요합니다 (postgresql://...).", file=sys.stderr)
        return 2
    now = datetime.now(KST).replace(tzinfo=None)
    week_start = week_start_of(args.week or now.date())
    # Cohort metrics follow this week's signups for COHORT_DAYS more days
    fetch_end = week_start + timedelta(days=7 + COHORT_DAYS + 1)
    try:
        events = fetch_events(database_url, week_start, fetch_end)
    except (RuntimeError, ValueError, subprocess.TimeoutExpired) as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(render(compute(events, week_start, now), now))
    return 0


if __name__ == "__main__":
    sys.exit(main())
