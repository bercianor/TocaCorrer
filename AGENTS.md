# AGENTS.md - Architecture and Project Requirements Documentation

## Development Decisions

- **Build:** Kotlin DSL (.gradle.kts)
- **JDK:** JDK 21 (21.0.9-tem)
- **Android SDK:** API 24 minimum (Android 7.0), target API 35, compile SDK 35
- **Gradle:** 8.11.1
- **Build System:** Gradle with Kotlin DSL

---

## 1. Project Overview

**App Name:** TocaCorrer
**Description:** Native Android running training tracker with voice-guided assistance, based on reading routines from the local calendar.

**MAIN RESTRICTION:** The app must be completely independent from Google Play Services (De-Googled). No Google APIs will be used (neither FusedLocationProvider, nor Google Maps SDK, nor direct integrations with Google cloud APIs).

## 2. Technology Stack

- **Language:** Kotlin.
- **UI:** Jetpack Compose.
- **Local Database:** Room (SQLite).
- **Location:** Native `android.location.LocationManager` (GPS Hardware).
- **Background Execution:** `Foreground Service` with persistent notification.
- **Voice and Haptics:** Native Android `TextToSpeech` and `Vibrator`.
- **Maps:** NO map rendering on screen. Everything is based on data collection and statistics generation.
- **Backup/Restore:** Moshi for JSON serialization (streaming export via `JsonWriter`).

## 3. Project Structure

```
app/src/main/java/es/bercianor/tocacorrer/
├── data/
│   ├── backup/
│   │   └── BackupManager.kt         # Backup/restore JSON (streaming export, atomic import via withTransaction)
│   ├── calendar/
│   │   └── CalendarManager.kt       # System calendar reading
│   ├── export/
│   │   ├── GpxGenerator.kt          # GPX 1.1 XML Generator
│   │   ├── GpxExporter.kt           # File export/share
│   │   └── GpxSegmentationMode.kt   # Enum: NONE | TRACKS | SEGMENTS
│   ├── local/
│   │   ├── AppDatabase.kt           # Room Database (version 3, explicit migrations 1→2→3)
│   │   ├── PreferencesManager.kt    # SharedPreferences wrapper (calendar, dark mode, language, auto-pause, GPX mode, chart metric)
│   │   ├── dao/                     # Data Access Objects
│   │   │   ├── WorkoutDao.kt
│   │   │   └── GpsPointDao.kt
│   │   └── entity/                  # Room Entities
│   │       ├── Workout.kt
│   │       └── GpsPoint.kt
│   └── repository/
│       └── WorkoutRepository.kt     # Takes AppDatabase as first constructor param (for withTransaction support)
├── domain/
│   ├── model/
│   │   ├── TrainingPhase.kt         # PhaseType (enum), TrainingPhase, WorkoutRoutine — pure Kotlin, no Android deps
│   │   ├── Statistics.kt            # Statistics data class (totalDistanceMeters, totalDurationSeconds, workoutCount)
│   └── parser/
│       └── WorkoutParser.kt         # DSL Parser
├── service/
│   ├── WorkoutService.kt            # Main Foreground Service (NonCancellable save, StateFlow.update{}, GPS accuracy filter)
│   ├── LocationTracker.kt           # GPS tracking with LocationManager (filters accuracy > 20f)
│   ├── PhaseTimer.kt                # Phase timer (coroutine-based)
│   └── TtsManager.kt               # Text-to-Speech and vibration (ConcurrentLinkedQueue)
├── ui/
│   ├── SettingsScreen.kt            # Settings (calendar, theme, language, backup, GPX mode, chart metric)
│   ├── StatisticsScreen.kt          # History & stats with charts (receives StatisticsViewModel, not repository)
│   ├── MainActivity.kt              # Navigation + lifecycle (repeatOnLifecycle, StatisticsViewModel created here)
│   ├── MainScreen.kt                # Home screen (safe Activity cast, ActiveWorkoutScreen hoisted here)
│   ├── MainViewModel.kt             # AndroidViewModel (uses getApplication<Application>(), no Context params)
│   ├── components/
│   │   └── Charts.kt               # Bar charts for stats (Paint hoisted with remember())
│   └── theme/
│       ├── Theme.kt                 # Dark/Light mode support
│       └── Type.kt                  # Typography definitions
├── util/
│   ├── PermissionManager.kt         # Runtime permission management
│   └── Strings.kt                   # Bilingual UI strings (English/Spanish); currentLanguage: Int field, no static Context
└── TocaCorrerApp.kt
```

## 4. Implemented Features

### 4.1. Calendar Integration (Nextcloud/DAVx5)

- **Permission:** `READ_CALENDAR`
- **Implementation:** `CalendarContract` to search today's events
- **Auto-detection:** Keywords in title (running, correr, entreno, etc.) or routine format in description
- **Calendar selection:** User can choose which calendar to use in Settings

