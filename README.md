# 📸 Photobooth

A full-featured Android photobooth app that controls a **Nikon DSLR** camera directly over **USB OTG** — no WiFi adapter, no third-party app, no PC required. Just plug your camera into an Android tablet and you have an instant event photobooth.

Built with Jetpack Compose, Kotlin Coroutines, and a from-scratch PTP (Picture Transfer Protocol) implementation.

---

## Features

### 🎯 One-Tap Photo Experience
Guests tap the screen to start. A configurable "get in position" phase leads into a color-coded countdown with audio beeps, autofocus locks, and a full-screen flash on capture. The photo appears instantly for preview and sharing.

### 📡 Live View
Real-time camera preview streamed at ~15fps directly from the DSLR sensor via PTP. Optional mirror mode for a natural selfie feel.

### 📱 Instant Sharing
Three ways to get photos off the booth:
- **QR Code** — scanned from the preview screen, links to an on-device HTTP server for instant browser download
- **Nearby Share** — one-tap sharing to any nearby Android device
- **Auto-save** — every photo saved to the tablet gallery (`Pictures/Photobooth/`)

### ⚙️ Settings Menu
- **Language** — Danish / English (easily extensible)
- **Countdown duration** — 3 to 10 seconds
- **Preparation time** — 0 to 10 seconds ("get in position" phase)
- **Mirror live view** — horizontal flip toggle
- **Watermark** — custom text overlay on captured photos

### 🔄 Resilient Connection
Auto-reconnect with retry logic, health checks via PTP DeviceReady, and graceful error recovery. Designed to survive hours of continuous use at an event without intervention.

---

## How It Works

```
┌─────────┐    USB OTG     ┌──────────┐    WiFi     ┌──────────┐
│  Nikon   │◄─────────────►│ Android  │◄───────────►│ Guest's  │
│  DSLR    │  PTP Protocol │ Tablet   │  HTTP/QR    │  Phone   │
└─────────┘                └──────────┘              └──────────┘
```

The app implements the **PTP (Picture Transfer Protocol)** over USB bulk transfers to control the camera directly:

1. **Session management** — OpenSession / CloseSession lifecycle
2. **Live view streaming** — Nikon-specific StartLiveView + frame polling
3. **Autofocus** — AF Drive command triggered during countdown
4. **Capture** — InitiateCapture with event-driven completion (ObjectAdded + CaptureComplete)
5. **Download** — GetObject to pull the full-resolution JPEG over USB

---

## Architecture

| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose + Material3 |
| **State** | ViewModel + StateFlow |
| **Async** | Kotlin Coroutines (IO/Main dispatchers) |
| **Camera** | Custom PTP/USB implementation |
| **Transport** | Android USB Host API (bulk IN/OUT) |
| **Sharing** | ZXing QR codes + built-in HTTP server |
| **Storage** | MediaStore API |
| **Settings** | SharedPreferences with StateFlow wrappers |

### State Machine

```
NotConnected → Idle → Countdown → Capturing → Preview → Idle
                 ↑                     │
                 └──── Error ──────────┘
```

---

## Supported Hardware

### Cameras
- **Nikon D850** (tested and confirmed)
- **Nikon D800** (tested and confirmed)
- Should work with any Nikon DSLR that supports PTP live view
- Fallback detection for any USB device with PTP interface class (0x06)

### Tablets / Phones
- Any Android device with **USB OTG** support
- **Tested on:** Lenovo Tab P12
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)

### Accessories
- USB OTG adapter (USB-C to USB-A)
- Standard USB cable for camera

---

## Quick Start

1. **Clone the repo**
   ```bash
   git clone https://github.com/fokodk/photobooth.git
   ```

2. **Open in Android Studio** and build, or use Gradle directly:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install** on your tablet:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Plug in your Nikon** via USB OTG — the app launches automatically

5. **Tap the screen** to take photos!

---

## Camera Setup Tips

- Set the camera to **Manual Focus** or **Release Priority** mode to avoid AF failures blocking captures
- Use **S (Small)** JPEG size for faster transfers over USB
- Keep the camera powered via AC adapter for long events
- The camera lens cap should obviously be off 😄

---

## Technical Highlights

### PTP Implementation
Built from scratch, inspired by [remoteyourcam-usb](https://github.com/nickinchrisma/remoteyourcam-usb) and libgphoto2:
- **16KB reusable read buffer** — eliminates GC pressure from per-read allocations
- **Atomic transactions** — command + data + response bundled to prevent protocol desync
- **Zero-byte read retry** — handles transient USB empty reads (up to 50 retries)
- **Multi-packet data streaming** — reconstructs large JPEGs from chunked USB transfers
- **Differentiated timeouts** — 3s writes, 10s commands, 30s data, 2s live view

### Resilience
- Auto-reconnect loop (30 attempts, 2s backoff)
- Capture retry logic (3 attempts, 1.5s delay)
- Live view error tolerance (5 consecutive errors before disconnect)
- DeviceReady polling after live view start (15 attempts)
- Health check before every countdown

### Performance
- ToneGenerator on IO dispatcher (prevents UI thread blocking)
- Bitmap downsampling for preview (1600px max)
- In-memory photo serving (no disk I/O for network shares)
- Frame time compensation for consistent live view FPS

---

## Project Structure

```
app/src/main/java/com/photobooth/
├── MainActivity.kt          # USB permission handling, app entry
├── PhotoboothViewModel.kt   # State management, capture flow
├── PhotoServer.kt           # HTTP server for QR code downloads
├── AppSettings.kt           # Persistent settings (SharedPreferences)
├── Strings.kt               # i18n strings (Danish/English)
├── ptp/
│   ├── PtpCamera.kt         # Camera operations (capture, live view, AF)
│   ├── PtpConnection.kt     # USB transport layer (bulk read/write)
│   ├── PtpConstants.kt      # PTP opcodes, response codes, timeouts
│   └── PtpPacket.kt         # Packet serialization/deserialization
└── ui/
    ├── PhotoboothApp.kt     # Navigation and state routing
    ├── WelcomeScreen.kt     # Idle screen with tap-to-start
    ├── CountdownScreen.kt   # Live view + countdown + beeps
    ├── CapturingScreen.kt   # Capture-in-progress animation
    ├── PreviewScreen.kt     # Photo preview + QR + sharing
    ├── ErrorScreen.kt       # Error display with retry
    ├── SettingsScreen.kt    # Configuration UI
    └── theme/Theme.kt       # Dark theme definition
```

---

## ☕ Support

If you find this useful, consider buying me a coffee!

[![PayPal](https://img.shields.io/badge/PayPal-Buy%20me%20a%20coffee-blue?logo=paypal&logoColor=white)](https://www.paypal.com/ncp/payment/B67VSF84HA2R2)

---

## License

Licensed under the [Apache License 2.0](LICENSE).

```
Copyright 2026 fokodk

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

*Built with ❤️ and [Claude Code](https://claude.ai/claude-code) for a Saturday event photobooth.*
