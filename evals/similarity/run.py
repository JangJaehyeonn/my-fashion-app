"""FR-24 similar-clothes search evaluation. Usage (from the repository root):

    python3 evals/similarity/run.py --out report.md
    python3 evals/similarity/run.py --live --ai-url http://127.0.0.1:8000 --cache my-vectors.json --out report.md

Without --live no network call is made: vectors come from the stored cache only.
With --live every piece is embedded again through the AI server (/ai/embed, which holds the OpenAI key)
and the cache file is rewritten.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from similarity_eval import (DEFAULT_THRESHOLD, Embedder, EvalError, load_cache, parse_items, parse_labels, refresh_cache,
                             render_report, score_pairs, vectors_for)

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SAMPLE = HERE / "sample"
LIVE_MODEL = "text-embedding-3-small (AI 서버 /ai/embed)"
EMBED_BATCH = 100  # AI server limit per {"texts": [...]} request
LIVE_TIMEOUT_S = 30


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="유사 옷 검색 평가 (FR-24)")
    parser.add_argument("--items", type=Path, default=SAMPLE / "items.json", help="옷 속성 목록 JSON")
    parser.add_argument("--pairs", type=Path, default=SAMPLE / "pairs.json", help="'비슷함/다름' 정답 쌍 JSON")
    parser.add_argument("--cache", type=Path, default=SAMPLE / "vectors.json", help="저장된 벡터 캐시 JSON")
    parser.add_argument("--out", type=Path, required=True, help="리포트(마크다운) 경로")
    parser.add_argument("--threshold", type=float, default=DEFAULT_THRESHOLD, help="오판 목록을 뽑을 기준값")
    parser.add_argument("--live", action="store_true", help="AI 서버로 실제 임베딩을 다시 받아 캐시를 갱신한다")
    parser.add_argument("--ai-url", default="http://127.0.0.1:8000", help="--live에서 쓸 AI 서버 주소")
    return parser.parse_args(argv)


def display(path: Path) -> str:
    resolved = path.resolve()
    return resolved.relative_to(REPO).as_posix() if resolved.is_relative_to(REPO) else path.as_posix()


def live_embedder(ai_url: str) -> Embedder:
    import urllib.request  # imported only for --live

    def embed(texts: list[str]) -> list[list[float]]:
        vectors: list[list[float]] = []
        for start in range(0, len(texts), EMBED_BATCH):
            body = json.dumps({"texts": texts[start:start + EMBED_BATCH]}).encode("utf-8")
            request = urllib.request.Request(f"{ai_url.rstrip('/')}/ai/embed", data=body,
                                             headers={"Content-Type": "application/json"}, method="POST")
            with urllib.request.urlopen(request, timeout=LIVE_TIMEOUT_S) as response:  # noqa: S310 (operator URL)
                vectors += json.loads(response.read().decode("utf-8"))["embeddings"]
        return vectors

    return embed


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    try:
        items = parse_items(json.loads(args.items.read_text(encoding="utf-8")))
        labels = parse_labels(json.loads(args.pairs.read_text(encoding="utf-8")), items)
        cache = load_cache(args.cache)
        if args.live:
            texts = sorted({item.text for item in items.values()})
            cache = refresh_cache(cache, texts, live_embedder(args.ai_url), LIVE_MODEL)
            args.cache.write_text(json.dumps(cache, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        pairs = score_pairs(labels, vectors_for(items, cache))
    except (EvalError, OSError, KeyError, json.JSONDecodeError) as e:
        print(f"평가 실패: {e}", file=sys.stderr)
        return 2
    sources = {"items": display(args.items), "pairs": display(args.pairs), "cache": display(args.cache)}
    report = render_report(items, pairs, sources, cache.get("model"), args.threshold)
    args.out.write_text(report, encoding="utf-8")
    print(f"리포트를 만들었습니다: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
