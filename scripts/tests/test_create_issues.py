import tomllib
from pathlib import Path

from create_issues import body_of, labels_of

ROOT = Path(__file__).resolve().parents[2]


def load() -> list[dict]:
    with (ROOT / "docs/requirements/issues.toml").open("rb") as f:
        return list(tomllib.load(f)["requirement"])


def test_requirements_are_consistent() -> None:
    reqs = load()
    ids = [r["id"] for r in reqs]
    assert len(ids) == len(set(ids))
    for r in reqs:
        assert r["acceptance"], r["id"]
        assert 1 <= r["phase"] <= 5, r["id"]
        for dep in r.get("depends", []):
            assert dep in ids, (r["id"], dep)


def test_human_verify_label_and_body() -> None:
    req = {"id": "FR-99", "title": "t", "area": "android", "priority": "필수", "phase": 3,
           "detail": "d", "acceptance": ["a"], "verify": "human", "model": "sonnet"}
    assert labels_of(req) == ["FR-99", "phase-3", "필수", "human-verify"]
    body = body_of(req)
    assert "Depends on: 없음" in body
    assert "에뮬레이터" in body


def test_model_field_ignored() -> None:
    req = {"id": "FR-98", "title": "t", "area": "backend", "priority": "권장", "phase": 2,
           "detail": "d", "acceptance": ["a"], "model": "opus"}
    assert labels_of(req) == ["FR-98", "phase-2", "권장"]
