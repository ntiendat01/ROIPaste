"""Application settings for the Mac agent."""

from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    """Loopback bind settings. Clipboard endpoints are added in a later milestone."""

    host: str = "127.0.0.1"
    port: int = 5000
