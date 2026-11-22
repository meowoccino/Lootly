**Twitch Watch-Time Badge Miner for Android**

> **Disclaimer:** Lootly is an independent, open-source project and is **not** affiliated with, endorsed, or sponsored by Twitch Interactive, Inc. or StreamDatabase. Automating watch-time or interacting with Twitch's internal APIs falls outside Twitch's official Terms of Service—while single-stream watch-time clients rarely face enforcement, use this software at your own discretion and risk.

---

## 1. Project Overview & Features

Lootly is a native Kotlin Android application (Min SDK 26, Target SDK 35, ARM64-v8a, Jetpack Compose Material 3 dark UI) for automated Twitch watch-time badge farming with zero manual token handling and minimal background data usage.

### Key Capabilities & Features

1. **Zero-Token Native WebView Authentication:**
   - Dedicated native `LoginActivity` loading the official Twitch login page (`https://www.twitch.tv/login`).
   - Intercepts `auth-token`, `twilight-user`, and `unique_id` cookies directly from Android `CookieManager` upon login.
   - Stores credentials in Android Keystore-backed `EncryptedSharedPreferences` (`MasterKey` AES-256 GCM).
   - No manual copy-pasting of OAuth tokens, Client IDs, or passwords.

2. **Automated Headless WebView Integrity Bridge:**
   - Boots an isolated, headless Android WebView offscreen sharing the logged-in session with image, video, and font loading disabled.
   - Intercepts and caches fresh `Client-Integrity` tokens, `User-Agent`, `X-Device-Id`, and `Client-Session-Id` headers required by Twitch GQL.
   - Runs a local, loopback-only authenticated HTTP bridge (`127.0.0.1`) protected by a per-session random secret that supplies fresh tokens to the Go engine on demand.
   - Handles token expiration and performs one-shot regeneration on integrity challenge errors.

3. **Streamless HLS Metadata & HEAD Watch Mode:**
   - Requests only HLS stream manifests (`.m3u8`) and issues HTTP `HEAD` requests to lowest-quality video segments (`usher.ttvnw.net`) alongside spade heartbeat telemetry instead of downloading video streams.
   - Watch-time minutes are recognized by Twitch's drop tracking servers while consuming less than 1% of normal video data and CPU bandwidth.

4. **Real-Time Badge Discovery & Catalog Monitoring:**
   - Periodically scrapes and indexes public watch-time badges from Twitch GQL and `streamdatabase.com/twitch/global-badges`.
   - Resolves multi-tier badge progressions (e.g., Payday 3: Clover -> Pearl -> Joy; Pokemon Evolutions; GTAVI Chains).
   - Prioritizes the lowest-duration unowned tier first.
   - Respects 60-minute daily caps on multi-day events (such as GTAVI 6-day chains), rotating to the next eligible campaign once today's cap is reached.

5. **Instant Auto-Switching & Real-Time Progress:**
   - Real-time minute-by-minute progress calculation: local session watch time is interpolated with Twitch GQL inventory syncs to prevent progress from stalling at static percentages.
   - Transitions to the next tier or queued campaign when a badge reaches 100% or is claimed.
   - Removes claimed badges from the expandable **ELIGIBLE** queue.

6. **Official Twitch App UsageStats Auto-Pause:**
   - Integrated `AppUsageMonitor` polls `UsageStatsManager` every 3 seconds.
   - Pauses watch heartbeats immediately when the official Twitch app (`tv.twitch.android.app`) is opened to avoid concurrent stream conflicts.
   - Resumes mining automatically when you exit the official Twitch app (toggleable in settings).

7. **Resilient Foreground Service & Zero-Battery Standby:**
   - `MinerForegroundService` runs under Android's Foreground Service type `DATA_SYNC` (`START_STICKY`) with partial WakeLock.
   - Displays an ongoing Android notification with badge progress, percentage, ETA, and Pause/Resume/Stop actions.
   - Enters Zero-Battery Standby mode when 0 eligible badges remain in the catalog, releasing the partial WakeLock until the next 1-hour catalog scan.

8. **Material 3 Jetpack Compose UI:**
   - Account status card displaying Twitch avatar, display name, username, user ID, and single-tap Logout.
   - Auto-hiding Permission Health Bar (collapses once Usage Stats, Battery Optimization, and Notifications are granted).
   - Active Badge Card with badge icon, title, streamer name, category, animated progress bar, percentage, and ETA (`~Xm left`).
   - Expandable **ELIGIBLE** queue listing upcoming badge tiers with icons and required watch minutes.
   - Live monospace Miner Console with local timestamp alignment (`HH:mm:ss`), color-coded log levels, and automatic suppression of internal debug spam.

---

## 2. How It Was Built & Compilation Instructions

Lootly consists of two integrated components:
1. Native ARM64 Go binary compiled as a shared object (`libtwitchminer.so`) inside the app's native library directory.
2. Native Kotlin / Jetpack Compose Android application package.

### Architecture Structure

```text
twitch-badge-miner-android/
├── go-miner/                       # Vendored Go engine with Android enhancements
│   ├── cmd/twitch-miner-go/        # Main Go executable entrypoint
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
│       ├── jniLibs/arm64-v8a/      # Destination for libtwitchminer.so
│       └── res/                    # Adaptive icons, layouts, themes, strings
└── .github/workflows/build-apk.yml # Automated GitHub Actions cloud CI build
