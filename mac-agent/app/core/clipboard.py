"""Clipboard adapters: Mac subprocess implementation and a shared protocol."""

from __future__ import annotations

import os
import subprocess
import threading
from typing import Protocol

from app.core.errors import ClipboardUnavailableError

_UTF8_LOCALE = "en_US.UTF-8"


class ClipboardAdapter(Protocol):
    def read_text(self) -> str | None:
        """Return clipboard text, or None when the clipboard is empty."""

    def write_text(self, text: str) -> None:
        """Replace clipboard contents with text. Raises on failure."""


def utf8_subprocess_env() -> dict[str, str]:
    env = os.environ.copy()
    env["LANG"] = _UTF8_LOCALE
    env["LC_CTYPE"] = _UTF8_LOCALE
    return env


class MacClipboardAdapter:
    """Clipboard access via pbcopy/pbpaste. Never uses shell=True."""

    def __init__(self, timeout_seconds: float = 5.0) -> None:
        self._timeout_seconds = timeout_seconds
        self._write_lock = threading.Lock()

    def read_text(self) -> str | None:
        completed = self._run(["pbpaste"], input_bytes=None)
        text = completed.stdout.decode("utf-8")
        if text == "":
            return None
        return text

    def write_text(self, text: str) -> None:
        with self._write_lock:
            self._run(["pbcopy"], input_bytes=text.encode("utf-8"))

    def _run(
        self, command: list[str], input_bytes: bytes | None
    ) -> subprocess.CompletedProcess[bytes]:
        try:
            completed = subprocess.run(  # noqa: S603  # command is a fixed argv list
                command,
                input=input_bytes,
                capture_output=True,
                timeout=self._timeout_seconds,
                check=False,
                shell=False,
                env=utf8_subprocess_env(),
            )
        except FileNotFoundError as exc:
            raise ClipboardUnavailableError("Clipboard tool is not available") from exc
        except subprocess.TimeoutExpired as exc:
            raise ClipboardUnavailableError("Clipboard tool timed out") from exc
        except OSError as exc:
            raise ClipboardUnavailableError("Clipboard tool failed") from exc
        if completed.returncode != 0:
            raise ClipboardUnavailableError("Clipboard tool exited unsuccessfully")
        return completed