### 4.2. Training DSL Parsing

- **Supported format:**
  - `D` = Rest/Walk
  - `T` = Trot/Jog
  - `R` = Easy run (Rodaje)
  - `RA` = Easy cheerful (Rodaje alegre)
  - `RF` = Strong easy run (Rodaje fuerte)
  - `F` = Fartlek
  - `P` = Progressives
  - `RC` = Race pace (Ritmo de competición)
  - `X` = Extra (fallback phase)
  - Durations: `R10` = Easy run 10 minutes, `D1` = Rest 1 minute
  - Distance-based: `R5km` = Easy run 5 km
  - Series: `4x(R3 - D1)` = 4 repetitions of Easy run 3 min + Rest 1 min
  - Combined: `R5 - 4x(R3 - D1) - R5`

### 4.3. Tracking and Engine (Foreground Service)

- `Foreground Service` with persistent notification (throttled, not rebuilt every second)
- Decremental timer per phase
- Voice announcements (TTS) on phase change and countdown (3, 2, 1)
- Vibration on phase changes
- GPS tracking with native `LocationManager` (discards points with accuracy > 20 m)
- Real-time distance and pace calculation
- **Auto-pause:** Automatically pauses if speed < 0.5 m/s for 10+ seconds (configurable)
- **Auto-resume:** Resumes when movement is detected
- **Data safety:** `saveFinalStats()` wrapped in `withContext(NonCancellable)` to prevent data loss on cancellation
- **Thread safety:** All `WorkoutStatus` state updates use `StateFlow.update {}` to prevent race conditions

### 4.4. Database (Room)

**Current version:** 3  
**Migrations:**
- v1 → v2: Added `phase_index` column to `gps_points` (disambiguates repeated phase letters for GPX export)
- v2 → v3: Added index on `startTime` column of `workouts` (speeds up date-range queries)

**Entity `Workout`** (table: `workouts`):
- `id` (PK, auto-generated)
- `startTime` (Long, timestamp in ms) — indexed
- `originalRoutine` (String, e.g., "R10 - D1 - R10")
- `totalDistanceMeters` (Double)
- `totalDurationSeconds` (Long)
- `averagePaceMinPerKm` (Double)
- `noGps` (Boolean, true = treadmill workout)

**Entity `GpsPoint`** (table: `gps_points`):
- `id` (PK, auto-generated)
- `workoutId` (FK → Workout.id, CASCADE delete)
- `latitude`, `longitude`, `altitude` (Double)
- `timestamp` (Long, in ms)
- `phase` (String: phase letter, e.g., "R", "D")
- `phase_index` (Int: 0-based index for disambiguating repeated phases)

### 4.5. GPX Export

- Native GPX 1.1 format (no external libraries)
- **Segmentation modes** (`GpxSegmentationMode`):
  - `NONE`: All points in a single `<trkseg>` within a single `<trk>` (default)
  - `TRACKS`: Consecutive same-phase points grouped into separate `<trk>` elements with `<name>`
  - `SEGMENTS`: Consecutive same-phase points grouped into separate `<trkseg>` within one `<trk>`
- Options: Save (SAF) or Share (FileProvider)

### 4.6. Backup and Restore

- JSON export using Moshi (streaming via `JsonWriter` — safe for large datasets)
- JSON import wrapped in `database.withTransaction {}` (atomic — all-or-nothing)
- Automatic filename: `tocacorrer_backup_YYYY-MM-DD.json`

### 4.7. User Interface

- **Main Screen:** Shows upcoming workouts from calendar (`calendarDays` preference, default 5)
- **History/Statistics:** Bar charts (weekly/monthly, metric configurable: distance/time/pace), delete workouts, export
- **Settings:**
  - Calendar selection + days ahead
  - Dark mode (System/Light/Dark)
  - Language (System/English/Spanish)
  - Auto-pause (On/Off + configurable time in seconds)
  - GPX segmentation mode (None/Tracks/Segments)
  - Weekly chart metric (Distance/Time/Pace)
  - Backup/Restore

### 4.8. Preferences (`PreferencesManager`)

Stores user settings via `SharedPreferences`:
- `selectedCalendarId` / `selectedCalendarName`
- `darkMode` (0 = system, 1 = light, 2 = dark)
- `language` (0 = system, 1 = English, 2 = Spanish)
- `autoPause` (Boolean)
- `autoPauseTime` (Int, seconds)
- `calendarDays` (Int, default 5)
- `primaryColorIndex` (Int, 0 = default purple)
- `gpxSegmentationMode` (String — stored by enum name, auto-migrates old int ordinal)
- `weeklyChartMetric` (Int, 0 = distance, 1 = time, 2 = pace)

