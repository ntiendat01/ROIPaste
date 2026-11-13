"""Test doubles for clipboard access."""

from __future__ import annotations

import threading

from app.core.errors import ClipboardUnavailableError


class FakeClipboardAdapter:
    """In-memory clipboard used by unit and API tests."""

    def __init__(self, text: str | None = None, *, fail: bool = False) -> None:
        self._text = text
        self.fail = fail
        self.write_count = 0
        self.read_count = 0
        self._lock = threading.Lock()

    def read_text(self) -> str | None:
        with self._lock:
            self.read_count += 1
            if self.fail:
                raise ClipboardUnavailableError("Clipboard is unavailable")
            return self._text

    def write_text(self, text: str) -> None:
        with self._lock:
            if self.fail:
                raise ClipboardUnavailableError("Clipboard is unavailable")
            self._text = text
            self.write_count += 1
