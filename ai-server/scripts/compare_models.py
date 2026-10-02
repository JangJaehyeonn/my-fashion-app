"""코디 추천 모델 비교 스크립트 (상황 추천 + 옷장 추천).

recommend_service 의 함수를 그대로 호출하고, settings.openai_model 만 바꿔가며 결과를 비교한다.
.env 는 ai-server 디렉터리에서 실행할 때 설정(pydantic-settings)이 알아서 읽으며, 이 스크립트는 값을 출력하지 않는다.

실행 (ai-server 디렉터리에서):
    python -m scripts.compare_models
    python -m scripts.compare_models --models gpt-4o-mini gpt-6-luna
    python -m scripts.compare_models --only situation      # situation | closet
    python -m scripts.compare_models --full                # 코디 설명을 자르지 않고 출력

주의: 모델 x 샘플 수만큼 실제 OpenAI 호출이 발생한다 (기본 3모델 x 샘플 5+5 = 30회).
"""
import argparse
import asyncio
import sys
import time
from pathlib import Path

# `python scripts/compare_models.py` 로 실행해도 app 패키지를 찾을 수 있게
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.core.config import settings  # noqa: E402
from app.schemas.outfit import (  # noqa: E402
    BodyProfile,
    ClosetItem,
    ClosetRecommendRequest,
    SituationRecommendRequest,
    WeatherInfo,
)
from app.services import recommend_service  # noqa: E402

DEFAULT_MODELS = ["gpt-4o-mini", "gpt-6-luna", "gpt-6.1-sol"]

_PROFILE_MALE = BodyProfile(height=178, weight=72, body_type="NORMAL", preferred_styles=["CASUAL", "MINIMAL"])
_PROFILE_FEMALE = BodyProfile(height=163, weight=52, body_type="SLIM", preferred_styles=["STREET", "VINTAGE"])

# (라벨, 기온, 날씨, 상황, 체형 정보)
SAMPLES = [
    ("3°C 비 / 출근", 3, "비", "출근", _PROFILE_MALE),
    ("14°C 맑음 / 데이트", 14, "맑음", "데이트", _PROFILE_FEMALE),
    ("24°C 흐림 / 운동", 24, "흐림", "운동", _PROFILE_MALE),
    ("31°C 폭염 / 여행", 31, "폭염", "여행", _PROFILE_FEMALE),
    ("18°C 맑음 / 면접 (체형 정보 없음)", 18, "맑음", "면접", None),
]

# (id, 카테고리, 색상, 이름)
CLOSET = [
    ("id-top-1", "TOP", "화이트", "화이트 옥스포드 셔츠"),
    ("id-top-2", "TOP", "그레이", "그레이 니트 스웨터"),
    ("id-top-3", "TOP", "블랙", "블랙 반팔 티셔츠"),
    ("id-top-4", "TOP", "네이비", "네이비 맨투맨"),
    ("id-bottom-1", "BOTTOM", "베이지", "베이지 치노 팬츠"),
    ("id-bottom-2", "BOTTOM", "블루", "연청 와이드 데님"),
    ("id-bottom-3", "BOTTOM", "블랙", "블랙 슬랙스"),
    ("id-outer-1", "OUTER", "카멜", "카멜 롱 코트"),
    ("id-outer-2", "OUTER", "블랙", "블랙 패딩 점퍼"),
    ("id-outer-3", "OUTER", "베이지", "베이지 트렌치 코트"),
    ("id-shoes-1", "SHOES", "화이트", "화이트 스니커즈"),
    ("id-shoes-2", "SHOES", "블랙", "블랙 로퍼"),
]
_CLOSET_NAMES = {c[0]: c[3] for c in CLOSET}


def _closet_items() -> list[ClosetItem]:
    return [ClosetItem(id=i, category=cat, color=color, name=name) for i, cat, color, name in CLOSET]


class _ParseFailCounter:
    """recommend_service._parse_json 의 실패 횟수를 센다 (원래 동작은 그대로)."""

    def __init__(self):
        self.count = 0
        self._orig = recommend_service._parse_json

    def __enter__(self):
        def wrapped(content):
            try:
                return self._orig(content)
            except Exception:
                self.count += 1
                raise

        recommend_service._parse_json = wrapped
        return self

    def __exit__(self, *exc):
        recommend_service._parse_json = self._orig


async def _timed(coro_fn):
    """(결과, 초, 파싱실패 횟수, 에러 문자열) 반환."""
    with _ParseFailCounter() as counter:
        start = time.perf_counter()
        try:
            result, error = await coro_fn(), None
        except Exception as e:  # API 에러(모델명 오류, 권한 등)도 표에 남기고 계속 진행
            result, error = None, f"{type(e).__name__}: {e}"
        elapsed = time.perf_counter() - start
    return result, elapsed, counter.count, error


