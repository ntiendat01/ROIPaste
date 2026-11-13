# ROIPaste — Android ↔ macOS Clipboard Companion

**Status:** Milestone 3 (secure Wi-Fi pairing) is implemented and covered by Python and JVM unit tests plus a local debug build. The real macOS clipboard gate (curl + `pbcopy`/`pbpaste`), the Ace 5 USB demo, and a real-device Wi-Fi pairing run still need to be done by the user. GitHub Actions CI for Milestone 0 was observed green.

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

Tested versions: Python 3.12.16, FastAPI 0.162.5, uvicorn 0.54.0, pydantic 2.14.3, cryptography 46.0.7, qrcode 8.2, keyring 25.7.0, ruff 0.17.5, mypy 2.4.1, and pytest 9.2.0.

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

`python -m app` (the default USB profile) binds only to `127.0.0.1:5000` (non-loopback hosts are rejected) and prints a per-session bearer token once to the terminal (stdout, not the log file). `GET /v1/health` stays unauthenticated and returns `{"status":"ok","protocol":1}` with no clipboard data. `GET /v1/status` and `GET`/`POST /v1/clip` require `Authorization: Bearer <token>`.

To connect a phone over USB after the agent is running:

```bash
# from the repository root, with adb on PATH
./scripts/connect_usb.sh
```

### LAN HTTPS (opt-in)

Wi-Fi is never the default. Start it explicitly:

```bash
cd mac-agent
source .venv/bin/activate
python -m app --lan --pair
```

That profile binds a detected private interface IP (not `0.0.0.0` unless you pass `--bind-any`, which is documented and still advertises a private IP in the QR). HTTPS is required; a LAN configuration without TLS is rejected before the server starts. The agent generates a self-signed ECDSA P-256 certificate once, stores it under the OS user config directory (`~/Library/Application Support/ROIPaste` on macOS, `$XDG_CONFIG_HOME/roipaste` or `~/.config/roipaste` otherwise, or `ROIPASTE_CONFIG_DIR`), and keeps the private key at mode `0600`. The SPKI SHA-256 pin is printed in the terminal. Certificate rotation is only via `python -m app cert reset`, after which every phone must be re-paired.

`--pair` (or typing `pair` and Enter in the same terminal) opens a two-minute, single-use pairing window and prints an ASCII QR plus a JSON line. The payload contains host, port, SPKI pin, a one-time enrollment token, and protocol version. It never contains the permanent device credential. When the phone completes pairing, the Mac prompts `y/N` with the device alias and a short certificate fingerprint before issuing a per-device bearer. Only a SHA-256 hash of that credential is stored.

On macOS, the first LAN start may show a firewall prompt. Allow incoming connections for Python, or the phone will not reach the agent. Admin commands (safe to run in another terminal, against the same config dir):

```bash
python -m app devices list
python -m app devices revoke <device_id>
python -m app devices pause
python -m app devices resume
python -m app cert reset
```

While the LAN server is running in a TTY, you can also type `pair`, `devices`, `revoke <id>`, `pause`, or `resume` and press Enter.

#### Paired-device storage threat model

On Darwin, hashes are written to the login Keychain via `keyring` when that backend works. Anywhere else (and whenever Keychain is unavailable, including this Linux runner and unit tests), the store is a mode-`0600` JSON file in the config directory. The file holds only SHA-256 hashes, aliases, revoke flags, and a pause flag — never the bearer itself. Anyone who can read that file or the Keychain item as your user can still *see* which devices are paired and can *replace* hashes (denial of pairing), but they cannot recover the original credential from the hash. They can also pause sync. Protect the Mac login account; this is not a multi-user hardened store.

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

Tested versions: JDK 17.0.21 (Temurin), Android Gradle Plugin 9.5.1, Gradle 9.6.1, Kotlin 2.4.21, Compose BOM 2026.09.00, `activity-compose` 1.13.0, Lifecycle 2.11.0, DataStore 1.2.0, OkHttp 4.12.0, kotlinx-serialization 1.9.0, kotlinx-coroutines 1.10.2, Play services code scanner 16.1.0, `compileSdk`/`targetSdk` 37, `minSdk` 29.

