# Lootly - Twitch Watch-Time Badge Miner for Android
> Complete Project Architecture, Features, Build Guide, Permissions, and License Documentation

---

## 1. Project Overview & Features

Lootly is a complete, self-contained native Kotlin Android application (**Min SDK 26**, **Target SDK 35**, **ARM64-v8a**, **Jetpack Compose Material 3** dark UI) designed for fully automated Twitch watch-time badge farming with zero manual token handling, zero video streaming data waste, and intelligent background execution.

### Key Capabilities & Features

1. **Zero-Token Native WebView Authentication:**
   - Dedicated native `LoginActivity` loading official Twitch login page (`[https://www.twitch.tv/login](https://www.twitch.tv/login)`).
   - Automatically intercepts `auth-token`, `twilight-user`, and `unique_id` cookies directly from Android `CookieManager` upon successful login.
   - Credentials are encrypted and stored exclusively in Android Keystore-backed `EncryptedSharedPreferences` (`MasterKey` AES-256 GCM).
   - Zero manual copy-pasting of OAuth tokens, Client IDs, or passwords.

2. **Automated Headless WebView Integrity Bridge:**
   - Modern Twitch GQL operations strictly require Client-Integrity tokens and device telemetry to prevent botting.
   - An isolated, headless Android WebView boots offscreen, sharing the logged-in session, with image, video, and font loading completely disabled.
   - Intercepts and caches fresh `Client-Integrity` tokens, `User-Agent`, `X-Device-Id`, and `Client-Session-Id` headers.
   - Runs a local, loopback-only authenticated HTTP bridge (`127.0.0.1`) protected by a per-session random secret that supplies fresh tokens to the Go engine on demand.
   - Automatically handles token expiration and performs one-shot token regeneration on integrity challenge errors.

3. **Streamless HLS Metadata & HEAD Watch Mode:**
   - Instead of downloading multi-gigabyte video streams, the engine only requests HLS stream manifests (`.m3u8`) and issues HTTP `HEAD` requests to lowest-quality video segments (`usher.ttvnw.net`) alongside spade heartbeat telemetry.
   - Watch-time minutes are 100% recognized by Twitch's drop tracking servers while consuming less than 1% of normal video data and CPU bandwidth.

4. **Real-Time Badge Discovery & Catalog Monitoring:**
   - Periodically scrapes and indexes public watch-time badges directly from Twitch GQL and `[streamdatabase.com/twitch/global-badges](https://streamdatabase.com/twitch/global-badges)`.
   - Automatically resolves multi-tier badge progressions (e.g., Payday 3: Clover -> Pearl -> Joy; Pokemon Evolutions; GTAVI Chains).
   - Prioritizes the lowest-duration unowned tier first.
   - Respects 60-minute daily caps on multi-day events (like GTAVI 6-day chains), rotating to the next eligible campaign once today's cap is achieved.

5. **Instant Auto-Switching & Real-Time Progress:**
   - Real-time minute-by-minute progress calculation: local session watch time is smoothly interpolated with Twitch GQL inventory syncs, preventing progress from stalling at static percentages (like 63%).
   - When a badge reaches 100% or is claimed, the active card smoothly transitions to the next tier or the next queued campaign in the eligible queue.
   - Automatically removes claimed badges from the expandable **"ELIGIBLE"** queue.

6. **Official Twitch App UsageStats Auto-Pause:**
   - Integrated `AppUsageMonitor` polls `UsageStatsManager` every 3 seconds.
   - If the user opens the official Twitch application (`tv.twitch.android.app`), Lootly immediately pauses watch heartbeats to avoid concurrent stream conflicts or account flag warnings.
   - When the user exits the official Twitch app, Lootly resumes mining automatically.
   - Auto-pause can be enabled or disabled with a single toggle in the settings.

7. **Resilient Foreground Service & Zero-Battery Standby:**
   - `MinerForegroundService` runs under Android's Foreground Service type `DATA_SYNC` (`START_STICKY`) with partial WakeLock.
   - Displays an ongoing Android notification with real-time badge progress, percentage, ETA, and Pause/Resume/Stop actions.
   - When 0 eligible badges remain in the catalog, the engine automatically enters Zero-Battery Standby mode (releasing the partial WakeLock and sleeping until the next 1-hour catalog scan).

8. **Clean, Modern Material 3 Jetpack Compose UI:**
   - Account status card displaying Twitch avatar, display name, username, user ID, and single-tap Logout.
   - Auto-hiding Permission Health Bar (collapses once Usage Stats, Battery Optimization, and Notifications are granted).
   - Active Badge Card with badge icon, title, streamer name, category, animated progress bar, percentage, and dynamic ETA (`~Xm left`).
   - Expandable **"ELIGIBLE"** queue listing every upcoming badge tier with its individual icon and required watch minutes.
   - Live Monospace Miner Console with local phone timestamp alignment (`HH:mm:ss`), color-coded log levels, and automatic suppression of internal debug/synthetic drop spam.

