# ROIPaste — Android ↔ macOS Clipboard Companion

**Status:** Implementation-ready specification, not application source code.

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

**This repository currently contains the specifications and agent instructions, not a built APK or macOS executable.**
