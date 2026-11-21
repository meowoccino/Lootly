# Lootly - Twitch Watch-Time Badge Miner for Android

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

## 2. Upstream Repositories & Attribution

1. **Upstream Go Mining Engine:**
   - **Repository:** https://github.com/Guliveer/twitch-miner-go
   - **Upstream Author:** Guliveer
   - **Upstream Commit Vendored:** `e98e7d639a9397a01d2a245ed35df489ff61c3ed`
   - **Upstream License:** GNU General Public License v3.0 (GPL-3.0)

2. **Upstream Global Badge Catalog Data:**
   - **Source:** https://www.streamdatabase.com/twitch/global-badges
   - StreamDatabase Twitch Global Badges index for scraping newly deployed badge campaigns.

3. **Android-Specific Upstream Enhancements Added:**
   - Pure-Go Android DNS resolver (`internal/androidnet/dns_android.go`) bypassing Android's lack of `/etc/resolv.conf`, with Cloudflare DoH (`1.1.1.1`) and system DNS fallback (for when local VPN firewalls like AdGuard block `spade.twitch.tv`).
   - Android 14+ Conscrypt system CA certificate discovery (`SSL_CERT_DIR`).
   - Headless WebView Client-Integrity bridge HTTP client (`internal/auth/`, `internal/gql/`).
   - Multi-level badge scraper and daily 60m watch-cap rotator (`internal/watcher/streamdatabase.go`, `internal/watcher/badge.go`).
   - Real-time minute watcher and live progress calculation in embedded analytics server (`/api/streamers`).
   - Dynamic POSIX and loopback HTTP control API (`POST /api/control/pause`, `POST /api/control/resume`, `POST /api/control/refresh-badges`).
   - Exclusion of desktop Cgo/X11 systray dependencies for pure Go Android ARM64 compilation.

---

## 3. License

Lootly and its vendored engine are licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

Full text of the GNU General Public License v3.0 is preserved in `go-miner/LICENSE.txt`. Anyone redistributing or modifying this software must provide source code under the terms of the GPL-3.0.

---

## 4. Android Permissions & Justification

The following permissions are declared in `app/src/main/AndroidManifest.xml` and requested at runtime:

1. **`android.permission.INTERNET`:**
   - Required to communicate with Twitch GraphQL APIs, stream playlists, segment servers, loopback integrity bridge, and load badge icons via Coil.

2. **`android.permission.ACCESS_NETWORK_STATE`:**
   - Monitors Wi-Fi and cellular connectivity state to pause or resume network operations when the phone connects or disconnects.

3. **`android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_DATA_SYNC`:**
   - Required on Android 9+ (API 28) and Android 14+ (API 34) to run `MinerForegroundService` as an ongoing background service with type `dataSync`, preventing the OS from terminating the engine while the screen is off or the app is minimized.

4. **`android.permission.POST_NOTIFICATIONS`:**
   - Required on Android 13+ (API 33) to display the ongoing foreground service notification (showing farming percentage, badge name, ETA, and Pause/Resume/Stop actions) and to alert when a badge has been claimed.

5. **`android.permission.WAKE_LOCK`:**
   - Acquires a partial wake lock (`PARTIAL_WAKE_LOCK`) while an eligible badge is actively being mined so the CPU stays awake to send watch heartbeats while the screen is locked. Automatically released when 0 badges remain (Zero-Battery Standby).

6. **`android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`:**
   - Prompts the user to exempt Lootly from OEM background battery restrictions (Doze mode, App Standby Buckets) to prevent background suspension.

7. **`android.permission.PACKAGE_USAGE_STATS`:**
   - Special system access required by `AppUsageMonitor` to detect when the official Twitch app (`tv.twitch.android.app`) is active in the foreground so Auto-Pause can pause heartbeats and avoid concurrent stream watch conflicts.

---

## 5. How It Was Built & Compilation Instructions

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
```

### Building the Native Go Engine for Android ARM64

To compile the Go engine without desktop dependencies for Android userspace:

```bash
cd go-miner
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build \
  -trimpath \
  -ldflags="-s -w" \
  -o ../app/src/main/jniLibs/arm64-v8a/libtwitchminer.so \
  ./cmd/twitch-miner-go
```

The output file `libtwitchminer.so` is packaged into the APK's `lib/arm64-v8a/` folder and extracted by Android at install time into the app's private `nativeLibraryDir` with executable permissions.

### Building the Android APK (Local)

Requirements:
- Android SDK (API 35 platforms, build-tools `35.0.0` or `34.0.0`)
- Java 17 or 21 (JDK)
- Android `aapt2` binary

Execute:
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`

### Cloud Builds via GitHub Actions

This repository includes a CI workflow at `.github/workflows/build-apk.yml`:
1. Push code to your GitHub repository (`main` branch).
2. Go to the **Actions** tab on GitHub.
3. The **Build Android APK** workflow runs on `ubuntu-latest` runners.
4. It sets up Go and Android SDK 35, runs all Go and Kotlin unit tests, cross-compiles the ARM64 engine, assembles the debug APK, and uploads it under **Artifacts** as `twitch-badge-miner-debug-apk`.

---

## 6. Security & Privacy

- **No Third-Party Analytics:** Zero tracking SDKs, telemetry, Firebase, or advertising trackers.
- **Credentials Isolated:** Login tokens are stored inside Android Keystore `EncryptedSharedPreferences` on the device, with no plaintext tokens on disk or shared storage.
- **Local Loopback Communication:** All IPC between the Kotlin UI/Service and the Go engine runs over `127.0.0.1` HTTP sockets protected by session-scoped secret tokens.
- **Standard Sandboxing:** Operates within the standard Android application sandbox without requiring root or Shizuku privileges.