### 4.9. Internationalization (`util/Strings.kt`)

- Bilingual support: English (default) and Spanish
- Language configurable via `Strings.currentLanguage: Int` (no static Context)
- Falls back to English when a Spanish key is missing

---

## 5. Architecture Rules

- **Domain layer** (`domain/`) is pure Kotlin — zero Android imports.
- **ViewModels** use `getApplication<Application>()` internally — no `Context` parameters in public methods.
- **Composables** receive ViewModels or state/callbacks — they never instantiate repositories, DAOs, or managers directly.
- **`StatisticsViewModel`** is created at Activity level in `MainActivity`, not inside the Composable.
- **Flow collection** in Activity uses `repeatOnLifecycle(Lifecycle.State.STARTED)`.
- **`WorkoutService`** has no dependency on `MainActivity` — uses `getLaunchIntentForPackage` for the notification tap intent.

---

## 6. Unit Tests

### Existing Tests:

1. **WorkoutParserTest** (`app/src/test/java/es/bercianor/tocacorrer/domain/parser/`)
   - DSL parser tests
   - Cases: simple phases, series, combined, errors

2. **GpxGeneratorTest** (`app/src/test/java/es/bercianor/tocacorrer/data/export/`)
   - GPX generator tests
   - Verifies XML structure, character escaping, segmentation modes

3. **GpxExporterTest** (`app/src/test/java/es/bercianor/tocacorrer/data/export/`)
   - GPX file export tests

4. **WorkoutTest** and **GpsPointTest** (`app/src/test/java/es/bercianor/tocacorrer/data/local/entity/`)
   - Room entity tests

5. **PhaseTimerTest** (`app/src/test/java/es/bercianor/tocacorrer/service/`)
   - Phase timer tests
   - Cases: start, pause, resume, progress, completed

6. **LocationTrackerTest** (`app/src/test/java/es/bercianor/tocacorrer/service/`)
   - Distance and pace calculation tests
   - Cases: speed, auto-pause thresholds

7. **BackupManagerTest** (`app/src/test/java/es/bercianor/tocacorrer/data/backup/`)
   - JSON serialization tests
   - Cases: entity conversion, file format

8. **TtsManagerTest** (`app/src/test/java/es/bercianor/tocacorrer/service/`)
   - TTS text formatting tests
   - Cases: distance/pace formatting, announcements, vibration patterns

9. **TrainingPhaseTest** (`app/src/test/java/es/bercianor/tocacorrer/domain/model/`)
   - Domain model tests for `TrainingPhase` and `PhaseType`
   - Cases: description, letter, duration, series number

10. **WorkoutRoutineTest** (`app/src/test/java/es/bercianor/tocacorrer/domain/model/`)
    - Domain model tests for `WorkoutRoutine`
    - Cases: empty routine, phase access, interval series, total duration

11. **WorkoutRepositoryTest** (`app/src/test/java/es/bercianor/tocacorrer/data/repository/`)
    - Repository layer tests using Mockito
    - Cases: CRUD operations, statistics, flows

12. **PreferencesManagerTest** (`app/src/test/java/es/bercianor/tocacorrer/data/local/`)
    - SharedPreferences wrapper tests using Mockito
    - Cases: calendar, dark mode, language, auto-pause, gpxSegmentationMode (stored by name)

13. **WorkoutServiceDistanceTest** (`app/src/test/java/es/bercianor/tocacorrer/service/`)
    - WorkoutService distance-based phase logic tests

14. **MainViewModelTest** (`app/src/test/java/es/bercianor/tocacorrer/ui/`)
    - MainViewModel logic tests (pure JVM, no Robolectric)

15. **StringsTest** (`app/src/test/java/es/bercianor/tocacorrer/util/`)
    - Bilingual string utility tests

---

## 7. Build Commands

### Build debug APK:
```bash
./gradlew assembleDebug
```

### Build release APK:
```bash
./gradlew assembleRelease
```

### Run tests:
```bash
./gradlew test
```

### Clean build:
```bash
./gradlew clean
```

---

## 8. Important Notes

- **No Google Play Services:** Everything is native Android
- **Required permissions:**
  - `ACCESS_FINE_LOCATION` (GPS)
  - `ACCESS_COARSE_LOCATION`
  - `READ_CALENDAR`
  - `POST_NOTIFICATIONS` (Android 13+)
- **Calendar must be synced** with Nextcloud (or another server) using DAVx5 or similar
- **TTS** uses the app language setting (`PreferencesManager.language`), not the system default
- **Bilingual app:** English and Spanish, configurable via `Strings.currentLanguage` and `PreferencesManager.language`
- **Database migrations:** Never use `fallbackToDestructiveMigration()` — always provide explicit `Migration` objects
- **GPS accuracy:** Points with `accuracy > 20f` are discarded before distance calculation
