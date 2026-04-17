# Koda 🐾

AI coding agent for Android. One APK, everything works.

## What is this?

Koda is a self-contained Android app that runs [OpenClaude](https://github.com/gitlawb/openclaude) — an AI coding agent — directly on your phone. No external apps needed.

## Architecture

- **Base:** Clean fork of [termux-app](https://github.com/termux/termux-app)
- **Runtime:** Termux bootstrap (bash, apt, coreutils) + Node.js + OpenClaude
- **UI:** Native Android (Java, XML layouts)
- **JNI:** Custom `libkoda-process.so` for subprocess management

## Flow

```
Launch → Permissions → Bootstrap extraction → Install Node.js + OpenClaude → API key → Chat
```

## Building

1. Open in Android Studio
2. Connect ARM64 phone via USB
3. ▶️ Run

**Note:** Must test on a real ARM64 device. x86_64 emulators cannot run the ARM64 bootstrap.

## License

See individual module licenses (Termux: GPL-3.0, terminal-emulator/terminal-view: Apache-2.0).
