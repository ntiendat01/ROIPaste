"""Bounded in-memory LRU caches for message IDs and content digest+origin."""

from __future__ import annotations

from collections import OrderedDict
from uuid import UUID

DEFAULT_CACHE_SIZE = 256


class DuplicateFilter:
    """Records recently processed message IDs and SHA-256 digest+origin pairs."""

    def __init__(self, max_entries: int = DEFAULT_CACHE_SIZE) -> None:
        if max_entries < 1:
            raise ValueError("max_entries must be at least 1")
        self._max_entries = max_entries
        self._message_ids: OrderedDict[UUID, None] = OrderedDict()
        self._digests: OrderedDict[tuple[str, str], None] = OrderedDict()

    def has_message_id(self, message_id: UUID) -> bool:
        return message_id in self._message_ids

    def remember_message_id(self, message_id: UUID) -> None:
        _touch(self._message_ids, message_id, self._max_entries)

    def has_digest(self, digest: str, origin: str) -> bool:
        return (digest, origin) in self._digests

    def remember_digest(self, digest: str, origin: str) -> None:
        _touch(self._digests, (digest, origin), self._max_entries)


def _touch[T](cache: OrderedDict[T, None], key: T, max_entries: int) -> None:
    if key in cache:
        cache.move_to_end(key)
        return
    cache[key] = None
    while len(cache) > max_entries:
        cache.popitem(last=False)
