"""Pydantic models and size limits for /v1/clip."""

from __future__ import annotations

from enum import StrEnum
from uuid import UUID

from pydantic import BaseModel, Field

from app.core.errors import PayloadTooLargeError

MAX_TEXT_BYTES = 256 * 1024


class Origin(StrEnum):
    android = "android"
    mac = "mac"


class ClipKind(StrEnum):
    text = "text"


class TransferResult(StrEnum):
    clipboard_updated = "clipboard_updated"
    queued = "queued"
    duplicate = "duplicate"


def utf8_size(text: str) -> int:
    return len(text.encode("utf-8"))


def ensure_text_within_limit(text: str) -> None:
    """Raise PayloadTooLargeError when UTF-8 payload exceeds 256 KiB."""
    if utf8_size(text) > MAX_TEXT_BYTES:
        raise PayloadTooLargeError()


class ClipRequest(BaseModel):
    message_id: UUID
    origin: Origin
    kind: ClipKind
    text: str = Field(..., min_length=0)


class ClipAccepted(BaseModel):
    accepted: bool
    message_id: UUID
    result: TransferResult


class ClipPayload(BaseModel):
    message_id: UUID
    origin: Origin
    kind: ClipKind
    text: str


class ClipNextResponse(BaseModel):
    message_id: UUID
    origin: Origin
    kind: ClipKind
    text: str
    sequence: int
    byte_count: int

