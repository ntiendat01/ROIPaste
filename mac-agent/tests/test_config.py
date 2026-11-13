"""Bind-address and LAN TLS policy tests."""

import pytest

from app.config import Settings, is_loopback_host
from app.core.errors import InsecureLanConfigError, InvalidBindAddressError


def test_rejects_unspecified_ipv4() -> None:
    with pytest.raises(InvalidBindAddressError, match="non-loopback"):
        Settings(host="0.0.0.0")


def test_rejects_unspecified_ipv6() -> None:
    with pytest.raises(InvalidBindAddressError, match="non-loopback"):
        Settings(host="::")


def test_rejects_lan_address() -> None:
    with pytest.raises(InvalidBindAddressError):
        Settings(host="192.168.1.10")


def test_accepts_loopback_hosts() -> None:
    assert Settings(host="127.0.0.1").host == "127.0.0.1"
    assert Settings(host="localhost").host == "localhost"
    assert Settings(host="::1").host == "::1"
    assert is_loopback_host("127.0.0.1")


def test_lan_http_is_rejected() -> None:
    with pytest.raises(InsecureLanConfigError, match="HTTPS"):
        Settings(host="192.168.1.10", profile="lan", tls_enabled=False)


def test_lan_https_private_ip_is_allowed() -> None:
    settings = Settings(host="192.168.1.10", port=5000, profile="lan", tls_enabled=True)
    assert settings.transport == "lan"
    assert settings.tls_enabled is True


def test_lan_https_loopback_is_allowed_for_tests() -> None:
    settings = Settings(host="127.0.0.1", profile="lan", tls_enabled=True)
    assert settings.host == "127.0.0.1"


def test_lan_unspecified_requires_explicit_flag() -> None:
    with pytest.raises(InvalidBindAddressError, match="bind-any"):
        Settings(host="0.0.0.0", profile="lan", tls_enabled=True)
    settings = Settings(
        host="0.0.0.0",
        profile="lan",
        tls_enabled=True,
        allow_unspecified_bind=True,
    )
    assert settings.allow_unspecified_bind is True


def test_lan_public_ip_rejected() -> None:
    with pytest.raises(InvalidBindAddressError, match="private"):
        Settings(host="8.8.8.8", profile="lan", tls_enabled=True)
