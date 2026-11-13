"""Private interface address detection for the LAN profile."""

from __future__ import annotations

import ipaddress
import socket

from app.config import is_private_or_loopback_host, is_unspecified_host


def detect_private_ipv4() -> str:
    """Return a private IPv4 used for the default route, or the first private NIC."""
    routed = _routed_ipv4()
    if routed is not None and is_private_or_loopback_host(routed) and not _is_loopback(routed):
        return routed
    for candidate in _listed_ipv4():
        if is_private_or_loopback_host(candidate) and not _is_loopback(candidate):
            return candidate
    if routed is not None and _is_loopback(routed):
        return routed
    return "127.0.0.1"


def advertised_host(bind_host: str) -> str:
    """Host written into a QR payload. Never advertise 0.0.0.0."""
    if is_unspecified_host(bind_host):
        return detect_private_ipv4()
    return bind_host


def _routed_ipv4() -> str | None:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("1.1.1.1", 80))
        host, _port = probe.getsockname()
        return str(host)
    except OSError:
        return None
    finally:
        probe.close()


def _listed_ipv4() -> list[str]:
    found: list[str] = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            found.append(str(info[4][0]))
    except OSError:
        return found
    return found


def _is_loopback(host: str) -> bool:
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return host in {"localhost"}
