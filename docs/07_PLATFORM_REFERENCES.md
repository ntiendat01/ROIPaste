# Platform documentation and verification references

These references justify architecture constraints; re-check official docs when implementing against the SDK/OS version in use.

1. Android 10 privacy changes — clipboard access restricted to focused app or default IME: https://developer.android.com/about/versions/10/privacy/changes
2. Android clipboard copy/paste and sensitive flag, Android 13 preview: https://developer.android.com/develop/ui/views/touch-and-input/copy-paste
3. Android clipboard security guidance: https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling
4. Android foreground service recent restrictions: https://developer.android.com/develop/background-work/services/fgs/changes
5. Android restrictions starting foreground service in background: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
6. Apple NSPasteboard API: https://developer.apple.com/documentation/appkit/nspasteboard
7. Apple Developer documentation (verify exact version and app sandbox permissions as needed): https://developer.apple.com/documentation/

## Implementation cautions

- Never infer that calling ClipboardManager from Quick Settings tile gives focus. It doesn't.
- Background clipboard writes and service lifecycle are implementation-dependent on Android/OxygenOS; verify on the actual device. Default to explicit user action when uncertain.
- Apple `pbcopy` and `pbpaste` are convenient text adapters but not a complete rich-clipboard solution; no photo/file support is claimed.
- macOS screen recording/accessibility permissions are not required for simple clipboard text sync and must not be requested unnecessarily.
