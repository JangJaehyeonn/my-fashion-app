"""FR-24 (1) similar-clothes search evaluation.

Reads a list of clothes attributes and human-labelled pairs ('비슷함' / '다름'), looks up each piece's embedding in a
stored vector cache (or, only with --live, asks the AI server's /ai/embed), and reports precision / recall per
threshold, precision@3 and the misjudged pairs. Deterministic: same files in, same report out.
"""

from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

THRESHOLDS: tuple[float, ...] = (0.70, 0.75, 0.80, 0.85, 0.90, 0.95)
DEFAULT_THRESHOLD = 0.85  # wearon.purchase-check.similar-threshold default (FR-11)
SIMILAR = "비슷함"
DIFFERENT = "다름"
TOP_K = 3
SIM_DECIMALS = 6  # cosine is rounded before comparing, so 0.85 is not lost to float noise

# Same labels and order as backend ClothesEmbedder.embeddingText (category color fit material pattern name).
CATEGORY_LABELS: dict[str, str] = {"TOP": "상의", "BOTTOM": "하의", "OUTER": "아우터", "SHOES": "신발", "ETC": "기타"}
TEXT_FIELDS: tuple[str, ...] = ("color", "fit", "material", "pattern", "name")

Embedder = Callable[[list[str]], list[list[float]]]


class EvalError(ValueError):
    """Input files are malformed or inconsistent. The message is shown to the user."""


@dataclass(frozen=True)
class Item:
    id: str
    category: str
    text: str


@dataclass(frozen=True)
class Pair:
    a: str
    b: str
    similar: bool
    similarity: float


@dataclass(frozen=True)
class ThresholdRow:
    threshold: float
    tp: int
    fp: int
    fn: int
    tn: int

    @property
    def precision(self) -> float | None:
        return self.tp / (self.tp + self.fp) if self.tp + self.fp else None

    @property
    def recall(self) -> float | None:
        return self.tp / (self.tp + self.fn) if self.tp + self.fn else None


def embedding_text(raw: dict[str, Any]) -> str:
    """'카테고리 색상 핏 소재 패턴 이름' with blanks left out, the text the backend embeds."""
    label = CATEGORY_LABELS[str(raw["category"]).strip().upper()]
    parts = [label] + [str(raw[f]).strip() for f in TEXT_FIELDS if raw.get(f) is not None]
    return " ".join(p for p in parts if p)


def parse_items(raw_items: list[dict[str, Any]]) -> dict[str, Item]:
    items: dict[str, Item] = {}
    for raw in raw_items:
        item_id = str(raw.get("id", "")).strip()
        category = str(raw.get("category", "")).strip().upper()
        if not item_id:
            raise EvalError("옷 목록에 id가 없는 항목이 있습니다.")
        if item_id in items:
            raise EvalError(f"옷 id가 중복됩니다: {item_id}")
        if category not in CATEGORY_LABELS:
            raise EvalError(f"옷 {item_id}의 카테고리가 올바르지 않습니다 (미분류는 평가에서 뺍니다): {category}")
        items[item_id] = Item(item_id, category, embedding_text(raw))
    return items


def parse_labels(raw_pairs: list[dict[str, Any]], items: dict[str, Item]) -> list[tuple[str, str, bool]]:
    labels: list[tuple[str, str, bool]] = []
    seen: set[frozenset[str]] = set()
    for raw in raw_pairs:
        a, b, label = str(raw.get("a", "")), str(raw.get("b", "")), raw.get("label")
        if a not in items or b not in items or a == b:
            raise EvalError(f"정답 쌍의 옷 id가 올바르지 않습니다: {a}, {b}")
        if items[a].category != items[b].category:
            raise EvalError(f"정답 쌍 {a}–{b}의 카테고리가 다릅니다 (유사 검색은 같은 카테고리 안에서만 합니다).")
        if label not in (SIMILAR, DIFFERENT):
            raise EvalError(f"정답 쌍 {a}–{b}의 label은 '{SIMILAR}' 또는 '{DIFFERENT}'여야 합니다: {label}")
        key = frozenset((a, b))
        if key in seen:
            raise EvalError(f"정답 쌍이 중복됩니다: {a}–{b}")
        seen.add(key)
        labels.append((a, b, label == SIMILAR))
    if not labels:
        raise EvalError("정답 쌍이 하나도 없습니다.")
    return labels


def cosine(u: list[float], v: list[float]) -> float:
    if len(u) != len(v):
        raise EvalError("벡터 차원이 서로 다릅니다.")
    nu = math.sqrt(sum(x * x for x in u))
    nv = math.sqrt(sum(x * x for x in v))
    if nu == 0 or nv == 0:
        raise EvalError("길이가 0인 벡터가 있습니다.")
    return round(sum(x * y for x, y in zip(u, v)) / (nu * nv), SIM_DECIMALS)


def load_cache(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {"model": None, "vectors": {}}
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data.get("vectors"), dict):
        raise EvalError(f"벡터 캐시 형식이 올바르지 않습니다: {path}")
    return data


def refresh_cache(cache: dict[str, Any], texts: list[str], embed: Embedder, model: str) -> dict[str, Any]:
    """--live: embeds every text again and returns a new cache holding only these texts."""
    vectors = embed(texts)
    if len(vectors) != len(texts):
        raise EvalError("임베딩 응답 개수가 요청과 다릅니다.")
    return {"model": model, "vectors": dict(sorted(zip(texts, vectors)))}


