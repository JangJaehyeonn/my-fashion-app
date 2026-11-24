"""FR-24 combo rule evaluation. Usage (from the repository root):

    python3 evals/combos/run.py --out report.md
    python3 evals/combos/run.py --combos my-combos.json --rules path/to/combo-rules.json --out report.md

No network call: the FR-09 rules are applied locally to the labelled combos.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from combo_eval import ComboRules, EvalError, judge, render_report

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
DEFAULT_RULES = REPO / "backend" / "src" / "main" / "resources" / "rules" / "combo-rules.json"


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="코디 규칙 평가 (FR-24)")
    parser.add_argument("--combos", type=Path, default=HERE / "sample" / "combos.json",
                        help="'어울림/안 어울림' 조합 JSON")
    parser.add_argument("--rules", type=Path, default=DEFAULT_RULES, help="FR-09 규칙 파일")
    parser.add_argument("--out", type=Path, required=True, help="리포트(마크다운) 경로")
    return parser.parse_args(argv)


def display(path: Path) -> str:
    resolved = path.resolve()
    return resolved.relative_to(REPO).as_posix() if resolved.is_relative_to(REPO) else path.as_posix()


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    try:
        rules = ComboRules.load(args.rules)
        judged = judge(json.loads(args.combos.read_text(encoding="utf-8")), rules)
    except (EvalError, OSError, AttributeError, json.JSONDecodeError) as e:
        print(f"평가 실패: {e}", file=sys.stderr)
        return 2
    report = render_report(judged, {"combos": display(args.combos), "rules": display(args.rules)})
    args.out.write_text(report, encoding="utf-8")
    print(f"리포트를 만들었습니다: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
