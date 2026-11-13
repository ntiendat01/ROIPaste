"""Holds at most the latest watcher item and wakes long-poll waiters."""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass

from app.models.clip import ClipPayload

MAX_WAIT_SECONDS = 30.0
WAIT_SLICE_SECONDS = 0.05


@dataclass(frozen=True)
class InboxItem:
    sequence: int
    payload: ClipPayload


class ClipInbox:
    """Single-slot inbox. A new publish replaces any undelivered item."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._changed = threading.Condition(self._lock)
        self._latest: InboxItem | None = None
        self._sequence = 0
        self._waiters = 0

    @property
    def sequence(self) -> int:
        with self._lock:
            return self._sequence

    @property
    def waiter_count(self) -> int:
        with self._lock:
            return self._waiters

    def latest(self) -> InboxItem | None:
        with self._lock:
            return self._latest

    def connected(self) -> bool:
        return self.waiter_count > 0

    def publish(self, payload: ClipPayload) -> InboxItem:
        with self._changed:
            self._sequence += 1
            item = InboxItem(sequence=self._sequence, payload=payload)
            self._latest = item
            self._changed.notify_all()
            return item

    def wait_next(
        self,
        since: int,
        timeout: float,
        cancelled: threading.Event | None = None,
    ) -> InboxItem | None:
        timeout = min(max(timeout, 0.0), MAX_WAIT_SECONDS)
        deadline = time.monotonic() + timeout
        with self._changed:
            self._waiters += 1
            try:
                while True:
                    if self._latest is not None and self._latest.sequence > since:
                        return self._latest
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        return None
                    if cancelled is not None and cancelled.is_set():
                        return None
                    self._changed.wait(timeout=min(remaining, WAIT_SLICE_SECONDS))
            finally:
                self._waiters -= 1

    def wake(self) -> None:
        with self._changed:
            self._changed.notify_all()
