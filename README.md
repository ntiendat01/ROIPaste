# ROIPaste — Android ↔ macOS Clipboard Companion

**Status:** Milestone 1 Mac loopback server is implemented and covered by unit tests. The real macOS clipboard gate (curl + `pbcopy`/`pbpaste`) still needs to be run on a Mac. GitHub Actions CI for Milestone 0 was observed green.

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
python -m app
```

`python -m app` binds only to `127.0.0.1:5000` (non-loopback hosts are rejected) and prints a per-session bearer token once to the terminal (stdout, not the log file). `GET /v1/health` stays unauthenticated and returns `{"status":"ok","protocol":1}` with no clipboard data. `GET /v1/status` and `GET`/`POST /v1/clip` require `Authorization: Bearer <token>`.

To connect a phone over USB after the agent is running:

```bash
# from the repository root, with adb on PATH
./scripts/connect_usb.sh
```

### Manual gate on a Mac (pending user run)

Use a benign test string only. In a terminal, start the agent and copy the printed token:

```bash
cd mac-agent
source .venv/bin/activate
python -m app
```

In another terminal (replace `$TOKEN` with the printed session token):

```bash
# Health (no auth)
curl -sS http://127.0.0.1:5000/v1/health

# Unauthenticated clip must be 401
curl -sS -o /tmp/roipaste-unauth.json -w "%{http_code}\n" http://127.0.0.1:5000/v1/clip
# expect 401 and a {"error":{"code":"UNAUTHENTICATED",...}} body

# POST clip, then confirm the Mac clipboard
curl -sS -X POST http://127.0.0.1:5000/v1/clip \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message_id":"4cf0408d-8393-41a0-8706-53fa9754315f","origin":"android","kind":"text","text":"Xin chào từ ROIPaste 👋"}'
pbpaste
# expect: Xin chào từ ROIPaste 👋

# Copy something on the Mac, then GET clip
printf 'hello from Mac' | pbcopy
curl -sS -H "Authorization: Bearer $TOKEN" http://127.0.0.1:5000/v1/clip

# Oversize body must be 413
python3 -c 'import json; print(json.dumps({"message_id":"11111111-1111-4111-8111-111111111111","origin":"android","kind":"text","text":"a"*(256*1024+1)}))' \
  | curl -sS -X POST http://127.0.0.1:5000/v1/clip \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      --data-binary @- -w "\n%{http_code}\n"
# expect PAYLOAD_TOO_LARGE and HTTP 413
```


## Android app

Tested versions: JDK 17.0.21 (Temurin), Android Gradle Plugin 9.5.1, Gradle 9.6.1, Kotlin 2.4.21, Compose BOM 2026.09.00, `activity-compose` 1.13.0, `compileSdk`/`targetSdk` 37, `minSdk` 29.

AGP 9 enables built-in Kotlin, so the `org.jetbrains.kotlin.android` plugin must not be applied. The Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is still applied, and Kotlin Gradle plugin 2.4.21 is pinned in the root `build.gradle.kts` so it stays aligned with that Compose plugin. This combination was verified by a local `./gradlew testDebugUnitTest assembleDebug` run. Android's AGP 9.5 docs show Kotlin 2.4.10 with Gradle 9.6.0; Kotlin's fully tested AGP range for 2.4.20–2.4.21 currently lists up to AGP 9.3.1, while Gradle 9.6.1 is inside Kotlin 2.4.21's supported Gradle range.

Requires JDK 17 and an Android SDK that includes platform 37 (the local SDK package was `platforms;android-37.0`). Point Gradle at the SDK with `ANDROID_HOME` / `ANDROID_SDK_ROOT` or `android/local.properties` (`sdk.dir=...`). Do not commit `local.properties`.

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

The scaffold is a single Compose (`Material 3`) `MainActivity` that shows the Vietnamese `app_name` and `subtitle` strings. It has debug and release build types (release minify is off, and no signing config is checked in). There is no `INTERNET` permission and no network security config yet. A JVM unit test covers `ClipLimits.MAX_TEXT_BYTES` (256 KiB).

## Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests to `main` and `dev` with three jobs:

- `line-limit` — Python 3.12, `pytest==9.2.0`, `pytest scripts/tests`, then `python scripts/check_file_lengths.py`
- `mac-agent` — Python 3.12, `pip install -r requirements-dev.txt` (that file includes `-r requirements.txt`), then `ruff check`, `mypy app`, and `pytest`
- `android` — JDK 17 (Temurin), Android SDK setup, Gradle setup, then `./gradlew testDebugUnitTest assembleDebug` in `android/`

This workflow was observed green on GitHub after the Milestone 0 commits were pushed.

## Known limitations

- The Milestone 1 real-Mac clipboard gate (`curl` + `pbpaste`/`pbcopy` on macOS) has not been run yet. Linux unit tests use a fake clipboard; the `MacClipboardAdapter` integration test is skipped except on Darwin.
- The Android app is still a UI scaffold. USB transfer from the Ace 5 is not implemented on the client yet.
- The `com.roipaste.app` namespace has not been checked for uniqueness on the Play Store; do that before public distribution.
- A local debug packaging warning reported that `libandroidx.graphics.path.so` could not be stripped; the APK still built.
- `pytest` for the Mac agent may print a Starlette deprecation warning about using `httpx` with `TestClient` (prefer `httpx2` in a later library update).
