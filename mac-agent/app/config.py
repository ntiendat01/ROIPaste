"""Application settings for the Mac agent."""

from __future__ import annotations

import ipaddress
from dataclasses import dataclass

from app.core.errors import InvalidBindAddressError

LOOPBACK_HOSTS = frozenset({"127.0.0.1", "::1", "localhost"})


def is_loopback_host(host: str) -> bool:
    if host in LOOPBACK_HOSTS:
        return True
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return False


@dataclass(frozen=True)
class Settings:
    """Loopback bind settings. Non-loopback hosts are rejected (fail closed)."""

    host: str = "127.0.0.1"
    port: int = 5000
    clipboard_timeout_seconds: float = 5.0

    def __post_init__(self) -> None:
        if not is_loopback_host(self.host):
            raise InvalidBindAddressError(
                f"Refusing to bind non-loopback host {self.host!r}; use 127.0.0.1"
            )
        if not (1 <= self.port <= 65535):
            raise InvalidBindAddressError(f"Invalid port {self.port}")
