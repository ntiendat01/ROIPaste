# ROIPaste — Android ↔ macOS Clipboard Companion

**Status:** Milestone 0 in progress. Documentation is at the repository root, the line-length guard is in place, and the mac-agent FastAPI scaffold is available. The Android Gradle project and CI workflow are not added yet.

ROIPaste transfers text and URLs between an Android phone (target: OnePlus Ace 5 / OxygenOS 16) and a MacBook over USB/ADB or a secured local network, without root or cloud API.

## Start here

1. Read `AGENT.md` (and `AGENTS.md` for coding agents).
2. Read `docs/01_PRODUCT_SPEC.md` for scope and acceptance criteria.
3. Read `docs/02_ARCHITECTURE.md` and `docs/03_PROTOCOL_SECURITY.md` before writing code.
4. Implement tasks in `docs/04_IMPLEMENTATION_PLAN.md` in order; use `docs/05_TEST_PLAN.md` as completion gates.
5. Follow `docs/06_UI_UX_SPEC.md` for visual UI and states.

## Non-negotiables

- **No source/test code file may exceed 500 physical lines.** Split before adding the line that would exceed the limit.
- No root, ROM changes, bootloader changes, accessibility-service clipboard scraping, or hidden elevated permissions.
- Never claim universal automatic clipboard sync on Android 16 without on-device proof.
- Do not expose clipboard contents through unsecured LAN HTTP; the development-only ADB tunnel is loopback-bound.
- Handle secrets and logs with care; history off by default.

## Suggested repository shape

```text
ROIPaste/
├── android/                      # Kotlin + Jetpack Compose client
├── mac-agent/                    # Python 3.12+ FastAPI Mac service
├── docs/                         # Product, architecture, protocol, UX, tests
├── scripts/                      # Lint, line counts, developer utilities
├── AGENT.md                      # Canonical AI coding rules
├── AGENTS.md                     # Identical rules for Codex-compatible agents
├── CLAUDE.md                     # Pointer to AGENT.md for Claude
└── README.md
```

## Delivery targets

- **Milestone A:** Tested USB demo: tap send on Ace 5 -> Cmd+V on Mac; tap Fetch on Android -> Paste on Ace 5.
- **Milestone B:** Paired, mutually authenticated HTTPS on Wi-Fi; share sheet; reconnect UX.
- **Milestone C:** Mac clipboard watcher + Android receive notification; optionally automatic receive if proven on the target device.

## Line-length guard

Maintained source and test files must stay at or under 500 physical lines. Check with:

```bash
python3 scripts/check_file_lengths.py
python3 -m pytest scripts/tests
```

The checker counts physical lines, including blanks, comments, and a last line that has no trailing newline. It scans `.kt`, `.kts`, `.java`, `.swift`, `.py`, `.js`, `.ts`, `.tsx`, and `.sh` files.

Excluded directories (any path component): `.git`, `build`, `.gradle`, `.venv`, `venv`, `node_modules`, `generated`, `__pycache__`. Lock files (for example `package-lock.json`, `uv.lock`, or `*.lock`) are also skipped.

Optional flags: `--max` (default 500) and `--root` (default `.`). The script prints the largest maintained file and its line count, and exits 1 if any file is over the limit.

## Mac agent

Tested versions: Python 3.12.16, FastAPI 0.162.5, uvicorn 0.54.0, pydantic 2.14.3, ruff 0.17.5, mypy 2.4.1, and pytest 9.2.0.

```bash
cd mac-agent
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
ruff check
mypy app
pytest
uvicorn app.main:app --host 127.0.0.1 --port 5000
```

The development server binds to `127.0.0.1:5000`. This scaffold exposes only `GET /v1/health`, which returns `{"status":"ok","protocol":1}`. Clipboard routes are added in Milestone 1 and must never be unauthenticated.

## Known limitations

- The Android Gradle scaffold and CI workflow are not part of this change set.
- This environment is Linux, so macOS clipboard tools (`pbcopy` / `pbpaste`) were not used. They are not required for the current health-only scaffold.
- No packaged APK or macOS executable is produced yet.
