from typing import Any

import notion_sync


def test_reuses_existing_child_page(monkeypatch) -> None:
    calls: list[tuple[str, str]] = []

    def fake(method: str, path: str, token: str, body: Any = None) -> Any:
        calls.append((method, path))
        return {"results": [{"type": "child_page", "id": "c1", "child_page": {"title": notion_sync.CHILD_TITLE}}],
                "has_more": False}

    monkeypatch.setattr(notion_sync, "call", fake)
    assert notion_sync.child_page("p", "t") == "c1"
    assert all(m == "GET" for m, _ in calls)


def test_creates_child_page_when_missing(monkeypatch) -> None:
    def fake(method: str, path: str, token: str, body: Any = None) -> Any:
        if method == "GET":
            return {"results": [{"type": "paragraph", "id": "x"}], "has_more": False}
        assert body["parent"] == {"page_id": "p"}
        return {"id": "new"}

    monkeypatch.setattr(notion_sync, "call", fake)
    assert notion_sync.child_page("p", "t") == "new"


def test_sync_skips_without_env(monkeypatch) -> None:
    monkeypatch.delenv("NOTION_TOKEN", raising=False)
    monkeypatch.delenv("NOTION_PAGE_ID", raising=False)
    assert notion_sync.sync("## x") == 0
