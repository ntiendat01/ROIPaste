"""Application settings for the Mac agent."""

from __future__ import annotations

import ipaddress
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

from app.core.errors import InsecureLanConfigError, InvalidBindAddressError

LOOPBACK_HOSTS = frozenset({"127.0.0.1", "::1", "localhost"})
Profile = Literal["usb", "lan"]


def is_loopback_host(host: str) -> bool:
    if host in LOOPBACK_HOSTS:
        return True
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return False


def is_unspecified_host(host: str) -> bool:
    if host in {"0.0.0.0", "::", "*"}:
        return True
    try:
        return ipaddress.ip_address(host).is_unspecified
    except ValueError:
        return False


def is_private_or_loopback_host(host: str) -> bool:
    if is_loopback_host(host):
        return True
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        return False
    return bool(address.is_private and not address.is_multicast)


def default_config_dir() -> Path:
    override = os.environ.get("ROIPASTE_CONFIG_DIR")
    if override:
        return Path(override)
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "ROIPaste"
    xdg = os.environ.get("XDG_CONFIG_HOME")
    if xdg:
        return Path(xdg) / "roipaste"
    return Path.home() / ".config" / "roipaste"


@dataclass(frozen=True)
class Settings:
    """USB binds loopback HTTP. LAN requires HTTPS and a private (or explicit) bind."""

    host: str = "127.0.0.1"
    port: int = 5000
    clipboard_timeout_seconds: float = 5.0
    profile: Profile = "usb"
    tls_enabled: bool = False
    allow_unspecified_bind: bool = False
    pairing_open: bool = False
    config_dir: Path | None = None

    def __post_init__(self) -> None:
        if not (1 <= self.port <= 65535):
            raise InvalidBindAddressError(f"Invalid port {self.port}")
        if self.profile == "usb":
            self._validate_usb()
            return
        if self.profile == "lan":
            self._validate_lan()
            return
        raise InvalidBindAddressError(f"Unknown profile {self.profile!r}")

    def _validate_usb(self) -> None:
        if not is_loopback_host(self.host):
            raise InvalidBindAddressError(
                f"Refusing to bind non-loopback host {self.host!r}; use 127.0.0.1"
            )

    def _validate_lan(self) -> None:
        if not self.tls_enabled:
            raise InsecureLanConfigError(
                "LAN profile requires HTTPS; refusing to serve clipboard over HTTP"
            )
        if is_unspecified_host(self.host):
            if not self.allow_unspecified_bind:
                raise InvalidBindAddressError(
                    "Refusing to bind 0.0.0.0 or :: silently; pass --bind-any to request it"
                )
            return
        if not is_private_or_loopback_host(self.host):
            raise InvalidBindAddressError(
                f"LAN bind host {self.host!r} must be a private or loopback address"
            )

    @property
    def resolved_config_dir(self) -> Path:
        return self.config_dir if self.config_dir is not None else default_config_dir()

    @property
    def transport(self) -> Literal["usb", "lan"]:
        return self.profile