def vectors_for(items: dict[str, Item], cache: dict[str, Any]) -> dict[str, list[float]]:
    stored: dict[str, list[float]] = cache["vectors"]
    missing = sorted({item.text for item in items.values() if item.text not in stored})
    if missing:
        raise EvalError(f"벡터 캐시에 없는 옷 문장이 {len(missing)}개 있습니다 (--live로 다시 만드세요): {missing[0]}")
    return {item_id: stored[item.text] for item_id, item in items.items()}


def score_pairs(labels: list[tuple[str, str, bool]], vectors: dict[str, list[float]]) -> list[Pair]:
    return [Pair(a, b, similar, cosine(vectors[a], vectors[b])) for a, b, similar in labels]


def threshold_row(pairs: list[Pair], threshold: float) -> ThresholdRow:
    # Same rule as FR-11: similarity exactly at the threshold counts as similar.
    tp = sum(1 for p in pairs if p.similar and p.similarity >= threshold)
    fp = sum(1 for p in pairs if not p.similar and p.similarity >= threshold)
    fn = sum(1 for p in pairs if p.similar and p.similarity < threshold)
    tn = sum(1 for p in pairs if not p.similar and p.similarity < threshold)
    return ThresholdRow(threshold, tp, fp, fn, tn)


def precision_at_k(pairs: list[Pair], k: int = TOP_K) -> tuple[float | None, int]:
    """Mean over query pieces with at least one '비슷함' pair: share of '비슷함' among its top-k labelled neighbours.

    Neighbours are the pieces it has a labelled pair with, ranked by similarity (ties by id), like FR-11's top 3.
    Returns (precision@k, number of queries).
    """
    neighbours: dict[str, list[tuple[float, str, bool]]] = {}
    for p in pairs:
        neighbours.setdefault(p.a, []).append((p.similarity, p.b, p.similar))
        neighbours.setdefault(p.b, []).append((p.similarity, p.a, p.similar))
    scores: list[float] = []
    for query in sorted(neighbours):
        ranked = sorted(neighbours[query], key=lambda n: (-n[0], n[1]))
        if not any(n[2] for n in ranked):
            continue
        top = ranked[:k]
        scores.append(sum(1 for n in top if n[2]) / len(top))
    return (sum(scores) / len(scores) if scores else None), len(scores)


def misjudged(pairs: list[Pair], threshold: float) -> list[Pair]:
    wrong = [p for p in pairs if (p.similarity >= threshold) != p.similar]
    return sorted(wrong, key=lambda p: (-p.similarity, p.a, p.b))


def _fmt(value: float | None) -> str:
    return "-" if value is None else f"{value:.3f}"


def render_report(items: dict[str, Item], pairs: list[Pair], sources: dict[str, str], model: str | None,
                  misjudge_threshold: float) -> str:
    n_similar = sum(1 for p in pairs if p.similar)
    p_at_k, queries = precision_at_k(pairs)
    lines = [
        "# 유사 옷 검색 평가 리포트 (FR-24)",
        "",
        f"- 옷 목록: `{sources['items']}` ({len(items)}벌)",
        f"- 정답 쌍: `{sources['pairs']}` ({len(pairs)}쌍: {SIMILAR} {n_similar}, {DIFFERENT} {len(pairs) - n_similar})",
        f"- 벡터: `{sources['cache']}` (모델: {model or '알 수 없음'})",
        "- 판정 규칙: 유사도(코사인, 소수 6자리 반올림)가 기준값 이상이면 '비슷함' (FR-11과 같다)",
        "",
        "## 기준값별 precision·recall",
        "",
        "| 기준값 | precision | recall | TP | FP | FN | TN |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for t in THRESHOLDS:
        row = threshold_row(pairs, t)
        lines.append(f"| {t:.2f} | {_fmt(row.precision)} | {_fmt(row.recall)} "
                     f"| {row.tp} | {row.fp} | {row.fn} | {row.tn} |")
    lines += [
        "",
        f"## precision@{TOP_K}",
        "",
        f"- precision@{TOP_K}: {_fmt(p_at_k)} (기준 옷 {queries}벌)",
        f"- 기준 옷마다 정답 쌍이 있는 같은 카테고리 옷을 유사도 순으로 세워 상위 {TOP_K}개 중 '{SIMILAR}' 비율의 평균. "
        f"'{SIMILAR}' 쌍이 없는 옷은 뺀다.",
        "",
        f"## 오판 목록 (기준값 {misjudge_threshold:.2f})",
        "",
    ]
    wrong = misjudged(pairs, misjudge_threshold)
    if not wrong:
        lines.append("오판 없음.")
    else:
        lines += ["| 옷 A | 옷 B | 정답 | 판정 | 유사도 |", "| --- | --- | --- | --- | --- |"]
        for p in wrong:
            truth = SIMILAR if p.similar else DIFFERENT
            guess = SIMILAR if p.similarity >= misjudge_threshold else DIFFERENT
            lines.append(f"| {p.a} ({items[p.a].text}) | {p.b} ({items[p.b].text}) | {truth} | {guess} "
                         f"| {p.similarity:.3f} |")
    return "\n".join(lines) + "\n"
