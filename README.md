<div align="center">

# Lootly

**Twitch Watch-Time Badge & In-Game Drops Miner for Android**

![Architecture](https://img.shields.io/badge/arch-arm64--v8a-blue)
![Android](https://img.shields.io/badge/Android-8.0%20--%2017-green)
![License](https://img.shields.io/badge/license-GPL--3.0-orange)

</div>

> **Disclaimer:** Lootly is an independent, open-source project and is not affiliated with, endorsed, or sponsored by Twitch Interactive, Inc. or StreamDatabase. Automating watch-time or interacting with Twitch's internal APIs falls outside Twitch's official Terms of Service—while single-stream watch-time clients rarely face enforcement, use this software at your own discretion and risk.

---

## 1. Overview & Features

Lootly (`com.lootly.app`) is a native Kotlin Android app (Jetpack Compose Material 3 dark UI) for automated Twitch watch-time global badge and in-game drop farming with zero manual token setup and minimal background data usage.

- **Architecture:** `arm64-v8a`
- **Android versions:** Android 8.0 to 17 (minSdk 26, targetSdk 35)

### Features

1. **WebView Login & Auto-Start:**
   - Native `LoginActivity` loads `https://www.twitch.tv/login` and extracts `auth-token`, `twilight-user`, and `unique_id` cookies directly from Android `CookieManager`.
   - Credentials are saved in Android Keystore-backed `EncryptedSharedPreferences` (`MasterKey` AES-256 GCM).
   - Starts background mining automatically on app launch (when signed in) and right after login—no copy-pasting of OAuth tokens, Client IDs, or passwords.

2. **Headless WebView Integrity Bridge:**
   - Runs an isolated offscreen Android WebView sharing the logged-in session with image, video, and font loading disabled.
   - Captures and caches `Client-Integrity` tokens, `User-Agent`, `X-Device-Id`, and `Client-Session-Id` headers required by Twitch GQL.
   - Exposes a local loopback HTTP bridge (`127.0.0.1`) secured with a per-session random token to supply fresh headers to the Go engine on demand, regenerating tokens on integrity challenge errors.

3. **Streamless HLS Metadata & HEAD Watch Mode:**
   - Fetches only HLS playlists (`.m3u8`) and sends HTTP `HEAD` requests to lowest-quality video segments (`usher.ttvnw.net`) alongside spade heartbeat telemetry instead of downloading video.
   - Counts watch-time minutes toward badges and drops while using less than 1% of normal video data and CPU.

4. **Global Badges & In-Game Drops Discovery, Multi-Tier Handling & Account Linking:**
   - **Global Badges & In-Game Drops:** Automatically scans, farms, and claims free watch-to-earn in-game drops alongside global watch-time badges (indexed from Twitch GQL Drops Inventory and `streamdatabase.com/twitch/global-badges` every hour with a random 4-minute offset).
   - **Independent Badges / Drops Toggles:** Turn Global Badges or In-Game Drops on and off independently right from the main screen.
   - **In-App Account Linking:** Unlinked game drop campaigns appear at the bottom of the queue; tapping one opens Twitch's account-linking page directly inside the app and automatically refreshes drop eligibility when you return.
   - **Multi-Tier & Daily Cap Handling:** Resolves multi-tier badge and drop progressions in ascending tier order, calculates per-tier step durations dynamically, prioritizes items in Auto order (earliest expiration date first, then shortest unowned tier), and rotates away from multi-day events once a daily watch cap is reached.

5. **Live Watch Drops Sync, Manual Target Selection & Smarter Stream Switching:**
   - **Live Per-Channel Watch Drops Sync (`DropCurrentSessionContext`):** Syncs watched and remaining minutes directly from Twitch for channel-level Watch Drops and badge celebrations, combining session watch time with Twitch GQL inventory syncs so progress updates continuously.
   - **Manual Mode (`TARGET` Tile):** Tap any badge or drop in the eligible queue to select and mine it immediately via the **TARGET** (`Auto` / `Manual`) tile. Tap **TARGET** (or **Reset Auto**) to return to automatic priority, or let it auto-reset once the manually selected item completes or hits its daily cap.
   - **Smarter Stream Switching & Pacing:** Verifies that the selected stream actually has the target drop active and automatically switches streams if watch-time progress stalls for 2 minutes or the streamer goes offline. Uses a randomized 20 to 55 second claim delay upon completion and a 5 to 12 second cooldown between stream switches.

6. **Twitch App Auto-Pause (`UsageStatsManager`):**
   - `AppUsageMonitor` checks foreground app usage every 3 seconds.
   - Pauses watch heartbeats immediately when the official Twitch app (`tv.twitch.android.app`) is opened to prevent concurrent stream conflicts, and resumes automatically when closed (toggleable in settings).

7. **Foreground Service & Zero-Battery Standby:**
   - `MinerForegroundService` runs as a `DATA_SYNC` foreground service (`START_STICKY`) with a partial WakeLock, handling Android 15 and 16+'s 6-hour rolling `dataSync` limit via `onTimeout()`.
   - Shows an ongoing notification with badge or drop progress, percentage, estimated time remaining, and Pause, Resume, and Stop controls.
   - Enters Zero-Battery Standby when no eligible items remain, releasing the partial WakeLock until the next catalog scan.

8. **Redesigned Single-Screen Dark UI:**
   - Cleaner single-screen dark layout featuring minimal icon controls, independent **Badges** and **Drops** toggles, and an auto-hiding Permission Health Bar (collapses once Usage Stats, Battery Optimization, and Notifications are granted).
   - **Liquid Progress Fill:** Active badge or drop card with liquid progress fill animation, icon, title, streamer name, category, split percentage and remaining time estimate, and **TARGET** mode indicator (`Auto` or `Manual`).
   - **Inline Expandable Queue:** Expandable list for eligible drops and badges sorted in Auto priority order (manually selected item first, then earliest expiration date, with unlinked drops grouped at the bottom for one-tap in-app account linking).
   - **Flat Log Console:** Permanently expanded flat monospace console with `HH:mm:ss` timestamps, color-coded log levels, and debug spam filtering.

---

## 2. Architecture & Build Instructions

Lootly consists of two components:
1. Native ARM64 Go binary compiled as a shared library (`libtwitchminer.so`) inside the app's native library directory.
2. Native Kotlin / Jetpack Compose Android app (`com.lootly.app`, packaged as `Lootly.apk`).

### Directory Layout

    twitch-badge-miner-android/
    ├── go-miner/                       # Vendored Go engine with Android changes
    │   ├── cmd/twitch-miner-go/        # Main Go entrypoint
    │   └── internal/                   # GQL, Twitch, Watcher, Server, Model, AndroidNet packages
    ├── app/                            # Android Gradle module
    │   └── src/main/
    │       ├── java/                   # Kotlin source code
    │       │   ├── bridge/             # Headless WebView integrity bridge & HTTP server
    │       │   ├── data/               # Models, EncryptedSharedPreferences, State Repository
    │       │   ├── engine/             # ProcessManager, LogParser, ControlClient, ConfigGenerator
    │       │   ├── monitor/            # AppUsageMonitor & PermissionHelper
    │       │   ├── service/            # MinerForegroundService & NotificationManager
    │       │   └── ui/                 # Jetpack Compose UI (Dashboard, Cards, Theme, Login)
    │       ├── jniLibs/arm64-v8a/      # Output path for libtwitchminer.so
    │       └── res/                    # Adaptive icons, layouts, themes, strings
    └── .github/workflows/build-apk.yml # GitHub Actions CI build

### Compiling the Go Engine (Android ARM64)

    cd go-miner
    GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build \
      -trimpath \
      -ldflags="-s -w" \
      -o ../app/src/main/jniLibs/arm64-v8a/libtwitchminer.so \
      ./cmd/twitch-miner-go

`libtwitchminer.so` is bundled into `lib/arm64-v8a/` inside the APK and extracted by Android at install time into the app's `nativeLibraryDir` with executable permissions.

### Building the Android APK (Local)

Requirements:
- Android SDK (API 35 platform, build-tools `35.0.0` or `34.0.0`)
- JDK 17 or 21
- Android `aapt2`

Run:

    ./gradlew testDebugUnitTest
    ./gradlew assembleDebug

Output APK:
`app/build/outputs/apk/debug/app-debug.apk` (released as `Lootly.apk`)

### GitHub Actions CI

`.github/workflows/build-apk.yml` runs automatically on pushes to `main`:
1. Sets up Go and Android SDK 35 on `ubuntu-latest`.
2. Runs Go and Kotlin unit tests.
3. Cross-compiles `libtwitchminer.so` for `arm64-v8a`.
4. Assembles the APK and uploads it to the workflow **Artifacts**.

---

## 3. Android Permissions

Declared in `app/src/main/AndroidManifest.xml`:

1. **`android.permission.INTERNET`:** Connects to Twitch GQL APIs, HLS playlists, segment servers, Twitch account-linking pages, the local loopback integrity bridge, and loads badge and drop icons via Coil.
2. **`android.permission.ACCESS_NETWORK_STATE`:** Detects Wi-Fi and cellular connectivity changes to pause or resume network activity.
3. **`android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_DATA_SYNC`:** Keeps `MinerForegroundService` running in the background (`dataSync` type) while the screen is off or the app is minimized, handling Android 15 and 16+'s 6-hour rolling `dataSync` limit via `onTimeout()`.
4. **`android.permission.POST_NOTIFICATIONS`:** Shows the ongoing notification (progress percentage, badge or drop name, remaining time, and Pause, Resume, and Stop buttons) and alerts when a reward is claimed (Android 13 and newer).
5. **`android.permission.WAKE_LOCK`:** Holds a `PARTIAL_WAKE_LOCK` while an eligible badge or drop is actively mining so watch heartbeats continue with the screen off; released when no items remain.
6. **`android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`:** Prompts to exempt Lootly from Doze and OEM background battery restrictions.
7. **`android.permission.PACKAGE_USAGE_STATS`:** Used by `AppUsageMonitor` to detect when the official Twitch app (`tv.twitch.android.app`) is in the foreground so Auto-Pause can halt watch heartbeats.

---

## 4. Security & Privacy

- **No Analytics:** Zero tracking SDKs, telemetry, Firebase, or ad libraries.
- **Encrypted Credentials:** Session cookies and tokens are stored in Android Keystore `EncryptedSharedPreferences`; no plaintext credentials are written to disk or shared storage.
- **Local Loopback IPC:** Communication between the Kotlin app and Go binary uses `127.0.0.1` HTTP sockets authenticated with a per-session random secret.
- **Standard App Sandbox:** Runs inside the standard Android app sandbox without root or Shizuku.

---

## 5. Upstream & Attribution

1. **Upstream Go Engine:**
   - **Repository:** https://github.com/Guliveer/twitch-miner-go
   - **Author:** Guliveer
   - **Vendored Commit:** `e98e7d639a9397a01d2a245ed35df489ff61c3ed`
   - **License:** GNU General Public License v3.0 (GPL-3.0)

2. **Badge Catalog Source:**
   - **Source:** https://www.streamdatabase.com/twitch/global-badges
   - Used to index newly deployed Twitch global watch-time badge campaigns.

3. **Android Changes Added:**
   - Pure-Go Android DNS resolver (`internal/androidnet/dns_android.go`) bypassing Android's missing `/etc/resolv.conf`, with Cloudflare DoH (`1.1.1.1`) and system DNS fallback (handles local VPN firewalls like AdGuard blocking `spade.twitch.tv`).
   - Android 14+ Conscrypt system CA certificate discovery (`SSL_CERT_DIR`).
   - Headless WebView `Client-Integrity` HTTP bridge client (`internal/auth/`, `internal/gql/`).
   - Multi-tier global badge and in-game drop discovery, StreamDatabase to Twitch Inventory matcher, unlinked drop surfacing, dynamic step duration calculator, priority queue sorter, randomized scan, switch, and claim delays, and daily watch-cap rotator (`internal/watcher/streamdatabase.go`, `internal/watcher/badge.go`, `internal/watcher/loop.go`, `internal/twitch/drops.go`).
   - Live per-channel Watch Drops sync (`DropCurrentSessionContext`), active stream drop verification, 2-minute stall detection with automatic stream failover, and live progress calculation in `/api/streamers`.
   - Loopback HTTP control endpoints (`POST /api/control/pause`, `POST /api/control/resume`, `POST /api/control/refresh-badges`, `POST /api/control/pin-badge`, and category toggle controls).
   - Stripped desktop Cgo/X11 systray dependencies for pure-Go Android ARM64 builds.

---

## 6. License

Lootly and its vendored engine are licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

See `go-miner/LICENSE.txt` for the full license text. Any redistribution or modification must provide source code under the terms of GPL-3.0.