---

## 2. Upstream Repositories & Attribution

Lootly builds upon and integrates open-source work:

1. **Upstream Go Mining Engine:**
   - **Repository:** [https://github.com/Guliveer/twitch-miner-go](https://github.com/Guliveer/twitch-miner-go)
   - **Upstream Author:** Guliveer
   - **Upstream Commit Vendored:** `e98e7d639a9397a01d2a245ed35df489ff61c3ed`
   - **Upstream License:** GNU General Public License v3.0 (GPL-3.0)

2. **Upstream Global Badge Catalog Data:**
   - **Source:** [https://www.streamdatabase.com/twitch/global-badges](https://www.streamdatabase.com/twitch/global-badges)
   - StreamDatabase Twitch Global Badges index for real-time scraping of newly deployed badge campaigns.

3. **Android-Specific Upstream Enhancements Added:**
   - Pure-Go Android DNS resolver (`internal/androidnet/dns_android.go`) bypassing Android's lack of `/etc/resolv.conf`, with Cloudflare DoH (`1.1.1.1`) and system DNS fallback (vital when local VPN firewalls like AdGuard block `spade.twitch.tv`).
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

1. **`android.permission.INTERNET`**
   - **Purpose:** Required to communicate with Twitch GraphQL APIs, stream playlists, segment servers, loopback integrity bridge, and load badge icons via Coil.

2. **`android.permission.ACCESS_NETWORK_STATE`**
   - **Purpose:** Monitors Wi-Fi and cellular connectivity state to pause or resume network operations when the phone connects or disconnects.

3. **`android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_DATA_SYNC`**
   - **Purpose:** Required on Android 9+ (API 28) and Android 14+ (API 34) to run `MinerForegroundService` as an ongoing background service with type `dataSync`. Ensures the OS does not terminate the engine while the screen is off or the app is minimized.

4. **`android.permission.POST_NOTIFICATIONS`**
   - **Purpose:** Required on Android 13+ (API 33) to display the mandatory ongoing foreground service notification (showing live farming percentage, badge name, ETA, and Pause/Resume/Stop actions) and to alert the user when a badge has been successfully claimed.

5. **`android.permission.WAKE_LOCK`**
   - **Purpose:** Acquires a partial wake lock (`PARTIAL_WAKE_LOCK`) while an eligible badge is actively being mined so the CPU stays awake to send watch heartbeats even when the screen is locked. Automatically released when 0 badges remain (Zero-Battery Standby).

6. **`android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`**
   - **Purpose:** Prompts the user to exempt Lootly from OEM battery-killer background restrictions (Doze mode, App Standby Buckets). Ensures 24/7 autonomous mining without sudden background suspension.

7. **`android.permission.PACKAGE_USAGE_STATS`**
   - **Purpose:** Special system access required by `AppUsageMonitor` to detect when the official Twitch app (`tv.twitch.android.app`) is active in the foreground. This allows the Auto-Pause feature to immediately pause heartbeats and avoid concurrent stream watch conflicts.

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

The output file `libtwitchminer.so` is packaged into the APK's `lib/arm64-v8a/` folder and automatically extracted by Android at install time into the app's private `nativeLibraryDir` with executable permissions.

### Building the Android APK (Local)

**Requirements:**
- Android SDK (API 35 platforms, build-tools `35.0.0` or `34.0.0`)
- Java 17 or 21 (JDK)
- Android `aapt2` binary

**Execute:**
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Output APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Cloud Builds via GitHub Actions (Zero Local Setup)

This repository includes a pre-configured CI workflow at `.github/workflows/build-apk.yml`:
1. Push code to your GitHub repository (branch `main`).
2. Go to the **Actions** tab on GitHub.
3. The **Build Android APK** workflow runs automatically on `ubuntu-latest` runners.
4. It sets up Go, Android SDK 35, runs all Go and Kotlin unit tests, cross-compiles the ARM64 engine, assembles the debug APK, and uploads it under **Artifacts** as `twitch-badge-miner-debug-apk`.

---

## 6. Security & Privacy Highlights

- **No Third-Party Analytics:** Zero tracking SDKs, zero telemetry backdoors, no Firebase, no advertising trackers.
- **Credentials Isolated:** Login tokens are stored strictly inside the Android Keystore `EncryptedSharedPreferences` on the device. No plaintext tokens on disk or shared storage.
- **Local Loopback Communication:** All IPC between the Kotlin UI/Service and the Go engine takes place over `127.0.0.1` HTTP sockets protected by session-scoped secret tokens.
- **Safe Sandboxing:** Operates completely within the standard Android application sandbox without requiring root or Shizuku privileges.