AGP 9 enables built-in Kotlin, so the `org.jetbrains.kotlin.android` plugin must not be applied. The Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) and the serialization plugin are applied, and Kotlin Gradle plugin 2.4.21 is pinned in the root `build.gradle.kts`. Android's AGP 9.5 docs show Kotlin 2.4.10 with Gradle 9.6.0; Kotlin's fully tested AGP range for 2.4.20–2.4.21 currently lists up to AGP 9.3.1, while Gradle 9.6.1 is inside Kotlin 2.4.21's supported Gradle range.

Requires JDK 17 and an Android SDK that includes platform 37 (the local SDK package was `platforms;android-37.0`). Point Gradle at the SDK with `ANDROID_HOME` / `ANDROID_SDK_ROOT` or `android/local.properties` (`sdk.dir=...`). Do not commit `local.properties`.

```bash
cd android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is a Compose (`Material 3`) client for USB and pinned-HTTPS Wi-Fi transfer. Home can send the Android clipboard to the Mac agent, fetch a preview (hidden until tap, truncated to 200 characters), and copy that preview back to Android. The connection card shows **Wi-Fi an toàn** after a LAN pair. A Pair screen offers USB (dev) or Wi-Fi: USB still accepts the per-session token printed by `python -m app`; Wi-Fi accepts a scanned QR or a pasted JSON/URI payload, then shows the Mac SPKI fingerprint and a device alias for confirmation before completing pairing. Share sheet (`ACTION_SEND` / `text/plain`) shows a preview and requires an explicit confirm before send. USB session tokens and LAN credentials, plus the SPKI pin and host, are stored with Android Keystore AES-GCM wrapping in DataStore, never in `BuildConfig` or logs. **Hủy ghép cặp** in Settings clears those keys. Debug builds permit cleartext only to `127.0.0.1` and `localhost`; release/main cleartext is disabled, and a stored pin never falls back to HTTP. The only added permission is `INTERNET`.

QR scanning uses the Google Play services code scanner (`play-services-code-scanner` 16.1.0) so the app does not request `CAMERA`. If Play services are missing (possible on some Ace 5 / China builds), the scan button explains that and the JSON paste field remains the fallback. CameraX + ML Kit was not chosen, to avoid a camera permission for a pairing flow that already has a manual paste path.

TLS uses a custom `X509TrustManager` that accepts only a certificate whose SPKI SHA-256 equals the pin stored at pairing (no CA bundle, no trust-all). Hostname verification then checks that the connected host equals the paired IP or name; `localhost` and `127.0.0.1` are treated as equivalent for loopback tests. Identity is the out-of-band pin, not a public CA or DNS name, because a durable self-signed certificate may not list a later DHCP address in its SAN. Android Lint warns about custom trust managers; the implementation is intentionally pin-only and is covered by a MockWebServer + `HeldCertificate` test that fails closed on a mismatched pin.

### USB demo (pending a run on Ace 5 + Mac)

Use only a benign test string. On the Mac:

```bash
cd mac-agent
source .venv/bin/activate
python -m app
```

Copy the printed session token. In another Mac terminal, from the repository root, with the phone connected and USB debugging authorized:

```bash
./scripts/connect_usb.sh
```

Install the debug APK (`android/app/build/outputs/apk/debug/app-debug.apk`) on the phone, open ROIPaste, open **Ghép cặp thiết bị**, paste the token, and save it. Then:

1. Copy a short harmless sentence on the phone, open ROIPaste (it must stay in the foreground), tap **Gửi clipboard sang Mac**. The app should show **Đã gửi sang Mac**. On the Mac, `pbpaste` (or Cmd+V) should match.
2. Copy a different harmless sentence on the Mac, tap **Lấy clipboard từ Mac**, tap the preview to reveal it, then **Sao chép vào Android**. Paste in another Android app; the text should match.
3. Optional: share a `text/plain` snippet into ROIPaste from another app, confirm the preview, and send.

If `adb reverse` is dropped (replug, `adb kill-server`, or a phone reboot), run `./scripts/connect_usb.sh` again. If the Mac agent is restarted, it prints a new token that must be saved again in the Pair screen.

### Manual Wi-Fi pairing gate (pending a run on Ace 5 + Mac)

Phone and Mac must be on the same local network. Use only a benign test string. On the Mac:

```bash
cd mac-agent
source .venv/bin/activate
python -m app --lan --pair
```

If macOS asks to allow incoming connections, choose Allow. Confirm the terminal shows `https://<private-ip>:5000`, a SPKI pin, an ASCII QR, and a JSON line. Install the debug APK on the phone, open ROIPaste, open **Ghép cặp thiết bị**, choose **Wi-Fi**, then either tap **Quét mã QR** or paste the JSON line into the payload field and tap **Xem dấu vân tay**. Check that the short SPKI fingerprint matches the one printed on the Mac, set a device alias if you want, and tap **Xác nhận và ghép cặp**. On the Mac, type `y` when prompted. The phone should then show **Wi-Fi an toàn**.

