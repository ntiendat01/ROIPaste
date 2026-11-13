"""Clipboard transfer service: validation, idempotency, and writes."""

from __future__ import annotations

import hashlib
import threading
from uuid import uuid4

from app.core.clipboard import ClipboardAdapter
from app.core.duplicates import DuplicateFilter
from app.core.logging import log_event
from app.models.clip import (
    ClipAccepted,
    ClipKind,
    ClipPayload,
    ClipRequest,
    Origin,
    TransferResult,
    ensure_text_within_limit,
    utf8_size,
)


class TransferService:
    """routes -> TransferService -> ClipboardAdapter. Auth happens before this."""

    def __init__(
        self,
        clipboard: ClipboardAdapter,
        duplicates: DuplicateFilter | None = None,
    ) -> None:
        self._clipboard = clipboard
        self._duplicates = duplicates or DuplicateFilter()
        self._lock = threading.Lock()

    def put_clip(self, request: ClipRequest) -> ClipAccepted:
        ensure_text_within_limit(request.text)
        size = utf8_size(request.text)
        with self._lock:
            if self._duplicates.has_message_id(request.message_id):
                log_event("clip_duplicate", byte_count=size, message_id=request.message_id)
                return ClipAccepted(
                    accepted=True,
                    message_id=request.message_id,
                    result=TransferResult.duplicate,
                )
            self._clipboard.write_text(request.text)
            self._duplicates.remember_message_id(request.message_id)
            digest = hashlib.sha256(request.text.encode("utf-8")).hexdigest()
            self._duplicates.remember_digest(digest, request.origin.value)
            log_event("clip_write", byte_count=size, message_id=request.message_id)
            return ClipAccepted(
                accepted=True,
                message_id=request.message_id,
                result=TransferResult.clipboard_updated,
            )

    def get_clip(self) -> ClipPayload | None:
        text = self._clipboard.read_text()
        if text is None:
            log_event("clip_empty")
            return None
        payload = ClipPayload(
            message_id=uuid4(),
            origin=Origin.mac,
            kind=ClipKind.text,
            text=text,
        )
        log_event("clip_read", byte_count=utf8_size(text), message_id=payload.message_id)
        return payload
