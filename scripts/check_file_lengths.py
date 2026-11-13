#!/usr/bin/env python3
"""Fail if any maintained source or test file exceeds the line limit.

Physical lines include blanks and comments. A final line without a trailing
newline is still counted.

Excluded directories (any path component): .git, build, .gradle, .venv, venv,
node_modules, generated, __pycache__.

Lock files are excluded by exact name (package-lock.json, poetry.lock,
Pipfile.lock, uv.lock, Cargo.lock, yarn.lock, pnpm-lock.yaml, composer.lock,
Gemfile.lock, pdm.lock) or by a .lock suffix.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

DEFAULT_MAX = 500
SOURCE_SUFFIXES = {
    ".kt", ".kts", ".java", ".swift", ".py", ".js", ".ts", ".tsx", ".sh",
}
EXCLUDED_DIRS = frozenset({
    ".git", "build", ".gradle", ".venv", "venv", "node_modules", "generated",
    "__pycache__",
})
LOCK_NAMES = frozenset({
    "package-lock.json", "poetry.lock", "Pipfile.lock", "uv.lock",
    "Cargo.lock", "yarn.lock", "pnpm-lock.yaml", "composer.lock",
    "Gemfile.lock", "pdm.lock",
})


def count_physical_lines(path: Path) -> int:
    """Count physical lines; a last line without a newline still counts."""
    data = path.read_bytes()
    if not data:
        return 0
    count = data.count(b"\n")
    if not data.endswith(b"\n"):
        count += 1
    return count


def display_path(path: Path, root: Path) -> Path:
    try:
        return path.resolve().relative_to(root.resolve())
    except ValueError:
        return path


def is_excluded(path: Path, root: Path) -> bool:
    """Return True if path is under an excluded directory or is a lock file."""
    try:
        parts = path.resolve().relative_to(root.resolve()).parts
    except ValueError:
        parts = path.resolve().parts
    if any(part in EXCLUDED_DIRS for part in parts[:-1]):
        return True
    name = path.name
    return name in LOCK_NAMES or name.endswith(".lock")


def iter_maintained_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for path in sorted(root.rglob("*")):
        if path.is_file() and path.suffix in SOURCE_SUFFIXES and not is_excluded(path, root):
            files.append(path)
    return files


def check(root: Path, max_lines: int) -> int:
    """Print the largest file and return 1 if any file exceeds max_lines."""
    files = iter_maintained_files(root)
    if not files:
        print("No maintained source or test files found.")
        return 0
    counts = [(count_physical_lines(path), path) for path in files]
    largest, largest_path = max(counts, key=lambda item: (item[0], str(item[1])))
    print(f"Largest maintained file: {display_path(largest_path, root)} ({largest} lines)")
    violators = [(n, p) for n, p in counts if n > max_lines]
    if not violators:
        return 0
    print(f"Files exceeding {max_lines} physical lines:")
    for n, path in sorted(violators, key=lambda item: (-item[0], str(item[1]))):
        print(f"  {display_path(path, root)}: {n}")
    return 1


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--max", type=int, default=DEFAULT_MAX, help="Maximum physical lines (default: 500)")
    parser.add_argument("--root", type=Path, default=Path("."), help="Repository root to scan (default: .)")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if not args.root.exists():
        print(f"Root does not exist: {args.root}", file=sys.stderr)
        return 2
    return check(args.root, args.max)


if __name__ == "__main__":
    raise SystemExit(main())