1. Copy a short harmless sentence on the phone, keep ROIPaste in the foreground, tap **Gửi clipboard sang Mac**. On the Mac, `pbpaste` (or Cmd+V) should match.
2. Copy a different harmless sentence on the Mac, tap **Lấy clipboard từ Mac**, reveal the preview, then **Sao chép vào Android**. Paste in another Android app; the text should match.
3. Optional: toggle airplane mode on the phone, turn Wi-Fi back on, return to ROIPaste, and confirm it reconnects without falling back to HTTP. Then, on the Mac, run `python -m app devices list` and `python -m app devices revoke <device_id>`; the phone should fail subsequent clipboard calls until you re-pair.

A USB session token must not work against the LAN port. If pairing is rejected or the pin does not match, the app must not switch to `http://`.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests to `main` and `dev` with three jobs:

- `line-limit` — Python 3.12, `pytest==9.2.0`, `pytest scripts/tests`, then `python scripts/check_file_lengths.py`
- `mac-agent` — Python 3.12, `pip install -r requirements-dev.txt` (that file includes `-r requirements.txt`), then `ruff check`, `mypy app`, and `pytest`
- `android` — JDK 17 (Temurin), Android SDK setup, Gradle setup, then `./gradlew testDebugUnitTest assembleDebug lintDebug` in `android/`

This workflow was observed green on GitHub after the Milestone 0 commits were pushed.

## Known limitations

- The Milestone 1 real-Mac clipboard gate (`curl` + `pbpaste`/`pbcopy` on macOS) has not been run yet. Linux unit tests use a fake clipboard; the `MacClipboardAdapter` integration test is skipped except on Darwin.
- The Milestone 2 Ace 5 USB demo (install APK, paste token, send/fetch over `adb reverse`) has not been run on a physical device yet. Android JVM tests use fakes and MockWebServer.
- The Milestone 3 real-device Wi-Fi gate (QR or paste pairing, send/fetch over pinned HTTPS, revoke) has not been run on Ace 5 + a Mac yet. LAN TLS is covered by a local uvicorn + httpx test on `127.0.0.1`, and pin mismatch is covered by MockWebServer + `HeldCertificate`.
- Optional mDNS discovery was not added. Pairing always uses the host and pin from the QR/JSON, never a discovered name alone.
- Sleep, airplane mode, and DHCP address changes have not been tested on hardware. If the Mac's private IP changes, re-open a pairing window or start the agent so the QR advertises the new address; an already-paired phone must be re-paired if the stored host no longer reaches the agent. `python -m app cert reset` also requires a full re-pair because the pin changes.
- QR scanning needs Google Play services. On builds without them, paste the JSON payload instead.
- On non-Darwin systems the paired-device store is a user-owned `0600` JSON file of credential hashes, not Keychain. See the threat model above.
- The `com.roipaste.app` namespace has not been checked for uniqueness on the Play Store; do that before public distribution.
- A local debug packaging warning reported that `libandroidx.graphics.path.so` could not be stripped; the APK still built.
- `pytest` for the Mac agent may print a Starlette deprecation warning about using `httpx` with `TestClient` (prefer `httpx2` in a later library update), and an httpx warning about `verify=<str>` in the TLS test.
