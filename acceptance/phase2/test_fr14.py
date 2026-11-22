"""FR-14 이벤트 기록과 주간 지표 리포트.

가정 (NOTES.md 참고)
- events(id, user_id, type, props(JSON), created_at). 조회 API가 없어 stack.sql로 본다.
- APP_OPENED는 POST /api/events/app-open (본문 없음) → 2xx.
- tools/metrics/weekly_report.py는 환경 변수 DATABASE_URL(postgresql://...)로 DB에 붙어 Markdown을 표준 출력에 쓴다.
  하네스 postgres는 127.0.0.1:15432 (acceptance/compose.yml).
- 가입은 FR-05 아이디 가입 API(동의 3종 포함), 살까 말까는 FR-11·FR-12 API.
"""

from __future__ import annotations

import os
import subprocess
import sys
import uuid
from pathlib import Path
from typing import Any

import httpx
import pytest

from acceptance.conftest import Stack

REPO = Path(__file__).resolve().parents[2]
REPORT = REPO / "tools" / "metrics" / "weekly_report.py"
DATABASE_URL = "postgresql://fashionapp:fashionapp@127.0.0.1:15432/fashionapp"
CORE_TYPES = ("SIGNUP", "CLOTHES_ADDED", "PURCHASE_CHECKED", "PURCHASE_DECIDED")
CLOSET = {
    "t1": "top_white_shirt.jpg", "t2": "top_grey_tee.jpg",
    "b1": "bottom_black_slacks.jpg", "b2": "bottom_navy_pants.jpg",
    "s1": "shoes_white_sneakers.jpg",
}


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    return res.json()["data"]


def signup(stack: Stack) -> tuple[str, httpx.Client]:
    login_id = f"evt{uuid.uuid4().hex[:12]}"
    body = {"loginId": login_id, "password": "wearon1234", "nickname": "이벤트테스트",
            "termsAgreed": True, "privacyAgreed": True, "ageOver14": True}
    res = httpx.post(f"{stack.api}/api/auth/signup", json=body, timeout=10)
    assert res.status_code in (200, 201), res.text
    token = res.json()["data"]["accessToken"]
    client = httpx.Client(base_url=stack.api, headers={"Authorization": f"Bearer {token}"}, timeout=30)
    user_id = client.get("/api/users/me").json()["data"]["id"]
    return user_id, client


def event_types(stack: Stack, user_id: str) -> list[str]:
    out = stack.sql(f"SELECT type FROM events WHERE user_id = '{user_id}' ORDER BY created_at, id")
    return [line for line in out.splitlines() if line]


def event_props(stack: Stack, user_id: str, event_type: str | None = None) -> list[str]:
    where = f"user_id = '{user_id}'" + (f" AND type = '{event_type}'" if event_type else "")
    out = stack.sql(f"SELECT coalesce(props::text, '') FROM events WHERE {where} ORDER BY created_at, id")
    return out.splitlines()


def dedupe_runs(items: list[str]) -> list[str]:
    result: list[str] = []
    for item in items:
        if not result or result[-1] != item:
            result.append(item)
    return result


@pytest.mark.fr("FR-14")
def test_core_flow_records_four_event_types_in_order(stack, tiny_jpg) -> None:
    user_id, client = signup(stack)
    try:
        for filename in CLOSET.values():
            register(client, tiny_jpg, filename)
        res = client.post("/api/purchase-checks", files={"image": ("top_black_hoodie.jpg", tiny_jpg, "image/jpeg")})
        assert res.status_code == 200, res.text
        result = res.json()["data"]
        res = client.post(f"/api/purchase-checks/{result['id']}/decision", json={"decision": "SKIP"})
        assert res.status_code == 200, res.text

        core = [t for t in event_types(stack, user_id) if t in CORE_TYPES]
        assert dedupe_runs(core) == list(CORE_TYPES), core
        assert core.count("CLOTHES_ADDED") == len(CLOSET)

        checked = event_props(stack, user_id, "PURCHASE_CHECKED")
        assert len(checked) == 1
        assert result["verdict"] in checked[0] and '"plus"' in checked[0], checked[0]
        decided = event_props(stack, user_id, "PURCHASE_DECIDED")
        assert len(decided) == 1 and '"decision"' in decided[0], decided
    finally:
        client.close()


