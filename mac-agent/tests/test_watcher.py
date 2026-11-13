"""Clipboard watcher: change detection, echo suppression, pause, and inbox."""

from __future__ import annotations

from uuid import uuid4

from tests.fakes import FakeClipboardAdapter

from app.core.duplicates import DuplicateFilter
from app.core.inbox import ClipInbox
from app.core.transfer import TransferService
from app.core.watcher import ClipboardWatcher
from app.models.clip import ClipKind, ClipRequest, Origin


def _watcher(
    clipboard: FakeClipboardAdapter,
    inbox: ClipInbox | None = None,
    duplicates: DuplicateFilter | None = None,
    *,
    enabled: bool = True,
) -> tuple[ClipboardWatcher, ClipInbox, DuplicateFilter]:
    box = inbox or ClipInbox()
    filt = duplicates or DuplicateFilter()
    watcher = ClipboardWatcher(clipboard, box, filt, enabled=enabled)
    return watcher, box, filt


def test_detects_clipboard_change_after_baseline() -> None:
    clipboard = FakeClipboardAdapter("first")
    watcher, inbox, _filt = _watcher(clipboard)
    assert watcher.poll_once() is None
    clipboard.write_text("second")
    item = watcher.poll_once()
    assert item is not None
    assert item.sequence == 1
    assert item.payload.text == "second"
    assert inbox.latest() is item


def test_ignores_own_android_writes_no_echo() -> None:
    clipboard = FakeClipboardAdapter("seed")
    duplicates = DuplicateFilter()
    service = TransferService(clipboard, duplicates)
    watcher, inbox, _filt = _watcher(clipboard, duplicates=duplicates)
    assert watcher.poll_once() is None
    request = ClipRequest(
        message_id=uuid4(),
        origin=Origin.android,
        kind=ClipKind.text,
        text="from phone",
    )
    service.put_clip(request)
    assert watcher.poll_once() is None
    assert inbox.latest() is None


def test_back_to_back_distinct_clips() -> None:
    clipboard = FakeClipboardAdapter("a")
    watcher, _inbox, _filt = _watcher(clipboard)
    assert watcher.poll_once() is None
    clipboard.write_text("b")
    first = watcher.poll_once()
    clipboard.write_text("c")
    second = watcher.poll_once()
    assert first is not None and second is not None
    assert first.payload.text == "b"
    assert second.payload.text == "c"
    assert second.sequence == first.sequence + 1


def test_competing_clips_and_no_delivery_keep_only_latest() -> None:
    clipboard = FakeClipboardAdapter("start")
    watcher, inbox, _filt = _watcher(clipboard)
    watcher.poll_once()
    clipboard.write_text("one")
    watcher.poll_once()
    clipboard.write_text("two")
    watcher.poll_once()
    latest = inbox.latest()
    assert latest is not None
    assert latest.payload.text == "two"
    assert inbox.wait_next(since=0, timeout=0) is latest
    assert inbox.wait_next(since=latest.sequence, timeout=0) is None


def test_pause_blocks_publishing() -> None:
    clipboard = FakeClipboardAdapter("start")
    watcher, inbox, _filt = _watcher(clipboard)
    watcher.poll_once()
    watcher.set_paused(True)
    clipboard.write_text("paused-text")
    assert watcher.poll_once() is None
    assert inbox.latest() is None
    watcher.set_paused(False)
    # last_digest is still the baseline; the paused change should be seen now
    item = watcher.poll_once()
    assert item is not None
    assert item.payload.text == "paused-text"


def test_disabled_watcher_does_not_publish() -> None:
    clipboard = FakeClipboardAdapter("start")
    watcher, inbox, _filt = _watcher(clipboard, enabled=False)
    clipboard.write_text("ignored")
    assert watcher.poll_once() is None
    assert inbox.latest() is None


def test_cancellation_wakes_waiter() -> None:
    import threading

    inbox = ClipInbox()
    cancelled = threading.Event()
    result: list[object] = []

    def wait() -> None:
        result.append(inbox.wait_next(since=0, timeout=5, cancelled=cancelled))

    thread = threading.Thread(target=wait)
    thread.start()
    cancelled.set()
    inbox.wake()
    thread.join(timeout=2)
    assert not thread.is_alive()
    assert result == [None]
