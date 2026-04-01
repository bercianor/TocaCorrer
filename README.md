# TocaCorrer

> Native Android running trainer with voice guidance, calendar integration and zero Google dependency.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple.svg)](https://kotlinlang.org)

**[Versión en español](README.es.md)**

---

## What is TocaCorrer?

TocaCorrer ("time to run" in Spanish) is a fully offline, De-Googled Android app that reads your running workouts from your local calendar and guides you through each phase with voice announcements and haptic feedback.

No Google Play Services. No tracking. No cloud. Just you and the road.

---

## Features

- **Calendar-driven workouts** — reads today's training sessions from your system calendar (Nextcloud / DAVx⁵ compatible)
- **Training DSL** — define intervals in a simple format: `D5 - 4x(R3 - D1) - D5`
- **8 phase types** — Rest (D), Jog (T), Easy run (R), Easy cheerful (RA), Easy strong (RF), Fartlek (F), Progressives (P), Race pace (RC)
- **Distance-based phases** — mix time and distance: `D5 - 3x(R1k - D1) - D5` shows `13 min + 3 km`
- **Voice guidance** — native Android TTS announces phase changes, 500 m checkpoints and countdowns
- **GPS tracking** — native `LocationManager`, no Google Maps SDK
- **Auto-pause / Auto-resume** — pauses when speed drops below 0.5 m/s for a configurable number of seconds
- **Treadmill mode** — trains without GPS, manual phase advance
- **Foreground service** — keeps running when the screen is off
- **GPX 1.1 export** — share or save your routes
- **Backup & Restore** — JSON export / import, fully local
- **Statistics** — weekly and monthly bar charts
- **Bilingual** — English and Spanish, switchable in settings
- **Dark / Light / System theme**

---

## Training DSL

| Token | Meaning | Example |
|-------|---------|---------|
| `D` | Rest / Walk | `D5` = rest 5 min |
| `T` | Jog | `T10` = jog 10 min |
| `R` | Easy run | `R1k` = easy run 1 km |
| `RA` | Easy cheerful | `RA20` = 20 min |
| `RF` | Easy strong | `RF5` = 5 min |
| `F` | Fartlek | `F30` = 30 min |
| `P` | Progressives | `P20` = 20 min |
| `RC` | Race pace | `RC5` = 5 min |
| `Nx(...)` | Series | `4x(R3 - D1)` = 4 reps |

**Example:** `D5 - 4x(R3 - D1) - D5` → warm-up 5 min, 4 × (easy 3 min + rest 1 min), cool-down 5 min

Add this to your calendar event description and TocaCorrer picks it up automatically.

---

## Requirements

- Android 7.0+ (API 24)
- Calendar synced via [DAVx⁵](https://www.davx5.com/) or any CalDAV client (Nextcloud, etc.)
- GPS permission for outdoor tracking
- TTS engine installed (the default Android TTS engine works fine)

---

## Installation

TocaCorrer is **not distributed via Google Play**. Download the latest APK from the [Releases](https://github.com/bercianor/releases) page:

1. Download `tocacorrer-vX.Y.Z.apk`
2. Verify the checksum: `sha256sum -c tocacorrer-vX.Y.Z.apk.sha256`
3. Enable _Install from unknown sources_ in your Android settings
4. Install the APK

---

## Building from source

```bash
# Clone the repository
git clone https://github.com/bercianor/TocaCorrer.git
cd TocaCorrer

# Debug build
./gradlew assembleDebug

# Run tests
./gradlew test

# Release build (requires signing config — see below)
./gradlew assembleRelease
```

### Signing a release build locally

```bash
# Generate a keystore (one-time setup)
keytool -genkey -v -keystore tocacorrer.jks -alias tocacorrer \
  -keyalg RSA -keysize 2048 -validity 10000

# Export environment variables
export KEYSTORE_PATH=/path/to/tocacorrer.jks
export KEYSTORE_PASSWORD=your_store_password
export KEY_ALIAS=tocacorrer
export KEY_PASSWORD=your_key_password

./gradlew assembleRelease
```

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Database | Room (SQLite) |
| Location | `android.location.LocationManager` (native GPS) |
| Background | Foreground Service |
| Voice | Android `TextToSpeech` |
| Haptics | Android `Vibrator` |
| Serialization | Moshi (backup / restore) |
| Build | Gradle 8 + Kotlin DSL |
| Min SDK | API 24 (Android 7.0) |

**No Google Play Services. No Firebase. No analytics.**

---

## Permissions

| Permission | Purpose |
|-----------|---------|
| `ACCESS_FINE_LOCATION` | GPS tracking |
| `ACCESS_COARSE_LOCATION` | GPS fallback |
| `FOREGROUND_SERVICE` | Background execution |
| `FOREGROUND_SERVICE_LOCATION` | GPS while screen is off |
| `POST_NOTIFICATIONS` | Workout notification (Android 13+) |
| `VIBRATE` | Phase-change haptics |
| `READ_CALENDAR` | Read workout from calendar |
| `WAKE_LOCK` | Keep CPU active during workout |

---

## Privacy

- All data stays on your device
- No internet permission declared
- No analytics or crash reporting
- GPS data is stored locally in SQLite and exported only on your request

---

## License

[MIT](LICENSE) © 2026 Rubén (bercianor)
