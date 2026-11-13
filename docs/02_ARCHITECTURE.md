# ROIPaste — Architecture & Engineering Decisions

## 1. Strategy

Use two native-enough components with a small shared JSON protocol: Kotlin/Jetpack Compose Android client and Python FastAPI Mac Agent. This optimizes ease of building, testing and debugging. A SwiftUI menu-bar host is P2, not required for first transfer.

```text
 OnePlus Ace 5 / OxygenOS 16                     MacBook / macOS
 ┌─────────────────────────────────┐             ┌──────────────────────────────┐
 │ Compose Screens / ViewModels    │             │ CLI + optional local status  │
 │ ShareReceiverActivity           │             │ FastAPI HTTP API             │
 │ ClipboardGateway (focused only) │             │ ClipboardAdapter            │
 │ TransferRepository              │ <--HTTPS--> │ TransferService             │
 │ PairingStore (Android Keystore) │             │ Auth / TLS / Pairing         │
 │ Connectivity / Notifications    │             │ Watcher / NSPasteboard bridge│
 └─────────────────────────────────┘             └──────────────────────────────┘
                  │ USB development tunnel: adb reverse tcp:5000 tcp:5000
                  └────────────────> 127.0.0.1:5000 on Mac
```

## 2. Android layers (Kotlin)

- `ui/`: Compose screens, states, navigation, previews. No HTTP or direct clipboard calls in UI composables.
- `presentation/`: ViewModels and immutable UI state; coroutine-safe, lifecycle-aware.
- `domain/`: SendClip, FetchClip, PairDevice, PauseSync, type/validation rules.
- `data/clipboard/`: focused-Activity clipboard gateway, MIME coercion and size guard.
- `data/network/`: OkHttp/Ktor client with injection of base URL, certificate pin, auth, JSON serializer, timeouts and retry policy.
- `data/security/`: Keystore-backed credential handling; no secrets in BuildConfig or source.
- `data/settings/`: DataStore preferences. Do not persist copied content by default.
- `share/`: exported ACTION_SEND receiver; validate incoming intent MIME and length.
- `service/`: OPTIONAL receiver / notification mechanism; do not start long-running FGS without confirmed policy-compliant use case, permissions and device test.

**Android clipboard gate:** read clipboard only from resumed/focused Activity or with legitimate direct user-provided share Intent data. Quick Settings tile brings app to foreground; no clipboard access inside tile service. Receiving while background defaults to notification/explicit copy if automatic behavior is not proven.

## 3. Mac Agent layers (Python 3.12+)

- `app/main.py`: app factory, lifecycle and dependency wiring only.
- `app/api/`: routes `/v1/health`, `/v1/clip`, `/v1/pair/*`, `/v1/status`.
- `app/models/`: Pydantic typed request/response and validation.
- `app/core/`: clipboard adapter, transfer service, duplicate filter, watcher.
- `app/security/`: auth, pairing, cert management, stored secrets, rate limiting.
- `app/config.py`: loopback vs LAN profile, environment validation, safe defaults.
- `tests/`: unit/API/integration tests and fake clipboard adapter.

**Clipboard adapters:** begin with `subprocess.run(["pbpaste"], capture_output=True)` and `subprocess.run(["pbcopy"], input=...)` without shell=True; preserve UTF-8, enforce size/timeouts. As needed use `NSPasteboard` via PyObjC for changeCount/type handling. Avoid blindly polling sensitive clipboard by default.

## 4. Transport profiles

### A. USB dev (Milestone A)

- Mac app binds ONLY 127.0.0.1:5000.
- On Mac run `adb reverse tcp:5000 tcp:5000` after USB debugging authorization.
- Android calls `http://127.0.0.1:5000` through tunnel; Android cleartext HTTP exception ONLY for localhost debug build and NEVER for production/LAN.
- Still require local session auth token; do not rely solely on ADB trust.
- Explicit connect/disconnect. No user-facing expectation that ADB is always connected.

### B. LAN production (Milestone B)

- HTTPS with pinned local server certificate/SPKI, QR enrollment, short-lived pairing challenge, per-device bearer credential, replay-safe request identifiers. Certificates rotate through explicit re-pairing.
- Agent may bind LAN interface only when enabled by user; advertise via mDNS optionally, but never trust discovery alone.
- OS firewall and private/local-network access must be surfaced to user. HTTP 0.0.0.0:5000 without TLS is forbidden.
- Use a narrow allowlist: no arbitrary shell command execution; no file system API.

## 5. Clipboard transfer state machine

`Disconnected -> Connecting -> PairedConnected -> Sending/Fetching -> Confirmed or Failed -> PairedConnected`.

Every event gets `message_id` UUID, `source_device_id`, monotonic local sequence or timestamp (not used as sole order authority), `origin`, and SHA-256 content digest. Keep an in-memory bounded LRU of recently processed IDs and digest+origin. Do not echo imported clipboard changes back to originating device. Do not overwrite local clipboard on failed/partial transfer.

## 6. Concurrency and lifecycle

- Android: Kotlin Coroutines + StateFlow, dependency injection via simple constructors or Hilt if justified; no work on main thread.
- macOS: FastAPI/uvicorn lifecycle, lock for clipboard writes and transfer status, bounded queue; watcher must cancel cleanly.
- Under simultaneous copy conflict: **manual send wins only after explicit user action**; do not silently overwrite current clipboard. Show latest received item with explicit action.
- Reconnect with bounded exponential backoff only while UI active; network errors should not drain battery.

## 7. Dependency direction

`UI -> ViewModel -> domain -> repository interface -> data adapters`.

Server: `routes -> service -> clipboard interface`, with auth before any clipboard access.

Interfaces are mockable; no filesystem writes, system clipboard or network required for most unit tests.

## 8. Deployment artifacts

- Android: Gradle wrapper, installable debug APK, signed release only after review.
- Mac: `python -m venv`, pinned Python dependencies, `uvicorn` start script; eventually a `launchd` user agent for opt-in auto-start.
- No privileged daemon, no always-on root helper, no bootloader or OS modifications.

## 9. Technical decision log

ADR-001 Kotlin + Compose client, Python FastAPI host; prioritize MVP usability.
ADR-002 Scoped clipboard access respecting Android focus; share-sheet as primary fast path.
ADR-003 No plain HTTP over local Wi-Fi; pinned HTTPS before LAN release.
ADR-004 Manual copy action on background receive is dependable baseline.
ADR-005 Text/URL only; no persistence of clip contents by default.
ADR-006 Hard 500 physical-line limit for every source/test file.
