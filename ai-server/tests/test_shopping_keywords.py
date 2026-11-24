"""FR-23: the shopping-mall search keyword prompt survives as a module; the helper route is gone."""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import app
from app.services.shopping_keywords import (
    SHOPPING_SITES,
    SearchKeyword,
    build_search_keyword_prompt,
    parse_search_keywords,
    parse_search_keywords_text,
)


def test_shopping_helper_route_is_removed() -> None:
    client = TestClient(app)
    res = client.post("/ai/shopping/" + "recommend", json={"budget": 100000, "situation": "출근"})
    assert res.status_code == 404


def test_prompt_lists_items_sites_and_rules() -> None:
    prompt = build_search_keyword_prompt(["네이비 슬림핏 셔츠", "블랙 와이드 슬랙스"])
    assert "- 네이비 슬림핏 셔츠" in prompt
    assert "- 블랙 와이드 슬랙스" in prompt
    assert "무신사|지그재그|에이블리|W컨셉" in prompt
    assert "search_keyword" in prompt
    assert "무신사와 지그재그를 우선" in prompt


def test_prompt_is_deterministic() -> None:
    assert build_search_keyword_prompt(["셔츠"]) == build_search_keyword_prompt(["셔츠"])


def test_parse_keeps_valid_and_drops_unknown_site_or_blank() -> None:
    data = {
        "suggestions": [
            {"item": " 네이비 셔츠 ", "site": "무신사", "search_keyword": " 남성 네이비 셔츠 "},
            {"item": "슬랙스", "site": "모르는몰", "search_keyword": "슬랙스"},
            {"item": "니트", "site": "지그재그", "search_keyword": ""},
            "not-a-dict",
        ]
    }
    assert parse_search_keywords(data) == [SearchKeyword("네이비 셔츠", "무신사", "남성 네이비 셔츠")]


def test_parse_handles_missing_suggestions() -> None:
    assert parse_search_keywords({}) == []
    assert parse_search_keywords({"suggestions": None}) == []


def test_parse_text_accepts_fenced_json_and_rejects_garbage() -> None:
    text = '```json\n{"suggestions": [{"item": "셔츠", "site": "W컨셉", "search_keyword": "셔츠"}]}\n```'
    assert parse_search_keywords_text(text) == [SearchKeyword("셔츠", "W컨셉", "셔츠")]
    assert parse_search_keywords_text("not json") == []
    assert parse_search_keywords_text("[1, 2]") == []


def test_sites_are_fixed() -> None:
    assert SHOPPING_SITES == ("무신사", "지그재그", "에이블리", "W컨셉")
