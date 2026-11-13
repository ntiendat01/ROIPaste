"""Explicit error classes for the Mac agent. Messages must stay non-sensitive."""

from __future__ import annotations


class AppError(Exception):
    """Base error that maps to the protocol error envelope."""

    def __init__(
        self,
        code: str,
        message: str,
        status_code: int,
        headers: dict[str, str] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
        self.headers = headers or {}


class PayloadTooLargeError(AppError):
    def __init__(self) -> None:
        super().__init__(
            code="PAYLOAD_TOO_LARGE",
            message="Text exceeds the 256 KiB limit",
            status_code=413,
        )


class UnauthenticatedError(AppError):
    def __init__(self) -> None:
        super().__init__(
            code="UNAUTHENTICATED",
            message="Valid bearer token required",
            status_code=401,
            headers={"WWW-Authenticate": "Bearer"},
        )


class ClipboardUnavailableError(AppError):
    def __init__(self, message: str = "Clipboard is unavailable") -> None:
        super().__init__(
            code="CLIPBOARD_UNAVAILABLE",
            message=message,
            status_code=503,
        )


class InvalidBindAddressError(ValueError):
    """Raised when the server would bind a non-loopback address."""
