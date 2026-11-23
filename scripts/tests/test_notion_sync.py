from typing import Any

import notion_sync


def test_archives_old_child_page_only(monkeypatch) -> None:
    calls: list[tuple[str, str, Any]] = []

    def fake(method: str, path: str, token: str, body: Any = None) -> Any:
        calls.append((method, path, body))
        if method == "GET":
            return {
                "results": [
                    {"type": "child_page", "id": "old", "child_page": {"title": notion_sync.OLD_CHILD_TITLE}},
                    {"type": "child_page", "id": "keep", "child_page": {"title": "메모"}},
                ],
                "has_more": False,
            }
        return {}

    monkeypatch.setattr(notion_sync, "call", fake)
    notion_sync.archive_old_child("p", "t")
    assert ("PATCH", "/pages/old", {"archived": True}) in calls
    assert all(path != "/pages/keep" for _, path, _ in calls)


def test_sync_writes_main_page(monkeypatch) -> None:
    seen: list[str] = []
    monkeypatch.setenv("NOTION_TOKEN", "t")
    monkeypatch.setenv("NOTION_PAGE_ID", "main")
    monkeypatch.setattr(notion_sync, "replace_page", lambda pid, tok, blocks: seen.append(pid))
    monkeypatch.setattr(notion_sync, "archive_old_child", lambda pid, tok: None)
    assert notion_sync.sync("## x") == 0
    assert seen == ["main"]


def test_sync_skips_without_env(monkeypatch) -> None:
    monkeypatch.delenv("NOTION_TOKEN", raising=False)
    monkeypatch.delenv("NOTION_PAGE_ID", raising=False)
    assert notion_sync.sync("## x") == 0
