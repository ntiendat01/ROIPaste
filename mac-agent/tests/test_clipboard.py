"""Unit tests for MacClipboardAdapter (mocked) and an optional macOS integration test."""

from __future__ import annotations

import subprocess
import sys
from typing import Any

import pytest

from app.core.clipboard import MacClipboardAdapter
from app.core.errors import ClipboardUnavailableError


class _Result:
    def __init__(self, returncode: int, stdout: bytes = b"", stderr: bytes = b"") -> None:
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr


def test_write_uses_pbcopy_without_shell(monkeypatch: pytest.MonkeyPatch) -> None:
    calls: list[tuple[list[str], dict[str, Any]]] = []

    def fake_run(command: list[str], **kwargs: Any) -> _Result:
        calls.append((command, kwargs))
        return _Result(0)

    monkeypatch.setattr(subprocess, "run", fake_run)
    MacClipboardAdapter().write_text("Xin chào")
    assert len(calls) == 1
    command, kwargs = calls[0]
    assert command == ["pbcopy"]
    assert kwargs.get("shell") is False
    assert kwargs["input"] == "Xin chào".encode()
    assert kwargs["env"]["LANG"] == "en_US.UTF-8"
    assert kwargs["env"]["LC_CTYPE"] == "en_US.UTF-8"


def test_read_uses_pbpaste_and_returns_text(monkeypatch: pytest.MonkeyPatch) -> None:
    def fake_run(command: list[str], **kwargs: Any) -> _Result:
        assert command == ["pbpaste"]
        assert kwargs.get("shell") is False
        return _Result(0, stdout=b"hello\nthere")

    monkeypatch.setattr(subprocess, "run", fake_run)
    assert MacClipboardAdapter().read_text() == "hello\nthere"


def test_empty_pbpaste_returns_none(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(subprocess, "run", lambda *a, **k: _Result(0, stdout=b""))
    assert MacClipboardAdapter().read_text() is None


def test_missing_tool_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    def fake_run(*_args: object, **_kwargs: object) -> _Result:
        raise FileNotFoundError("pbpaste")

    monkeypatch.setattr(subprocess, "run", fake_run)
    with pytest.raises(ClipboardUnavailableError):
        MacClipboardAdapter().read_text()


def test_nonzero_exit_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(subprocess, "run", lambda *a, **k: _Result(1))
    with pytest.raises(ClipboardUnavailableError):
        MacClipboardAdapter().write_text("x")


@pytest.mark.skipif(sys.platform != "darwin", reason="Requires macOS pbcopy/pbpaste")
def test_mac_clipboard_round_trip() -> None:
    adapter = MacClipboardAdapter()
    adapter.write_text("ROIPaste macOS integration")
    assert adapter.read_text() == "ROIPaste macOS integration"
