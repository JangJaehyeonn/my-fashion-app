"""FR-23 쇼핑 도우미 제거.

가정 (NOTES.md 참고)
- 지운 뒤 POST /api/shopping/recommend는 인증된 사용자에게도 404다. 백엔드가 AI 서버 /ai/shopping/recommend를 부르지 않는다.
- '진단 기능은 건드리지 않는다': 진단 목록 API(GET /api/diagnosis)는 그대로 200이다.
- '쇼핑 도우미 화면·API 참조'는 저장소 텍스트에 아래 표식이 남아 있지 않은 것으로 본다.
  `shopping/recommend`(백엔드·앱 Retrofit·AI 서버 라우터·k6·README의 경로), `shopping_recommend`(k6 지표 이름),
  `ShoppingRecommend`(요청·응답 클래스), `recommendShopping`(AiServerClient 메서드), `ShoppingApi`(앱 Retrofit 인터페이스).
- 지금의 앱 `ui/shopping/ShoppingScreen`·`ShoppingViewModel`은 쇼핑 도우미가 아니라 하단 '쇼핑' 탭의 쇼핑 홈(살까 말까)이라
  지우지 않는다. 요구사항 detail의 '앱 ui/shopping'은 옛 쇼핑 도우미 화면을 가리킨 것으로 본다.
  옛 기록·요구사항·인수 테스트(docs/devlog.md, docs/AUDIT.md, docs/adr/, docs/requirements/, acceptance*/)는 검사에서 뺀다.
  하단 '쇼핑' 탭과 쇼핑몰 검색어 모듈(FR-25 재사용)은 쇼핑 도우미가 아니므로 이름에 shopping이 들어가도 된다.
"""

from __future__ import annotations

from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[2]

MARKERS = (
    "shopping/recommend",
    "shopping_recommend",
    "ShoppingRecommend",
    "recommendShopping",
    "ShoppingApi",
)

EXCLUDED_DIRS = {
    ".git", ".gradle", ".idea", "build", "node_modules", "__pycache__", ".venv", "venv", ".pytest_cache",
    "acceptance", "acceptance_draft",
}
EXCLUDED_PATHS = ("docs/devlog.md", "docs/AUDIT.md", "docs/adr/", "docs/requirements/")
TEXT_SUFFIXES = {
    ".java", ".kt", ".kts", ".py", ".js", ".ts", ".tsx", ".jsx", ".md", ".yml", ".yaml", ".xml",
    ".json", ".toml", ".gradle", ".properties", ".sql", ".conf", ".sh", ".txt", ".html",
}


def repo_text_files() -> list[Path]:
    files: list[Path] = []
    for path in REPO.rglob("*"):
        rel = path.relative_to(REPO)
        if any(part in EXCLUDED_DIRS for part in rel.parts):
            continue
        if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
            continue
        if any(rel.as_posix().startswith(prefix) for prefix in EXCLUDED_PATHS):
            continue
        files.append(path)
    return files


@pytest.mark.fr("FR-23")
def test_shopping_recommend_api_is_gone(user, fake_ai) -> None:
    res = user.client.post("/api/shopping/recommend", json={"budget": 100000, "situation": "DAILY"})
    assert res.status_code == 404, res.text

    paths = [c["path"] for c in fake_ai.get("/__calls").json()]
    assert "/ai/shopping/recommend" not in paths


@pytest.mark.fr("FR-23")
def test_shopping_recommend_is_404_even_without_body(user) -> None:
    res = user.client.post("/api/shopping/recommend")
    assert res.status_code == 404, res.text


@pytest.mark.fr("FR-23")
def test_diagnosis_still_works(user) -> None:
    res = user.client.get("/api/diagnosis")
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["success"] is True
    assert body["data"] == []


@pytest.mark.fr("FR-23")
def test_other_apis_still_work(user, tiny_jpg) -> None:
    res = user.client.post("/api/clothes", files={"image": ("top_navy_knit.jpg", tiny_jpg, "image/jpeg")})
    assert res.status_code == 201, res.text
    listed = user.client.get("/api/clothes")
    assert listed.status_code == 200, listed.text
    assert len(listed.json()["data"]) == 1


@pytest.mark.fr("FR-23")
@pytest.mark.parametrize("marker", MARKERS)
def test_no_shopping_helper_reference_left(marker: str) -> None:
    hits = [
        path.relative_to(REPO).as_posix()
        for path in repo_text_files()
        if marker in path.read_text(encoding="utf-8", errors="ignore")
    ]
    assert hits == [], f"'{marker}' 참조가 남아 있습니다: {hits}"


@pytest.mark.fr("FR-23")
def test_k6_scripts_do_not_measure_shopping_recommend() -> None:
    scripts = sorted((REPO / "performance").glob("*.js"))
    assert scripts, "performance/의 k6 스크립트를 찾지 못했습니다"
    for script in scripts:
        text = script.read_text(encoding="utf-8")
        assert "/shopping/recommend" not in text, script.name
        assert "shopping_recommend" not in text, script.name


@pytest.mark.fr("FR-23")
def test_shopping_helper_files_are_deleted() -> None:
    gone = [
        "backend/src/main/java/com/fashionapp/domain/shopping/ShoppingController.java",
        "backend/src/main/java/com/fashionapp/domain/shopping/ShoppingService.java",
        "backend/src/main/java/com/fashionapp/domain/shopping/ShoppingRecommendRequest.java",
        "ai-server/app/routers/shopping.py",
        "android/app/src/main/java/com/fashionapp/data/api/ShoppingApi.kt",
        "android/app/src/main/java/com/fashionapp/data/repository/ShoppingRepository.kt",
    ]
    left = [p for p in gone if (REPO / p).exists()]
    assert left == [], f"남아 있는 쇼핑 도우미 파일: {left}"
