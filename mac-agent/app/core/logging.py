"""Structured metadata logging. Never log clipboard text, tokens, or bodies."""

from __future__ import annotations

import logging
from uuid import UUID

logger = logging.getLogger("roipaste")


def truncate_id(message_id: UUID) -> str:
    return str(message_id).split("-", 1)[0]


def log_event(
    event: str,
    *,
    byte_count: int | None = None,
    message_id: UUID | None = None,
) -> None:
    parts = [f"event={event}"]
    if byte_count is not None:
        parts.append(f"bytes={byte_count}")
    if message_id is not None:
        parts.append(f"id={truncate_id(message_id)}")
    logger.info(" ".join(parts))
