"""One stdin reader routes commands and bounded pairing confirmations."""

from __future__ import annotations

import sys
import threading
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import TextIO

CONFIRM_TIMEOUT_SECONDS = 60.0


@dataclass
class _Confirmation:
    ready: threading.Event = field(default_factory=threading.Event)
    accepted: bool = False


class TerminalInput:
    def __init__(
        self,
        stream: TextIO | None = None,
        output: TextIO | None = None,
        timeout_seconds: float = CONFIRM_TIMEOUT_SECONDS,
    ) -> None:
        self._stream = stream if stream is not None else sys.stdin
        self._output = output if output is not None else sys.stdout
        self._timeout = timeout_seconds
        self._lock = threading.Lock()
        self._pending: _Confirmation | None = None
        self._available = False

    def start(self, on_command: Callable[[str], None]) -> None:
        if not self._stream.isatty():
            return
        with self._lock:
            if self._available:
                return
            self._available = True
        threading.Thread(
            target=self._read_loop,
            args=(on_command,),
            daemon=True,
            name="roipaste-stdin",
        ).start()

    def confirm(self, alias: str, fingerprint: str) -> bool:
        pending = _Confirmation()
        with self._lock:
            if not self._available or self._pending is not None:
                return False
            self._pending = pending
            self._println(
                f"Pair device {alias!r} with certificate fingerprint {fingerprint}? "
                f"[y/N] (within {self._timeout:g}s)"
            )
        answered = pending.ready.wait(self._timeout)
        with self._lock:
            self._pending = None
            accepted = answered and pending.accepted
            self._println(
                "Pairing approved; saving device..."
                if accepted
                else "Pairing denied or timed out. Type `pair` to try again."
            )
        return accepted

    def _read_loop(self, on_command: Callable[[str], None]) -> None:
        try:
            for raw in self._stream:
                line = raw.strip()
                with self._lock:
                    pending = self._pending
                    if pending is not None:
                        if not pending.ready.is_set():
                            pending.accepted = line.lower() in {"y", "yes"}
                            pending.ready.set()
                        continue
                if line:
                    on_command(line)
        finally:
            with self._lock:
                self._available = False
                if self._pending is not None:
                    self._pending.accepted = False
                    self._pending.ready.set()

    def _println(self, message: str) -> None:
        print(message, file=self._output, flush=True)
