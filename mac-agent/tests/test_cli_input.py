"""Exercise the real stdin router while an HTTP worker awaits approval."""

from __future__ import annotations

import io
import queue
import threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from tests.fakes import FakeClipboardAdapter

from app.cli_commands import start_command_thread
from app.cli_input import TerminalInput
from app.config import Settings
from app.main import create_app
from app.security.devices import DeviceStore, FileDeviceBackend
from app.security.pairing import PairingService


class TtyInput(io.StringIO):
    def __init__(self) -> None:
        super().__init__()
        self.lines: queue.Queue[str | None] = queue.Queue()
        self.finished = threading.Event()

    def isatty(self) -> bool:
        return True

    def __next__(self) -> str:
        line = self.lines.get(timeout=3)
        if line is None:
            self.finished.set()
            raise StopIteration
        return line


class PromptOutput(io.StringIO):
    def __init__(self) -> None:
        super().__init__()
        self.prompted = threading.Event()

    def write(self, text: str) -> int:
        result = super().write(text)
        if "[y/N]" in text:
            self.prompted.set()
        return result


@pytest.fixture
def terminal():
    stream = TtyInput()
    output = PromptOutput()
    reader = TerminalInput(stream, output, timeout_seconds=1)
    yield reader, stream, output
    stream.lines.put(None)
    assert stream.finished.wait(1)


def test_http_pair_approval_reaches_waiting_worker(terminal, tmp_path: Path) -> None:
    reader, stream, output = terminal
    devices = DeviceStore(FileDeviceBackend(tmp_path / "devices.json"))
    pairing = PairingService(
        host="127.0.0.1",
        port=5000,
        pin="sha256/test",
        devices=devices,
        clock=lambda: 1000.0,
        confirm=reader.confirm,
    )
    enrollment = pairing.open_window()
    app = create_app(
        settings=Settings(host="127.0.0.1", profile="lan", tls_enabled=True, config_dir=tmp_path),
        clipboard=FakeClipboardAdapter(),
        devices=devices,
        pairing=pairing,
    )
    start_command_thread(pairing, devices, app.state.watcher, reader)
    with TestClient(app) as client, ThreadPoolExecutor(max_workers=1) as workers:
        response = workers.submit(
            client.post,
            "/v1/pair/complete",
            json={"enrollment_token": enrollment.token, "device_id": "ace5", "alias": "Ace 5"},
        )
        assert output.prompted.wait(1)
        assert devices.get("ace5") is None
        assert not response.done()
        stream.lines.put("y\n")
        complete = response.result(timeout=2)
        assert complete.status_code == 201
        credential = complete.json()["credential"]
        assert devices.find_by_credential(credential) is not None
        assert credential not in output.getvalue()
        assert "Pairing approved" in output.getvalue()
        status = client.get("/v1/status", headers={"Authorization": f"Bearer {credential}"})
        assert status.status_code == 200
        assert status.json()["transport"] == "lan"


@pytest.mark.parametrize("answer, accepted", [("yes\n", True), ("n\n", False), ("\n", False)])
def test_confirmation_answers_and_commands_resume(terminal, answer: str, accepted: bool) -> None:
    reader, stream, output = terminal
    commands: queue.Queue[str] = queue.Queue()
    reader.start(commands.put)
    with ThreadPoolExecutor(max_workers=1) as workers:
        result = workers.submit(reader.confirm, "Ace 5", "test-pin")
        assert output.prompted.wait(1)
        stream.lines.put(answer)
        assert result.result(timeout=2) is accepted
    stream.lines.put("watch on\n")
    assert commands.get(timeout=1) == "watch on"
    assert commands.empty()  # Approval was never sent to the command handler.


def test_timeout_and_late_yes_cannot_approve_next_request(terminal) -> None:
    reader, stream, output = terminal
    commands: queue.Queue[str] = queue.Queue()
    reader.start(commands.put)
    assert reader.confirm("Ace 5", "test-pin") is False
    assert "timed out" in output.getvalue()
    stream.lines.put("y\n")
    assert commands.get(timeout=1) == "y"
    output.prompted.clear()
    with ThreadPoolExecutor(max_workers=1) as workers:
        result = workers.submit(reader.confirm, "Other", "test-pin")
        assert output.prompted.wait(1)
        assert not result.done()
        stream.lines.put("n\n")
        assert result.result(timeout=2) is False


def test_eof_releases_pending_confirmation(terminal) -> None:
    reader, stream, output = terminal
    reader.start(lambda _: None)
    with ThreadPoolExecutor(max_workers=1) as workers:
        result = workers.submit(reader.confirm, "Ace 5", "test-pin")
        assert output.prompted.wait(1)
        stream.lines.put(None)
        assert result.result(timeout=2) is False
    assert reader.confirm("Other", "test-pin") is False


def test_second_confirmation_cannot_replace_pending_request(terminal) -> None:
    reader, stream, output = terminal
    reader.start(lambda _: None)
    with ThreadPoolExecutor(max_workers=1) as workers:
        result = workers.submit(reader.confirm, "Ace 5", "test-pin")
        assert output.prompted.wait(1)
        assert reader.confirm("Other", "test-pin") is False
        stream.lines.put("y\n")
        assert result.result(timeout=2) is True


def test_noninteractive_terminal_fails_closed() -> None:
    reader = TerminalInput(io.StringIO("y\n"), io.StringIO())
    reader.start(lambda _: pytest.fail("Noninteractive commands must not run"))
    assert reader.confirm("Ace 5", "test-pin") is False