def _status(outfits, parse_fails, error) -> str:
    if error:
        return "ERROR"
    if parse_fails:
        # 재시도 후 복구됐으면 OK(retry), 끝내 실패해 빈 결과면 FAIL
        return "FAIL" if not outfits else f"OK(retry x{parse_fails})"
    return "OK"


def _clip(text: str, full: bool, n: int = 70) -> str:
    return text if full or len(text) <= n else text[: n - 1] + "…"


def _situation_lines(result, full):
    lines = []
    for i, o in enumerate(result.outfits, 1):
        shop = ", ".join(f"{s.site}:{s.search_keyword}" for s in o.shopping_suggestions)
        lines.append(f"    {i}. [{o.style_tag}] {_clip(o.description, full)}" + (f"  🛍 {_clip(shop, full, 50)}" if shop else ""))
    return lines


def _closet_lines(result, full):
    lines = []
    for i, o in enumerate(result.outfits, 1):
        names = " + ".join(_CLOSET_NAMES.get(c, c) for c in o.clothes_ids)
        lines.append(f"    {i}. [{o.style_tag}] {_clip(names, full, 90)}")
    return lines


async def run(models: list[str], only: str | None, full: bool):
    original_model = settings.openai_model
    # rows: (모드, 샘플, 모델, 상태, 초, 개수)
    rows = []
    details = []

    try:
        for model in models:
            settings.openai_model = model
            print(f"\n=== {model} ===", flush=True)

            if only in (None, "situation"):
                for label, temp, cond, situation, profile in SAMPLES:
                    req = SituationRecommendRequest(
                        weather=WeatherInfo(temperature=temp, condition=cond), situation=situation, body_profile=profile
                    )
                    res, sec, fails, err = await _timed(lambda: recommend_service.recommend_outfit_by_situation(req))
                    n = len(res.outfits) if res else 0
                    rows.append(("상황", label, model, _status(n and res.outfits, fails, err), sec, n))
                    details.append((model, "상황", label, err, _situation_lines(res, full) if res else []))
                    print(f"  상황 | {label}: {rows[-1][3]} {sec:.1f}s", flush=True)

            if only in (None, "closet"):
                for label, temp, cond, situation, profile in SAMPLES:
                    req = ClosetRecommendRequest(
                        weather=WeatherInfo(temperature=temp, condition=cond),
                        situation=situation,
                        body_profile=profile,
                        clothes=_closet_items(),
                    )
                    res, sec, fails, err = await _timed(lambda: recommend_service.recommend_outfit_by_closet(req))
                    n = len(res.outfits) if res else 0
                    rows.append(("옷장", label, model, _status(n and res.outfits, fails, err), sec, n))
                    details.append((model, "옷장", label, err, _closet_lines(res, full) if res else []))
                    print(f"  옷장 | {label}: {rows[-1][3]} {sec:.1f}s", flush=True)
    finally:
        settings.openai_model = original_model

    # --- 요약 표 ---
    print("\n\n## 요약 (모드 / 샘플 / 모델 / 상태 / 시간 / 코디 수)")
    header = f"{'모드':<4} {'샘플':<26} {'모델':<16} {'상태':<14} {'시간(s)':>8} {'코디수':>5}"
    print(header)
    print("-" * len(header))
    for mode, label, model, status, sec, n in rows:
        print(f"{mode:<4} {label:<26} {model:<16} {status:<14} {sec:>8.1f} {n:>5}")

    print("\n## 모델별 평균 시간 / 실패")
    for model in models:
        mine = [r for r in rows if r[2] == model]
        if not mine:
            continue
        avg = sum(r[4] for r in mine) / len(mine)
        bad = sum(1 for r in mine if r[3] in ("FAIL", "ERROR"))
        retried = sum(1 for r in mine if r[3].startswith("OK(retry"))
        print(f"{model:<16} 평균 {avg:.1f}s | 호출 {len(mine)}회 | 실패 {bad} | 재시도 후 성공 {retried}")

    # --- 추천 결과 ---
    print("\n## 추천 결과")
    for label in dict.fromkeys(d[2] for d in details):
        for mode in ("상황", "옷장"):
            block = [d for d in details if d[2] == label and d[1] == mode]
            if not block:
                continue
            print(f"\n[{mode}] {label}")
            for model, _, _, err, lines in block:
                print(f"  - {model}")
                if err:
                    print(f"    ERROR {err}")
                elif not lines:
                    print("    (추천 결과 없음)")
                else:
                    print("\n".join(lines))


def main():
    parser = argparse.ArgumentParser(description="코디 추천 모델 비교")
    parser.add_argument("--models", nargs="+", default=DEFAULT_MODELS, help="비교할 모델명 목록")
    parser.add_argument("--only", choices=["situation", "closet"], help="한쪽 추천만 실행")
    parser.add_argument("--full", action="store_true", help="코디 설명을 자르지 않고 출력")
    args = parser.parse_args()
    asyncio.run(run(args.models, args.only, args.full))


if __name__ == "__main__":
    main()
