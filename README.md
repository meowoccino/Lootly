**Twitch Watch-Time Badge Miner for Android**

> **Disclaimer:** Lootly is an independent, open-source project and is not affiliated with, endorsed, or sponsored by Twitch Interactive, Inc. or StreamDatabase. Automating watch-time or interacting with Twitch's internal APIs falls outside Twitch's official Terms of Service—while single-stream watch-time clients rarely face enforcement, use this software at your own discretion and risk.

---

## 1. Overview & Features

Lootly (`com.lootly.app`) is a native Kotlin Android app (Jetpack Compose Material 3 dark UI) for automated Twitch watch-time badge farming with zero manual token setup and minimal background data usage.

- **Architecture:** `arm64-v8a`
- **Android versions:** Android 8.0 – 17 (minSdk 26, targetSdk 35)

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
   - Counts watch-time minutes toward drops while using less than 1% of normal video data and CPU.

4. **Badge Discovery & Multi-Tier Handling:**
   - Scrapes public watch-time badges from Twitch GQL and `streamdatabase.com/twitch/global-badges` on a 1-hour timer with **±4 minute** jitter, matching StreamDatabase campaigns against Twitch's Drops Inventory by game and badge reward name.
   - Resolves multi-tier badge progressions (e.g., Payday 3: Clover -> Pearl -> Joy; Pokemon Evolutions; GTAVI Chains) in ascending tier order and calculates per-tier step durations dynamically (e.g., `60m/day` for GTAVI, `30m` for 30m-step campaigns).
   - Sorts eligible badges in exact Auto priority order: earliest expiration date first, then shortest unowned tier.
   - Tracks daily watch caps on multi-day events—once a tier is earned, the rest of that campaign is skipped for the remainder of the UTC day while mining rotates to the next eligible badge.

5. **Live Watch Drops Sync, Manual Pinning (`TARGET` Tile) & Pacing:**
   - **Live Per-Channel Watch Drops Sync (`DropCurrentSessionContext`):** Syncs watched and remaining minutes directly from Twitch for channel-level Watch Drops and badge celebrations, combining session watch time with Twitch GQL inventory syncs so progress updates continuously.
   - Tap any badge in the **ELIGIBLE** queue to pin and mine it immediately via the **TARGET** (`Auto` / `Pinned`) tile.
   - Tap **TARGET** (or **Reset Auto**) to return to automatic priority, or let it auto-reset to `Auto` once the pinned badge completes or hits its daily cap.
   - Uses a **20–55s** randomized claim delay when a badge finishes and a **5–12s** cooldown when switching streams, removing claimed badges from the **ELIGIBLE** queue.

6. **Twitch App Auto-Pause (`UsageStatsManager`):**
   - `AppUsageMonitor` checks foreground app usage every 3 seconds.
   - Pauses watch heartbeats immediately when the official Twitch app (`tv.twitch.android.app`) is opened to prevent concurrent stream conflicts, and resumes automatically when closed (toggleable in settings).

7. **Foreground Service & Zero-Battery Standby:**
   - `MinerForegroundService` runs as a `DATA_SYNC` foreground service (`START_STICKY`) with a partial WakeLock, handling Android 15/16+'s 6-hour rolling `dataSync` limit via `onTimeout()`.
   - Shows an ongoing notification with badge progress, percentage, ETA, and Pause/Resume/Stop controls.
   - Enters Zero-Battery Standby when 0 eligible badges remain, releasing the partial WakeLock until the next catalog scan.

8. **UI:**
   - Symmetrical **Lootly ribbon-badge** adaptive launcher icon and matching monochrome notification icon.
   - Account card showing Twitch avatar, display name, username, user ID, and single-tap Logout.
   - Auto-hiding Permission Health Bar (collapses once Usage Stats, Battery Optimization, and Notifications are granted).
   - Active Badge Card with badge icon, title, streamer name, category, animated progress bar, split percentage (left) and ETA (`~Xm left`, right), and **TARGET** mode indicator (`Auto` / `Pinned`).
   - Expandable **ELIGIBLE** queue sorted in Auto priority order (pinned badge first, then earliest expiration date) with icons, required watch minutes, and tap-to-pin selection.
   - Collapsible monospace Miner Console (expanded by default, toggles on tap) with `HH:mm:ss` timestamps, color-coded log levels, and debug spam filtering.

---

## 2. Architecture & Build Instructions

Lootly consists of two components:
1. Native ARM64 Go binary compiled as a shared library (`libtwitchminer.so`) inside the app's native library directory.
2. Native Kotlin / Jetpack Compose Android app (`com.lootly.app`, packaged as `Lootly.apk`).

### Directory Layout

<pre>
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
</pre>

### Compiling the Go Engine (Android ARM64)

<pre>
cd go-miner
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build \
  -trimpath \
  -ldflags="-s -w" \
  -o ../app/src/main/jniLibs/arm64-v8a/libtwitchminer.so \
  ./cmd/twitch-miner-go
</pre>

`libtwitchminer.so` is bundled into `lib/arm64-v8a/` inside the APK and extracted by Android at install time into the app's `nativeLibraryDir` with executable permissions.

### Building the Android APK (Local)

Requirements:
- Android SDK (API 35 platform, build-tools `35.0.0` or `34.0.0`)
- JDK 17 or 21
- Android `aapt2`

Run:
<pre>
./gradlew testDebugUnitTest
./gradlew assembleDebug
</pre>

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

1. **`android.permission.INTERNET`:** Connects to Twitch GQL APIs, HLS playlists, segment servers, the local loopback integrity bridge, and loads badge icons via Coil.
2. **`android.permission.ACCESS_NETWORK_STATE`:** Detects Wi-Fi/cellular connectivity changes to pause or resume network activity.
3. **`android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_DATA_SYNC`:** Keeps `MinerForegroundService` running in the background (`dataSync` type) while the screen is off or the app is minimized, handling Android 15/16+'s 6-hour rolling `dataSync` limit via `onTimeout()`.
4. **`android.permission.POST_NOTIFICATIONS`:** Shows the ongoing notification (progress %, badge name, ETA, Pause/Resume/Stop buttons) and alerts when a badge is claimed (Android 13+).
5. **`android.permission.WAKE_LOCK`:** Holds a `PARTIAL_WAKE_LOCK` while an eligible badge is actively mining so watch heartbeats continue with the screen off; released when 0 badges remain.
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
   - Multi-tier badge scraper, StreamDatabase ↔ Twitch Inventory matcher, dynamic step duration calculator, priority queue sorter, randomized scan/switch/claim delays, and daily watch-cap rotator (`internal/watcher/streamdatabase.go`, `internal/watcher/badge.go`, `internal/watcher/loop.go`, `internal/twitch/drops.go`).
   - Live per-channel Watch Drops sync (`DropCurrentSessionContext`), minute watcher, and live progress calculation in `/api/streamers`.
   - Loopback HTTP control endpoints (`POST /api/control/pause`, `POST /api/control/resume`, `POST /api/control/refresh-badges`, `POST /api/control/pin-badge`).
   - Stripped desktop Cgo/X11 systray dependencies for pure-Go Android ARM64 builds.

---

## 6. License

Lootly and its vendored engine are licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

See `go-miner/LICENSE.txt` for the full license text. Any redistribution or modification must provide source code under the terms of GPL-3.0.
