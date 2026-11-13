"""Transfer service tests independent of HTTP."""

from uuid import uuid4

import pytest
from tests.fakes import FakeClipboardAdapter

from app.core.errors import ClipboardUnavailableError, PayloadTooLargeError
from app.core.transfer import TransferService
from app.models.clip import MAX_TEXT_BYTES, ClipKind, ClipRequest, Origin, TransferResult


def _request(text: str, message_id=None) -> ClipRequest:
    return ClipRequest(
        message_id=message_id or uuid4(),
        origin=Origin.android,
        kind=ClipKind.text,
        text=text,
    )


def test_put_clip_writes_and_get_returns_mac_origin() -> None:
    clipboard = FakeClipboardAdapter()
    service = TransferService(clipboard)
    accepted = service.put_clip(_request("hello"))
    assert accepted.result is TransferResult.clipboard_updated
    payload = service.get_clip()
    assert payload is not None
    assert payload.text == "hello"
    assert payload.origin is Origin.mac
    assert payload.message_id != accepted.message_id


def test_failed_write_is_not_recorded_as_success() -> None:
    clipboard = FakeClipboardAdapter(fail=True)
    service = TransferService(clipboard)
    request = _request("nope")
    with pytest.raises(ClipboardUnavailableError):
        service.put_clip(request)
    clipboard.fail = False
    accepted = service.put_clip(request)
    assert accepted.result is TransferResult.clipboard_updated
    assert clipboard.write_count == 1


def test_oversize_rejected_before_write() -> None:
    clipboard = FakeClipboardAdapter()
    service = TransferService(clipboard)
    with pytest.raises(PayloadTooLargeError):
        service.put_clip(_request("a" * (MAX_TEXT_BYTES + 1)))
    assert clipboard.write_count == 0
