"""Rebuilds the SYNTHETIC sample vector cache (sample/vectors.json) used by CI. No network.

These are not OpenAI embeddings: each word of the embedding text gets a fixed pseudo-random vector derived from its
SHA-256, and a piece's vector is the weighted sum of its words (the category word weighs more). It only gives the
offline sample realistic-looking similarities so the report can be checked. Real numbers need `run.py --live`.

    python3 evals/similarity/make_sample_cache.py
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

from similarity_eval import CATEGORY_LABELS, parse_items

HERE = Path(__file__).resolve().parent
DIMENSIONS = 64
CATEGORY_WEIGHT = 4.0
WORD_WEIGHT = 1.0
MODEL = "synthetic-sample (단어 해시 벡터, 실제 임베딩 아님)"


def word_vector(word: str) -> list[float]:
    digest = b""
    counter = 0
    while len(digest) < DIMENSIONS:
        digest += hashlib.sha256(f"{word}:{counter}".encode("utf-8")).digest()
        counter += 1
    return [(byte - 127.5) / 127.5 for byte in digest[:DIMENSIONS]]


def text_vector(text: str) -> list[float]:
    total = [0.0] * DIMENSIONS
    for word in text.split():
        weight = CATEGORY_WEIGHT if word in CATEGORY_LABELS.values() else WORD_WEIGHT
        total = [t + weight * w for t, w in zip(total, word_vector(word))]
    return [round(x, 6) for x in total]


def main() -> int:
    items = parse_items(json.loads((HERE / "sample" / "items.json").read_text(encoding="utf-8")))
    texts = sorted({item.text for item in items.values()})
    cache = {"model": MODEL, "vectors": {text: text_vector(text) for text in texts}}
    (HERE / "sample" / "vectors.json").write_text(json.dumps(cache, ensure_ascii=False) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
