"""FR-15 개인정보처리방침에 새 기능 반영.

/privacy는 지금 구조(1항 수집 정보 표 구분·항목·목적, 2항 보유 기간 목록, 5항 국외 이전의 OpenAI 표)를 유지한 채
새 항목을 더한다고 본다. 문장은 가정하지 않고, 같은 표 행·목록 항목 안에 필요한 낱말이 함께 있는지만 본다.
'30분'은 이미 날씨 캐시 문장에 있으므로 '살까 말까'와 같은 항목 안에 있어야 한다.
"""

from __future__ import annotations

import html
import re

import httpx
import pytest


def fetch_privacy(stack) -> str:
    res = httpx.get(f"{stack.api}/privacy", timeout=10)
    assert res.status_code == 200, res.text
    assert res.headers["content-type"].replace(" ", "").lower().startswith("text/html")
    return res.text


def text_of(fragment: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", fragment))).strip()


def blocks(page: str, tag: str) -> list[str]:
    return [m for m in re.findall(rf"<{tag}\b[^>]*>(.*?)</{tag}>", page, flags=re.S | re.I)]


def cells(row: str) -> list[str]:
    return [text_of(c) for c in re.findall(r"<t[dh]\b[^>]*>(.*?)</t[dh]>", row, flags=re.S | re.I)]


def section(page: str, heading: str) -> str:
    """<h2>에 heading이 든 절의 HTML (다음 <h2> 전까지)."""
    parts = re.split(r"(?=<h2\b)", page, flags=re.I)
    found = [p for p in parts if re.match(rf"<h2\b[^>]*>[^<]*{heading}", p, flags=re.I)]
    assert found, f"'{heading}' 절이 없다"
    return found[0]


def item_blocks(page: str) -> list[str]:
    return [text_of(b) for tag in ("li", "tr", "p") for b in blocks(page, tag)]


@pytest.mark.fr("FR-15")
def test_privacy_has_purchase_check_photo_retention_and_record_items(stack) -> None:
    page = fetch_privacy(stack)
    assert any("살까 말까" in b and "30분" in b for b in item_blocks(page)), "살까 말까 사진 30분 보관 문구가 없다"

    rows = [cells(r) for r in blocks(section(page, "수집하는 정보"), "tr")]
    purchase_rows = [" ".join(r) for r in rows if any("살까 말까" in c for c in r)]
    assert purchase_rows, "수집 정보 표에 살까 말까 행이 없다"
    joined = " ".join(purchase_rows)
    for word in ("이름", "카테고리", "색상", "가격", "판정"):
        assert word in joined, (word, joined)


@pytest.mark.fr("FR-15")
@pytest.mark.parametrize("subject", ["착용 기록", "이벤트"])
def test_privacy_has_wear_and_event_items_purpose_and_retention(stack, subject: str) -> None:
    page = fetch_privacy(stack)
    rows = [cells(r) for r in blocks(section(page, "수집하는 정보"), "tr")]
    matched = [r for r in rows if r and subject in r[0]]
    assert matched, f"수집 정보 표에 '{subject}' 행이 없다"
    # 구분·항목·목적 세 칸이 모두 채워져 있다
    assert all(len(r) >= 3 and all(c for c in r[:3]) for r in matched), matched

    retention = [text_of(b) for b in blocks(section(page, "보유 기간"), "li")]
    assert any(subject in b for b in retention), f"보유 기간 절에 '{subject}'이 없다"
    if subject == "이벤트":
        assert any("이벤트" in b and "1년" in b for b in retention), retention


@pytest.mark.fr("FR-15")
def test_openai_overseas_transfer_items_include_embedding_text(stack) -> None:
    page = fetch_privacy(stack)
    overseas = section(page, "국외 이전")
    m = re.search(r"<h3\b[^>]*>\s*OpenAI\s*</h3>(.*?)</table>", overseas, flags=re.S | re.I)
    assert m, "국외 이전 절에 OpenAI 표가 없다"
    rows = [cells(r) for r in blocks(m.group(1), "tr")]
    items = [r for r in rows if r and "이전 항목" in r[0]]
    assert items, rows
    assert "임베딩" in " ".join(items[0][1:]), items[0]
