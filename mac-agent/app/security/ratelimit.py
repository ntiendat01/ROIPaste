"""Bounded in-memory rate limiter for pairing endpoints."""

from __future__ import annotations

from collections import defaultdict, deque
from collections.abc import Callable

from app.core.errors import RateLimitedError

DEFAULT_LIMIT = 5
DEFAULT_WINDOW_SECONDS = 60.0


class RateLimiter:
    def __init__(
        self,
        *,
        limit: int = DEFAULT_LIMIT,
        window_seconds: float = DEFAULT_WINDOW_SECONDS,
        clock: Callable[[], float],
    ) -> None:
        self._limit = limit
        self._window = window_seconds
        self._clock = clock
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    def check(self, key: str) -> None:
        now = self._clock()
        bucket = self._hits[key]
        cutoff = now - self._window
        while bucket and bucket[0] <= cutoff:
            bucket.popleft()
        if len(bucket) >= self._limit:
            raise RateLimitedError()
        bucket.append(now)
