"""Tests for the bounded LRU duplicate filter."""

from uuid import uuid4

from app.core.duplicates import DuplicateFilter


def test_message_id_is_remembered() -> None:
    filt = DuplicateFilter(max_entries=2)
    first = uuid4()
    assert not filt.has_message_id(first)
    filt.remember_message_id(first)
    assert filt.has_message_id(first)


def test_lru_evicts_oldest_message_id() -> None:
    filt = DuplicateFilter(max_entries=2)
    a, b, c = uuid4(), uuid4(), uuid4()
    filt.remember_message_id(a)
    filt.remember_message_id(b)
    filt.remember_message_id(c)
    assert not filt.has_message_id(a)
    assert filt.has_message_id(b)
    assert filt.has_message_id(c)


def test_digest_origin_is_tracked() -> None:
    filt = DuplicateFilter()
    filt.remember_digest("abc", "android")
    assert filt.has_digest("abc", "android")
    assert not filt.has_digest("abc", "mac")
