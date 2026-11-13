# ROIPaste — UI/UX Specification

## Design direction

- Modern minimalist with dark mode first, light mode supported. Neutral charcoal + restrained blue accent; clear connection/transfer statuses.
- Android: Jetpack Compose / Material 3 with accessible sizes and dynamic type.
- Vietnamese default. Avoid alarming language and avoid implying that clipboard is being read without consent.
- Mac Milestone A: local CLI + status page; later SwiftUI menu bar.

## Android navigation

`Home` -> `Pair` / `Transfer Details` / `Settings`.

### Screen A — Home / Send & Receive

Header: `ROIPaste`, subtitle `Kết nối clipboard giữa Android và Mac`.

Connection card: device alias `MacBook`, status dot + `Đã kết nối qua USB` / `Wi-Fi an toàn` / `Mất kết nối`, `Kết nối lại` action.

Transfer actions:
- Primary: `Gửi clipboard sang Mac`.
- Secondary: `Lấy clipboard từ Mac`.
- Beneath: read-only preview of local text **only after explicit button/tap and while Activity focused**; don't show sensitive previews by default.
- For received remote text: show summary and `Sao chép vào Android` action.

Footer: `Tạm dừng đồng bộ` toggle, `Cài đặt`, `Ghép cặp thiết bị`.

States:
- Idle: show `Sẵn sàng`, buttons enabled when connected.
- Sending: button loading, repeat taps disabled.
- Success: `Đã gửi sang Mac` and short timestamp.
- Clipboard empty: `Clipboard hiện không có văn bản.`
- Disconnected: `Không kết nối được Mac. Kiểm tra USB hoặc Wi-Fi.`
- Permission/OS: `Android chỉ cho đọc clipboard khi ROIPaste đang mở.`
- Receiving while background: notification `Có nội dung mới từ Mac` without text preview, open app to confirm copy.

### Screen B — Pair Mac

Method selector: `Cáp USB (dành cho phát triển)` / `Wi-Fi`.
- USB: instructions for `adb reverse`, debug-only label and manual token code.
- Wi-Fi: QR scanner, show Mac certificate fingerprint in a confirmation view, device alias, pairing status.
- On pairing failure: clear recovery instructions, never downgrade to insecure HTTP.

### Screen C — Transfer Details

Show received text preview limited to 200 characters by default, reveal after tap; source name, byte count, timestamp, `Sao chép`, `Gửi lại` (if appropriate), `Đóng`.
- Don't persist content in the history for default setup.
- Confirmation not required on every action if user intentionally taps `Gửi` with known content and selected trusted Mac, but no silent transfer on app launch.

### Screen D — Settings

Sections: Device & pairing, Connections, Privacy, About.
- Device: paired Mac name, revoke/unpair.
- Connections: Wi-Fi/USB status and sync retry; no unsecured HTTP option.
- Privacy: passive Mac watcher control, previews off/on (default off), pause/resume, clear ephemeral received item.
- About: app version, supported OS and known limitations.

## Quick Settings tile (post-MVP)

Label `Gửi qua ROIPaste`; tap opens focused Home / dedicated send activity. Never reads clipboard inside the service. If not paired, opens Pair screen.

## Mac user experience

Milestone A terminal status: startup prints listening address, USB connect command and auth-pairing status (never permanent secret in long-lived log). Local status page shows `Connected`, `Paused`, last event metadata only.

Later menu bar SwiftUI:
- Menu icon, `Connected to Ace 5`, `Pause sync`, `Copy from phone`, `Pair device`, `Quit`.
- No automatic persistent clipboard history unless user enables it.

## Accessibility and behavior

- Buttons at least 48 dp touch targets, correct contrast, VoiceOver/TalkBack labels.
- Use OS clipboard confirmation on Android 13+; avoid duplicate toasts.
- Short failure description plus `Thử lại`; do not overwrite clipboard if transfer fails.
- Explicit offline state, cancellation, and device trust indicators.

## Text wireframes

```text
┌──────────────────────────────┐
│ ROIPaste                     │
│ Clipboard Android ↔ macOS    │
│ ┌──────────────────────────┐ │
│ │ ● MacBook — USB Connected│ │
│ └──────────────────────────┘ │
│                              │
│ [ Gửi clipboard sang Mac  ]  │
│ [ Lấy clipboard từ Mac    ]  │
│                              │
│ Nội dung vừa nhận            │
│ ┌──────────────────────────┐ │
│ │ Chưa có nội dung         │ │
│ └──────────────────────────┘ │
│ [ Sao chép vào Android    ]  │
│                              │
│ Tạm dừng đồng bộ        [ ]  │
│ Cài đặt                      │
└──────────────────────────────┘
```
