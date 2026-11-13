"""Tests for scripts/check_file_lengths.py (fixtures built in tmp_path)."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import check_file_lengths as cfl  # noqa: E402


def write_lines(path: Path, count: int, *, newline_at_eof: bool = True) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    body = "\n".join(f"line {i}" for i in range(count))
    if newline_at_eof and count > 0:
        body += "\n"
    path.write_text(body, encoding="utf-8")


def test_500_lines_passes(tmp_path: Path) -> None:
    target = tmp_path / "ok.py"
    write_lines(target, 500)
    assert cfl.count_physical_lines(target) == 500
    assert cfl.check(tmp_path, 500) == 0


def test_501_lines_fails(tmp_path: Path) -> None:
    target = tmp_path / "too_long.py"
    write_lines(target, 501)
    assert cfl.count_physical_lines(target) == 501
    assert cfl.check(tmp_path, 500) == 1


def test_excluded_dir_ignored(tmp_path: Path) -> None:
    write_lines(tmp_path / "build" / "generated.py", 600)
    write_lines(tmp_path / ".venv" / "lib" / "pkg.py", 600)
    write_lines(tmp_path / ".gradle" / "cache.py", 600)
    write_lines(tmp_path / "venv" / "lib" / "pkg.py", 600)
    write_lines(tmp_path / "node_modules" / "pkg" / "index.js", 600)
    write_lines(tmp_path / "generated" / "out.py", 600)
    write_lines(tmp_path / "__pycache__" / "mod.py", 600)
    write_lines(tmp_path / "ok.py", 10)
    assert cfl.check(tmp_path, 500) == 0


def test_no_trailing_newline_is_counted(tmp_path: Path) -> None:
    short_file = tmp_path / "no_nl.py"
    write_lines(short_file, 3, newline_at_eof=False)
    assert short_file.read_bytes().endswith(b"line 2")
    assert not short_file.read_bytes().endswith(b"\n")
    assert cfl.count_physical_lines(short_file) == 3

    long_file = tmp_path / "edge.py"
    write_lines(long_file, 501, newline_at_eof=False)
    assert cfl.count_physical_lines(long_file) == 501
    assert cfl.check(tmp_path, 500) == 1


def test_non_listed_extension_ignored(tmp_path: Path) -> None:
    write_lines(tmp_path / "notes.md", 800)
    write_lines(tmp_path / "data.json", 800)
    write_lines(tmp_path / "ok.py", 10)
    assert cfl.check(tmp_path, 500) == 0


def test_cli_exit_codes(tmp_path: Path) -> None:
    script = SCRIPTS_DIR / "check_file_lengths.py"
    write_lines(tmp_path / "ok.py", 500)
    ok = subprocess.run(
        [sys.executable, str(script), "--root", str(tmp_path), "--max", "500"],
        check=False,
        capture_output=True,
        text=True,
    )
    assert ok.returncode == 0
    assert "Largest maintained file" in ok.stdout
    assert "ok.py" in ok.stdout

    write_lines(tmp_path / "too_long.py", 501)
    bad = subprocess.run(
        [sys.executable, str(script), "--root", str(tmp_path), "--max", "500"],
        check=False,
        capture_output=True,
        text=True,
    )
    assert bad.returncode == 1
    assert "too_long.py" in bad.stdout
    assert "501" in bad.stdout
