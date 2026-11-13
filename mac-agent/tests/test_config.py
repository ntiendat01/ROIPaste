"""Bind-address policy tests."""

import pytest

from app.config import Settings, is_loopback_host
from app.core.errors import InvalidBindAddressError


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
