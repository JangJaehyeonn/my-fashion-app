"""FR-24 (2) combo rule evaluation tool (evals/combos)."""

from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path
from types import ModuleType
from typing import Any

import pytest

REPO = Path(__file__).resolve().parents[2]
COMBOS_DIR = REPO / "evals" / "combos"
RULES = REPO / "backend" / "src" / "main" / "resources" / "rules" / "combo-rules.json"
if str(COMBOS_DIR) not in sys.path:
    sys.path.insert(0, str(COMBOS_DIR))

import combo_eval as ce  # noqa: E402


def load_run() -> ModuleType:
    spec = importlib.util.spec_from_file_location("combos_run", COMBOS_DIR / "run.py")
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def combo(combo_id: str, label: str, **items: dict[str, Any]) -> dict[str, Any]:
    return {"id": combo_id, "label": label, "items": items}


def test_parity_cases_match_backend_engine_expectations() -> None:
    rules = ce.ComboRules.load(RULES)
    cases = json.loads((COMBOS_DIR / "parity_cases.json").read_text(encoding="utf-8"))["cases"]
    assert cases
    for case in cases:
        assert rules.compatible(case["a"], case["b"]) is case["compatible"], case


def test_rules_file_with_unknown_key_or_version_is_rejected() -> None:
    with pytest.raises(ce.EvalError):
        ce.ComboRules.parse({"version": 2})
    with pytest.raises(ce.EvalError):
        ce.ComboRules.parse({"version": 1, "extra": []})


def test_style_attribute_follows_rules_file() -> None:
    rules = ce.ComboRules.parse({"version": 1, "styleConflicts": {"attribute": "FIT", "pairs": [["슬림", "와이드"]]}})
    assert not rules.compatible({"fit": "슬림"}, {"fit": "와이드"})
    assert rules.compatible({"pattern": "체크", "fit": "슬림"}, {"pattern": "스트라이프"})


def test_combo_needs_top_bottom_shoes() -> None:
    rules = ce.ComboRules.load(RULES)
    with pytest.raises(ce.EvalError):
        ce.judge([combo("x", "어울림", top={}, bottom={})], rules)
    with pytest.raises(ce.EvalError):
        ce.judge([combo("x", "어울림", top={}, bottom={}, shoes={}, bag={})], rules)


def test_engine_checks_every_pair_including_outer() -> None:
    rules = ce.ComboRules.load(RULES)
    judged = ce.judge([
        combo("ok", "어울림", top={"color": "레드"}, bottom={"color": "블랙"}, shoes={"color": "화이트"}),
        combo("outer", "어울림", top={"color": "레드"}, bottom={"color": "블랙"}, shoes={"color": "화이트"},
              outer={"color": "그린"}),
    ], rules)
    assert judged[0].engine_good and judged[0].agrees
    assert not judged[1].engine_good and not judged[1].agrees
    assert judged[1].reasons == ("색 충돌 레드–그린",)


def test_report_has_agreement_and_mismatch_list(tmp_path: Path) -> None:
    combos = tmp_path / "combos.json"
    combos.write_text(json.dumps([
        combo("c1", "어울림", top={"color": "레드"}, bottom={"color": "그린"}, shoes={"color": "블랙"}),
        combo("c2", "안 어울림", top={"color": "레드"}, bottom={"color": "그린"}, shoes={"color": "블랙"}),
    ], ensure_ascii=False), encoding="utf-8")
    out = tmp_path / "report.md"
    assert load_run().main(["--combos", str(combos), "--out", str(out)]) == 0
    report = out.read_text(encoding="utf-8")
    assert "일치율: 50.0% (1/2)" in report
    assert "## 불일치 조합 목록" in report
    assert "| c1 |" in report
    assert "| c2 |" not in report


def test_sample_run_is_deterministic(tmp_path: Path) -> None:
    run = load_run()
    first, second = tmp_path / "a.md", tmp_path / "b.md"
    assert run.main(["--out", str(first)]) == 0
    assert run.main(["--out", str(second)]) == 0
    assert first.read_text(encoding="utf-8") == second.read_text(encoding="utf-8")
