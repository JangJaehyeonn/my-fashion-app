"""FR-08 기존 옷 임베딩 백필.

'기동'은 하네스 스택의 백엔드 컨테이너를 다시 시작해 흉내낸다 (docker compose restart backend).
그래서 이 테스트는 하네스가 띄운 스택에서만 돈다 (WEARON_ACCEPTANCE_URL로 붙은 외부 스택은 다시 시작할 수 없다).
가짜 AI 서버는 다시 시작하지 않으므로 /__control 상태와 호출 기록이 재시작 동안 유지된다.
"""

from __future__ import annotations

import subprocess
import time
from typing import Any

import httpx
import pytest

from acceptance.conftest import COMPOSE, Stack, _wait_ready

BACKFILL_TIMEOUT_S = 90


def register(client: httpx.Client, image: bytes, filename: str) -> dict[str, Any]:
    res = client.post("/api/clothes", files={"image": (filename, image, "image/jpeg")})
    assert res.status_code == 201, res.text
    return res.json()["data"]


def restart_backend(stack: Stack) -> None:
    subprocess.run([*COMPOSE, "restart", "backend"], check=True)
    _wait_ready(stack.api, stack.fake_ai)


def empty_embeddings(stack: Stack, user_id: str) -> int:
    return int(stack.sql(f"SELECT count(*) FROM clothes WHERE user_id = '{user_id}' AND embedding IS NULL"))


def clear_embeddings(stack: Stack, user_id: str) -> None:
    stack.sql(f"UPDATE clothes SET embedding = NULL WHERE user_id = '{user_id}'")


def wait_until_filled(stack: Stack, user_id: str) -> None:
    deadline = time.monotonic() + BACKFILL_TIMEOUT_S
    while time.monotonic() < deadline:
        if empty_embeddings(stack, user_id) == 0:
            return
        time.sleep(2)
    raise AssertionError(f"{BACKFILL_TIMEOUT_S}초가 지나도 임베딩이 비어 있는 옷이 {empty_embeddings(stack, user_id)}벌 남았다")


def seed_closet(user, tiny_jpg: bytes) -> list[str]:
    return [
        register(user.client, tiny_jpg, name)["id"]
        for name in ("top_navy_knit.jpg", "bottom_black_slacks.jpg", "shoes_white_sneakers.jpg")
    ]


@pytest.mark.fr("FR-08")
def test_startup_backfills_missing_embeddings(stack, user, tiny_jpg) -> None:
    seed_closet(user, tiny_jpg)
    clear_embeddings(stack, user.id)
    assert empty_embeddings(stack, user.id) == 3

    restart_backend(stack)
    wait_until_filled(stack, user.id)
    assert int(stack.sql(
        f"SELECT count(*) FROM clothes WHERE user_id = '{user.id}' AND vector_dims(embedding) = 1536"
    )) == 3


@pytest.mark.fr("FR-08")
def test_startup_succeeds_when_embedding_is_down(stack, user, fake_ai, tiny_jpg) -> None:
    seed_closet(user, tiny_jpg)
    clear_embeddings(stack, user.id)
    fake_ai.post("/__control/paths", json={"/ai/embed": "down"}).raise_for_status()

    restart_backend(stack)  # _wait_ready: 백엔드가 401로 응답할 때까지 기다린다

    res = user.client.get("/api/clothes")
    assert res.status_code == 200, res.text
    assert len(res.json()["data"]) == 3
    # 임베딩 실패는 기동을 막지 않고, 옷은 그대로다
    assert empty_embeddings(stack, user.id) == 3

    # 임베딩이 살아나면 다음 기동 때 채워진다
    fake_ai.post("/__control/paths", json={"/ai/embed": "normal"}).raise_for_status()
    restart_backend(stack)
    wait_until_filled(stack, user.id)


@pytest.mark.fr("FR-08")
def test_default_startup_does_not_call_classify(stack, user, fake_ai, tiny_jpg) -> None:
    ids = seed_closet(user, tiny_jpg)
    clear_embeddings(stack, user.id)
    fake_ai.post("/__control/reset").raise_for_status()

    restart_backend(stack)
    wait_until_filled(stack, user.id)

    paths = [c["path"] for c in fake_ai.get("/__calls").json()]
    assert "/ai/clothes/classify" not in paths, paths
    assert "/ai/embed" in paths, paths

    # 재분류 백필이 꺼져 있으므로 분류 값도 그대로다
    listed = {c["id"]: c for c in user.client.get("/api/clothes").json()["data"]}
    assert [listed[i]["category"] for i in ids] == ["TOP", "BOTTOM", "SHOES"]
