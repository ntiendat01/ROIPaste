"""Opt-in Mac clipboard watcher. Polls a text hash; never logs content."""

from __future__ import annotations

import hashlib
import threading
from collections.abc import Callable
from uuid import uuid4

from app.core.clipboard import ClipboardAdapter
from app.core.duplicates import DuplicateFilter
from app.core.errors import ClipboardUnavailableError, PayloadTooLargeError
from app.core.inbox import ClipInbox, InboxItem
from app.core.logging import log_event
from app.models.clip import ClipKind, ClipPayload, Origin, ensure_text_within_limit, utf8_size

DEFAULT_INTERVAL_SECONDS = 0.5
ANDROID_ORIGIN = Origin.android.value


class ClipboardWatcher:
    """Poll pbpaste (or a fake adapter) every 0.5–1 s when enabled."""

    def __init__(
        self,
        clipboard: ClipboardAdapter,
        inbox: ClipInbox,
        duplicates: DuplicateFilter,
        *,
        interval_seconds: float = DEFAULT_INTERVAL_SECONDS,
        is_paused: Callable[[], bool] | None = None,
        enabled: bool = False,
    ) -> None:
        self._clipboard = clipboard
        self._inbox = inbox
        self._duplicates = duplicates
        self._interval = min(max(interval_seconds, 0.5), 1.0)
        self._is_paused = is_paused or (lambda: False)
        self._enabled = enabled
        self._paused = False
        self._stop = threading.Event()
        self._wake = threading.Event()
        self._thread: threading.Thread | None = None
        self._last_digest: str | None = None
        self._baseline_taken = False

    @property
    def enabled(self) -> bool:
        return self._enabled

    @property
    def paused(self) -> bool:
        return self._paused or self._is_paused()

    @property
    def interval_seconds(self) -> float:
        return self._interval

    def set_enabled(self, enabled: bool) -> None:
        self._enabled = enabled
        if enabled:
            self._baseline_taken = False
            self._last_digest = None
            self.start()
        self._wake.set()

    def set_paused(self, paused: bool) -> None:
        self._paused = paused
        self._inbox.wake()

    def start(self) -> None:
        if self._thread is not None and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="roipaste-watcher", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._wake.set()
        self._inbox.wake()
        thread = self._thread
        if thread is not None and thread.is_alive():
            thread.join(timeout=2.0)
        self._thread = None

    def poll_once(self) -> InboxItem | None:
        """Run one poll iteration. Used by unit tests and the worker loop."""
        if not self._enabled or self.paused:
            return None
        try:
            text = self._clipboard.read_text()
        except ClipboardUnavailableError:
            log_event("watcher_clipboard_unavailable")
            return None
        if text is None:
            self._last_digest = None
            self._baseline_taken = True
            return None
        try:
            ensure_text_within_limit(text)
        except PayloadTooLargeError:
            log_event("watcher_skipped_oversize", byte_count=utf8_size(text))
            self._baseline_taken = True
            return None
        digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
        if not self._baseline_taken:
            self._last_digest = digest
            self._baseline_taken = True
            log_event("watcher_baseline", byte_count=utf8_size(text))
            return None
        if digest == self._last_digest:
            return None
        self._last_digest = digest
        if self._duplicates.has_digest(digest, ANDROID_ORIGIN):
            log_event("watcher_suppressed_echo", byte_count=utf8_size(text))
            return None
        payload = ClipPayload(
            message_id=uuid4(),
            origin=Origin.mac,
            kind=ClipKind.text,
            text=text,
        )
        self._duplicates.remember_message_id(payload.message_id)
        self._duplicates.remember_digest(digest, Origin.mac.value)
        item = self._inbox.publish(payload)
        log_event("watcher_published", byte_count=utf8_size(text), message_id=payload.message_id)
        return item

    def _run(self) -> None:
        while not self._stop.is_set():
            if self._enabled and not self.paused:
                self.poll_once()
            self._wake.wait(self._interval)
            self._wake.clear()
