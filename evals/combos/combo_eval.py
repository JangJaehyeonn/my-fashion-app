"""FR-24 (2) combo rule evaluation.

Compares human '어울림 / 안 어울림' labels with the FR-09 combo rules. The rules are read from the backend's own rules
file (backend/src/main/resources/rules/combo-rules.json) and applied with the same semantics as
backend ComboRules / ComboEngine (docs/adr/0008-combo-count-rules.md); parity_cases.json is checked by both the
Java and the Python unit tests so the two cannot drift apart. Deterministic: same files in, same report out.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from itertools import combinations
from pathlib import Path
from typing import Any

GOOD = "어울림"
BAD = "안 어울림"
SUPPORTED_VERSION = 1
RULE_KEYS = {"version", "neutralColors", "colorConflicts", "seasonConflicts", "styleConflicts"}
STYLE_ATTRIBUTES = ("pattern", "fit", "material")
# Slot -> category. A combo is top 1 + bottom 1 + shoes 1 (+ outer 0..1), in this order.
SLOTS: tuple[tuple[str, str], ...] = (("top", "TOP"), ("bottom", "BOTTOM"), ("shoes", "SHOES"), ("outer", "OUTER"))
REQUIRED_SLOTS = ("top", "bottom", "shoes")
SLOT_LABELS = {"top": "상의", "bottom": "하의", "shoes": "신발", "outer": "아우터"}


class EvalError(ValueError):
    """Input files are malformed or inconsistent. The message is shown to the user."""


def _normalize(value: str) -> str:
    return value.strip().lower()


def _key(a: str, b: str) -> tuple[str, str]:
    x, y = _normalize(a), _normalize(b)
    return (x, y) if x <= y else (y, x)


def _pair_keys(pairs: list[Any]) -> set[tuple[str, str]]:
    keys: set[tuple[str, str]] = set()
    for pair in pairs:
        if not isinstance(pair, list) or len(pair) != 2 or pair[0] is None or pair[1] is None:
            raise EvalError(f"충돌 규칙은 값 두 개의 쌍이어야 합니다: {pair}")
        keys.add(_key(pair[0], pair[1]))
    return keys


@dataclass(frozen=True)
class ComboRules:
    neutral_colors: frozenset[str]
    color_conflicts: frozenset[tuple[str, str]]
    season_conflicts: frozenset[tuple[str, str]]
    style_attribute: str
    style_conflicts: frozenset[tuple[str, str]]

    @staticmethod
    def parse(data: dict[str, Any]) -> "ComboRules":
        unknown = set(data) - RULE_KEYS
        if unknown:
            raise EvalError(f"규칙 파일에 모르는 항목이 있습니다: {sorted(unknown)}")
        if data.get("version") != SUPPORTED_VERSION:
            raise EvalError(f"지원하지 않는 규칙 파일 버전입니다: {data.get('version')}")
        style = data.get("styleConflicts") or {"attribute": "pattern", "pairs": []}
        if set(style) - {"attribute", "pairs"}:
            raise EvalError(f"styleConflicts에 모르는 항목이 있습니다: {sorted(set(style) - {'attribute', 'pairs'})}")
        attribute = _normalize(style.get("attribute") or "pattern")
        if attribute not in STYLE_ATTRIBUTES:
            raise EvalError(f"styleConflicts.attribute가 올바르지 않습니다: {attribute}")
        return ComboRules(
            neutral_colors=frozenset(_normalize(c) for c in data.get("neutralColors") or []),
            color_conflicts=frozenset(_pair_keys(data.get("colorConflicts") or [])),
            season_conflicts=frozenset(_pair_keys(data.get("seasonConflicts") or [])),
            style_attribute=attribute,
            style_conflicts=frozenset(_pair_keys(style.get("pairs") or [])),
        )

    @staticmethod
    def load(path: Path) -> "ComboRules":
        return ComboRules.parse(json.loads(path.read_text(encoding="utf-8")))

    def conflicts(self, a: dict[str, Any], b: dict[str, Any]) -> list[str]:
        """Names of the rules two pieces break (empty = compatible). Mirrors backend ComboRules.compatible."""
        found: list[str] = []
        if self._color_conflict(a.get("color"), b.get("color")):
            found.append(f"색 충돌 {a['color']}–{b['color']}")
        if self._conflict(self.season_conflicts, a.get("season"), b.get("season")):
            found.append(f"계절 충돌 {a['season']}–{b['season']}")
        style_a, style_b = a.get(self.style_attribute), b.get(self.style_attribute)
        if self._conflict(self.style_conflicts, style_a, style_b):
            found.append(f"스타일({self.style_attribute}) 충돌 {style_a}–{style_b}")
        return found

    def compatible(self, a: dict[str, Any], b: dict[str, Any]) -> bool:
        return not self.conflicts(a, b)

    def _color_conflict(self, a: str | None, b: str | None) -> bool:
        if a is None or b is None or _normalize(a) in self.neutral_colors or _normalize(b) in self.neutral_colors:
            return False
        return self._conflict(self.color_conflicts, a, b)

    @staticmethod
    def _conflict(pairs: frozenset[tuple[str, str]], a: str | None, b: str | None) -> bool:
        return a is not None and b is not None and _key(a, b) in pairs


@dataclass(frozen=True)
class Judged:
    id: str
    pieces: tuple[tuple[str, dict[str, Any]], ...]
    human_good: bool
    reasons: tuple[str, ...]

    @property
    def engine_good(self) -> bool:
        return not self.reasons

    @property
    def agrees(self) -> bool:
        return self.human_good == self.engine_good


def parse_combo(raw: dict[str, Any]) -> tuple[str, tuple[tuple[str, dict[str, Any]], ...], bool]:
    combo_id = str(raw.get("id", "")).strip()
    items = raw.get("items") or {}
    if not combo_id:
        raise EvalError("조합 파일에 id가 없는 항목이 있습니다.")
    unknown = set(items) - {slot for slot, _ in SLOTS}
    missing = [slot for slot in REQUIRED_SLOTS if slot not in items]
    if unknown or missing:
        raise EvalError(f"조합 {combo_id}은 상의·하의·신발 1벌씩(+아우터 0~1벌)이어야 합니다.")
    if raw.get("label") not in (GOOD, BAD):
        raise EvalError(f"조합 {combo_id}의 label은 '{GOOD}' 또는 '{BAD}'여야 합니다: {raw.get('label')}")
    pieces = tuple((slot, items[slot]) for slot, _ in SLOTS if slot in items)
    return combo_id, pieces, raw["label"] == GOOD


def judge(raw_combos: list[dict[str, Any]], rules: ComboRules) -> list[Judged]:
    """Engine verdict per combo: valid when every two pieces are compatible (FR-09)."""
    judged: list[Judged] = []
    seen: set[str] = set()
    for raw in raw_combos:
        combo_id, pieces, human_good = parse_combo(raw)
        if combo_id in seen:
            raise EvalError(f"조합 id가 중복됩니다: {combo_id}")
        seen.add(combo_id)
        reasons = [r for (_, a), (_, b) in combinations(pieces, 2) for r in rules.conflicts(a, b)]
        judged.append(Judged(combo_id, pieces, human_good, tuple(reasons)))
    if not judged:
        raise EvalError("조합이 하나도 없습니다.")
    return judged


def describe(piece: dict[str, Any]) -> str:
    fields = ("color", "season", "pattern", "fit", "material", "name")
    return " ".join(str(piece[f]).strip() for f in fields if piece.get(f) is not None and str(piece[f]).strip())


def render_report(judged: list[Judged], sources: dict[str, str]) -> str:
    agree = sum(1 for j in judged if j.agrees)
    counts = {(h, e): sum(1 for j in judged if j.human_good == h and j.engine_good == e)
              for h in (True, False) for e in (True, False)}
    lines = [
        "# 코디 규칙 평가 리포트 (FR-24)",
        "",
        f"- 조합 파일: `{sources['combos']}` ({len(judged)}개)",
        f"- 규칙 파일: `{sources['rules']}` (FR-09 엔진 규칙)",
        "- 엔진 판정: 조합 안의 모든 두 벌 쌍이 충돌하지 않으면 '어울림'",
        "",
        "## 일치율",
        "",
        f"- 일치율: {agree / len(judged) * 100:.1f}% ({agree}/{len(judged)})",
        "",
        f"| 사람 \\ 엔진 | {GOOD} | {BAD} |",
        "| --- | --- | --- |",
        f"| {GOOD} | {counts[(True, True)]} | {counts[(True, False)]} |",
        f"| {BAD} | {counts[(False, True)]} | {counts[(False, False)]} |",
        "",
        "## 불일치 조합 목록",
        "",
    ]
    mismatches = [j for j in judged if not j.agrees]
    if not mismatches:
        lines.append("불일치 없음.")
        return "\n".join(lines) + "\n"
    lines += ["| 조합 | 구성 | 사람 | 엔진 | 엔진 근거 |", "| --- | --- | --- | --- | --- |"]
    for j in mismatches:
        parts = " / ".join(f"{SLOT_LABELS[slot]} {describe(piece)}" for slot, piece in j.pieces)
        reason = ", ".join(j.reasons) if j.reasons else "충돌 규칙 없음"
        lines.append(f"| {j.id} | {parts} | {GOOD if j.human_good else BAD} "
                     f"| {GOOD if j.engine_good else BAD} | {reason} |")
    return "\n".join(lines) + "\n"
