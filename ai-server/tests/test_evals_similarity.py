"""FR-24 (1) similarity evaluation tool (evals/similarity). No network: the live embedder is replaced."""

from __future__ import annotations

import importlib.util
import json
import socket
import sys
from pathlib import Path
from types import ModuleType
from typing import Any

import pytest

SIM_DIR = Path(__file__).resolve().parents[2] / "evals" / "similarity"
if str(SIM_DIR) not in sys.path:
    sys.path.insert(0, str(SIM_DIR))

import similarity_eval as se  # noqa: E402


def load_run() -> ModuleType:
    spec = importlib.util.spec_from_file_location("similarity_run", SIM_DIR / "run.py")
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def pair(similar: bool, sim: float, a: str = "a", b: str = "b") -> se.Pair:
    return se.Pair(a, b, similar, sim)


def test_embedding_text_matches_backend_format() -> None:
    raw = {"category": "top", "color": " 화이트 ", "fit": None, "material": "면", "pattern": "", "name": "셔츠"}
    assert se.embedding_text(raw) == "상의 화이트 면 셔츠"


def test_unclassified_items_are_rejected() -> None:
    with pytest.raises(se.EvalError):
        se.parse_items([{"id": "x", "category": "UNCLASSIFIED"}])


def test_labels_must_share_category_and_use_known_label() -> None:
    items = se.parse_items([{"id": "t", "category": "TOP"}, {"id": "b", "category": "BOTTOM"},
                            {"id": "t2", "category": "TOP"}])
    with pytest.raises(se.EvalError):
        se.parse_labels([{"a": "t", "b": "b", "label": "다름"}], items)
    with pytest.raises(se.EvalError):
        se.parse_labels([{"a": "t", "b": "t2", "label": "같음"}], items)
    assert se.parse_labels([{"a": "t", "b": "t2", "label": "비슷함"}], items) == [("t", "t2", True)]


def test_similarity_exactly_at_threshold_counts_as_similar() -> None:
    row = se.threshold_row([pair(True, 0.85), pair(False, 0.85), pair(True, 0.849999), pair(False, 0.5)], 0.85)
    assert (row.tp, row.fp, row.fn, row.tn) == (1, 1, 1, 1)
    assert row.precision == 0.5
    assert row.recall == 0.5


def test_precision_is_empty_when_nothing_is_predicted_similar() -> None:
    row = se.threshold_row([pair(True, 0.1)], 0.9)
    assert row.precision is None
    assert row.recall == 0.0


def test_cosine_is_rounded_so_float_noise_does_not_flip_threshold() -> None:
    assert se.cosine([1.0, 0.0], [1.0, 0.0]) == 1.0
    assert se.cosine([1.0, 0.0], [0.0, 2.0]) == 0.0


def test_precision_at_3_uses_top_three_neighbours() -> None:
    pairs = [pair(True, 0.9, "q", "a"), pair(False, 0.8, "q", "b"), pair(True, 0.7, "q", "c"),
             pair(True, 0.6, "q", "d")]
    value, queries = se.precision_at_k(pairs, 3)
    # q: top3 a(T) b(F) c(T) -> 2/3; a, c, d each have one similar neighbour -> 1.0; b has none -> skipped
    assert queries == 4
    assert value == pytest.approx((2 / 3 + 1 + 1 + 1) / 4)


def test_misjudged_lists_wrong_pairs_in_similarity_order() -> None:
    pairs = [pair(False, 0.86, "a", "b"), pair(True, 0.9, "c", "d"), pair(True, 0.5, "e", "f")]
    assert [(p.a, p.b) for p in se.misjudged(pairs, 0.85)] == [("a", "b"), ("e", "f")]


def test_missing_vector_fails_instead_of_calling_out() -> None:
    items = se.parse_items([{"id": "t", "category": "TOP", "name": "셔츠"}])
    with pytest.raises(se.EvalError):
        se.vectors_for(items, {"model": None, "vectors": {}})


def test_sample_cache_covers_every_sample_item() -> None:
    items = se.parse_items(json.loads((SIM_DIR / "sample" / "items.json").read_text(encoding="utf-8")))
    cache = se.load_cache(SIM_DIR / "sample" / "vectors.json")
    assert set(se.vectors_for(items, cache)) == set(items)


def test_offline_run_makes_report_without_network(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    calls: list[Any] = []

    def blocked(self: socket.socket, address: Any) -> None:
        calls.append(address)
        raise OSError("blocked")

    monkeypatch.setattr(socket.socket, "connect", blocked)
    out = tmp_path / "report.md"
    assert load_run().main(["--out", str(out)]) == 0
    report = out.read_text(encoding="utf-8")
    assert calls == []
    for t in ("0.70", "0.75", "0.80", "0.85", "0.90", "0.95"):
        assert f"| {t} |" in report
    assert "precision@3" in report
    assert "오판 목록" in report


def test_live_run_embeds_through_ai_server_and_rewrites_cache(tmp_path: Path,
                                                               monkeypatch: pytest.MonkeyPatch) -> None:
    run = load_run()
    requested: list[str] = []

    def fake_embedder(ai_url: str) -> se.Embedder:
        assert ai_url == "http://127.0.0.1:9999"

        def embed(texts: list[str]) -> list[list[float]]:
            requested.extend(texts)
            return [[1.0, float(len(t))] for t in texts]

        return embed

    monkeypatch.setattr(run, "live_embedder", fake_embedder)
    cache, out = tmp_path / "vectors.json", tmp_path / "report.md"
    assert run.main(["--live", "--ai-url", "http://127.0.0.1:9999", "--cache", str(cache), "--out", str(out)]) == 0
    stored = json.loads(cache.read_text(encoding="utf-8"))
    assert sorted(stored["vectors"]) == sorted(set(requested))
    assert stored["model"] == run.LIVE_MODEL


def test_bad_input_exits_with_error(tmp_path: Path) -> None:
    pairs = tmp_path / "pairs.json"
    pairs.write_text("[]", encoding="utf-8")
    assert load_run().main(["--pairs", str(pairs), "--out", str(tmp_path / "r.md")]) == 2
