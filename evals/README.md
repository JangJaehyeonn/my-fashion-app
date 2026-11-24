# 평가 도구 (FR-24)

유사 옷 검색(FR-11)과 코디 수 규칙 엔진(FR-09)이 사람 판단과 얼마나 맞는지 재는 오프라인 도구다.
저장소 루트에서 실행하고, 결과는 마크다운 리포트 한 파일로 남는다. 같은 입력이면 리포트도 같다 (실행 시각을 넣지 않는다).
결정 배경은 `docs/adr/0024-eval-tools.md`.

## similarity — 유사 옷 검색

```
python3 evals/similarity/run.py --out similarity-report.md
```

- 입력
  - `--items` 옷 속성 목록 (기본 `sample/items.json`): `id`, `category`(TOP·BOTTOM·SHOES·OUTER·ETC), `color`, `fit`, `material`, `pattern`, `name`.
    사진 없이 속성만 쓴다. 임베딩 문장은 백엔드와 같은 '카테고리 색상 핏 소재 패턴 이름'이다.
  - `--pairs` 정답 쌍 (기본 `sample/pairs.json`): `{"a": id, "b": id, "label": "비슷함" | "다름"}`. 같은 카테고리끼리만 적는다.
  - `--cache` 벡터 캐시 (기본 `sample/vectors.json`): `{"model": ..., "vectors": {임베딩 문장: [숫자...]}}`.
- 리포트: 기준값 0.70~0.95(0.05 간격)별 precision·recall(TP·FP·FN·TN), precision@3, 오판 목록(`--threshold`, 기본 0.85).
  유사도가 기준값과 같으면 '비슷함'으로 본다 (FR-11과 같다).
- **`--live` 없이는 네트워크를 쓰지 않는다.** 캐시에 없는 옷 문장이 있으면 실패하고 `--live`로 다시 만들라고 안내한다.
- `--live --ai-url http://127.0.0.1:8000`: 모든 옷 문장을 AI 서버 `/ai/embed`로 다시 임베딩해 `--cache` 파일을 새로 쓴다.
  OpenAI 키는 AI 서버에만 있으므로 이 도구는 키를 받지 않는다. 실제 수치를 보려면 사람 정답 파일과 함께 별도 캐시 경로를 주고 돌린다.
- `sample/vectors.json`은 **합성 벡터**다 (단어 해시 벡터, 실제 임베딩 아님). CI에서 도구가 돌아가는지만 확인하는 용도이고,
  샘플 리포트의 숫자는 실제 검색 품질을 뜻하지 않는다. 샘플 옷 목록을 바꾸면 `python3 evals/similarity/make_sample_cache.py`로 다시 만든다.

## combos — 코디 규칙

```
python3 evals/combos/run.py --out combos-report.md
```

- 입력
  - `--combos` 조합 파일 (기본 `sample/combos.json`):
    `{"id": ..., "label": "어울림" | "안 어울림", "items": {"top": {...}, "bottom": {...}, "shoes": {...}, "outer": {...}?}}`.
    옷마다 `color`, `season`, `pattern`, `fit`, `material`, `name`(설명용)을 쓴다.
  - `--rules` 규칙 파일 (기본 백엔드 `backend/src/main/resources/rules/combo-rules.json`). 규칙 파일 후보를 바꿔 넣어 비교할 수 있다.
- 엔진 판정: 조합 안의 모든 두 벌 쌍이 FR-09 규칙상 충돌하지 않으면 '어울림'.
- 리포트: 일치율, 사람×엔진 표, 불일치 조합 목록(엔진이 본 충돌 근거 포함).
- 규칙 적용은 Python으로 다시 구현했다. `combos/parity_cases.json`을 백엔드 `ComboRulesEvalParityTest`와
  `ai-server/tests/test_evals_combos.py`가 함께 확인해 두 구현이 어긋나면 테스트가 실패한다.

## CI
인수 테스트(`acceptance/phase4/test_fr24.py`)가 인자 없이 두 도구를 돌려 샘플(캐시 기반)로만 확인한다.
단위 테스트는 `ai-server/tests/test_evals_*.py`에 있다.