@pytest.mark.fr("FR-14")
def test_event_props_have_no_personal_data(stack, tiny_jpg) -> None:
    user_id, client = signup(stack)
    try:
        names = [register(client, tiny_jpg, f)["name"] for f in CLOSET.values()]
        lat, lon = 37.51, 127.04
        res = client.post("/api/outfits/recommend/closet", json={
            "temperature": 12.0, "condition": "맑음", "situation": "DAILY", "lat": lat, "lon": lon})
        assert res.status_code == 200, res.text
        ids = [c["id"] for c in client.get("/api/clothes").json()["data"]]
        assert client.post("/api/wears", json={"clothesIds": ids[:3]}).status_code in (200, 201)
        res = client.post("/api/purchase-checks", files={"image": ("top_black_hoodie.jpg", tiny_jpg, "image/jpeg")},
                          data={"price": "39000"})
        assert res.status_code == 200, res.text
        check = res.json()["data"]
        res = client.post(f"/api/purchase-checks/{check['id']}/decision", json={"decision": "BUY"})
        assert res.status_code == 200, res.text
        assert client.post("/api/events/app-open").status_code in (200, 201, 204)

        types = set(event_types(stack, user_id))
        for expected in ("SIGNUP", "CLOTHES_ADDED", "OUTFIT_RECOMMENDED", "WEAR_LOGGED",
                         "PURCHASE_CHECKED", "PURCHASE_DECIDED", "APP_OPENED"):
            assert expected in types, (expected, types)

        forbidden = [*names, "블랙 hoodie", "이벤트테스트", "/api/images", "http", "tmp/", ".jpg",
                     str(lat), str(lon), '"lat"', '"lon"', "latitude", "longitude", "imageUrl", "nickname"]
        for props in event_props(stack, user_id):
            for word in forbidden:
                assert word not in props, (word, props)
    finally:
        client.close()


@pytest.mark.fr("FR-14")
def test_clothes_registration_succeeds_when_events_cannot_be_written(stack, user, tiny_jpg) -> None:
    stack.sql(
        "CREATE OR REPLACE FUNCTION acceptance_block_events() RETURNS trigger AS $$ "
        "BEGIN RAISE EXCEPTION 'events blocked by acceptance test'; END; $$ LANGUAGE plpgsql"
    )
    stack.sql("DROP TRIGGER IF EXISTS acceptance_block_events ON events")
    stack.sql("CREATE TRIGGER acceptance_block_events BEFORE INSERT ON events "
              "FOR EACH ROW EXECUTE FUNCTION acceptance_block_events()")
    try:
        data = register(user.client, tiny_jpg, "top_navy_knit.jpg")
        listed = {c["id"] for c in user.client.get("/api/clothes").json()["data"]}
        assert data["id"] in listed
        assert stack.sql(f"SELECT count(*) FROM events WHERE user_id = '{user.id}'") == "0"
    finally:
        stack.sql("DROP TRIGGER IF EXISTS acceptance_block_events ON events")
        stack.sql("DROP FUNCTION IF EXISTS acceptance_block_events()")


def make_sample(stack: Stack, image: bytes) -> None:
    """샘플 데이터는 API로 만든다 (events 스키마의 id 생성 방식 등을 가정하지 않기 위해).
    가입 2명, 그중 1명이 옷 5벌·살까 말까 2회(결정 BUY 1, SKIP 1)·착용 기록·앱 열기."""
    active_id, active = signup(stack)
    _, idle = signup(stack)
    try:
        ids = [register(active, image, f)["id"] for f in CLOSET.values()]
        for filename, decision in (("top_black_hoodie.jpg", "BUY"), ("outer_beige_coat.jpg", "SKIP")):
            res = active.post("/api/purchase-checks", files={"image": (filename, image, "image/jpeg")})
            assert res.status_code == 200, res.text
            check_id = res.json()["data"]["id"]
            res = active.post(f"/api/purchase-checks/{check_id}/decision", json={"decision": decision})
            assert res.status_code == 200, res.text
        assert active.post("/api/wears", json={"clothesIds": ids[:3]}).status_code in (200, 201)
        assert active.post("/api/events/app-open").status_code in (200, 201, 204)
        assert event_types(stack, active_id)
    finally:
        active.close()
        idle.close()


@pytest.mark.fr("FR-14")
def test_weekly_report_prints_metric_tables(stack, tiny_jpg) -> None:
    make_sample(stack, tiny_jpg)
    env = {**os.environ, "DATABASE_URL": DATABASE_URL}
    result = subprocess.run([sys.executable, str(REPORT)], capture_output=True, text=True, env=env,
                            cwd=REPO, timeout=120)
    assert result.returncode == 0, result.stderr
    report = result.stdout

    assert any(line.startswith("#") for line in report.splitlines()), "Markdown 제목이 있어야 한다"
    table_lines = [line for line in report.splitlines() if line.strip().startswith("|")]
    assert any(set(line.replace("|", "").strip()) <= set("-: ") for line in table_lines), "Markdown 표가 있어야 한다"
    for label in ("가입", "5벌", "살까 말까", "BUY", "THINK", "SIMILAR", "SKIP", "착용 기록", "재방문"):
        assert label in report, (label, report)


@pytest.mark.fr("FR-14")
def test_account_deletion_removes_events(stack, tiny_jpg) -> None:
    user_id, client = signup(stack)
    try:
        register(client, tiny_jpg, "top_navy_knit.jpg")
        assert int(stack.sql(f"SELECT count(*) FROM events WHERE user_id = '{user_id}'")) >= 2

        res = client.delete("/api/users/me")
        assert res.status_code == 200, res.text
        assert stack.sql(f"SELECT count(*) FROM events WHERE user_id = '{user_id}'") == "0"
    finally:
        client.close()
