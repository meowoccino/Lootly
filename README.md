# Lootly - Twitch Watch-Time Badge Miner for Android

An automated, zero-video-bandwidth Twitch watch-time badge miner for Android. Built as a native Kotlin application (Min SDK 26, Target SDK 35, ARM64-v8a) with a Jetpack Compose Material 3 dark UI and a vendored Go mining engine.

---

## Features

1. **Zero-Token Native WebView Authentication**
   - Dedicated `LoginActivity` loading the official Twitch login page (`https://www.twitch.tv/login`).
   - Automatically intercepts `auth-token`, `twilight-user`, and `unique_id` cookies directly from Android `CookieManager` upon login.
   - Credentials are encrypted and stored exclusively in Android Keystore-backed `EncryptedSharedPreferences` (`MasterKey` AES-256 GCM).
   - No manual copy-pasting of OAuth tokens, Client IDs, or passwords.

2. **Automated Headless WebView Integrity Bridge**
   - Boots an isolated, headless Android WebView offscreen sharing the logged-in session with image, video, and font loading disabled.
   - Intercepts and caches fresh `Client-Integrity` tokens, `User-Agent`, `X-Device-Id`, and `Client-Session-Id` headers required by Twitch GQL.
   - Runs a local loopback-only HTTP bridge (`127.0.0.1`) protected by a per-session random secret that supplies fresh tokens to the Go engine on demand.
   - Handles token expiration automatically with one-shot regeneration on integrity challenge errors.

3. **Streamless HLS Metadata & HEAD Watch Mode**
   - Requests only HLS stream manifests (`.m3u8`) and sends HTTP `HEAD` requests to lowest-quality video segments (`usher.ttvnw.net`) alongside spade heartbeat telemetry instead of downloading video streams.
   - Watch-time minutes are recognized by Twitch's drop servers while consuming less than 1% of normal video data and CPU bandwidth.

4. **Real-Time Badge Discovery & Catalog Monitoring**
   - Scrapes and indexes public watch-time badges from Twitch GQL and `streamdatabase.com/twitch/global-badges`.
   - Resolves multi-tier badge progressions (e.g., Payday 3: Clover -> Pearl -> Joy; Pokemon Evolutions; GTAVI Chains) and prioritizes the lowest-duration unowned tier first.
   - Respects 60-minute daily caps on multi-day events (such as GTAVI 6-day chains), rotating to the next eligible campaign once the daily cap is hit.

5. **Instant Auto-Switching & Real-Time Progress**
   - Smoothly interpolates local session watch time with Twitch GQL inventory syncs for real-time minute-by-minute progress.
   - Automatically transitions to the next tier or queued campaign when a badge reaches 100% or is claimed, removing completed badges from the **ELIGIBLE** queue.

6. **Official Twitch App Auto-Pause**
   - Polls `UsageStatsManager` every 3 seconds via `AppUsageMonitor`.
   - Pauses watch heartbeats immediately if the official Twitch app (`tv.twitch.android.app`) is opened to avoid concurrent stream conflicts, and resumes automatically upon exit (toggleable in settings).

7. **Resilient Foreground Service & Zero-Battery Standby**
   - Runs `MinerForegroundService` (`DATA_SYNC`, `START_STICKY`) with a partial WakeLock and an ongoing notification displaying live progress, percentage, ETA, and Pause/Resume/Stop actions.
   - Enters Zero-Battery Standby mode when 0 eligible badges remain, releasing the partial WakeLock until the next 1-hour catalog scan.

8. **Material 3 Jetpack Compose UI**
   - Account status card with Twitch avatar, display name, username, user ID, and one-tap Logout.
   - Auto-hiding Permission Health Bar that collapses once Usage Stats, Battery Optimization, and Notifications are granted.
   - Active Badge Card with badge icon, title, streamer name, category, animated progress bar, percentage, and dynamic ETA (`~Xm left`).
   - Expandable **ELIGIBLE** queue listing upcoming badge tiers with icons and required watch minutes.
   - Live monospace Miner Console with local timestamp alignment (`HH:mm:ss`), color-coded log levels, and automatic debug spam suppression.

---

## Architecture

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

---

## Build Instructions

Lootly compiles the Go engine as a native ARM64 shared library (`libtwitchminer.so`) packaged inside the Kotlin/Compose Android APK.

### Cloud Build via GitHub Actions (Recommended)

1. Push the repository to GitHub (`main` branch).
2. Open the **Actions** tab.
3. The **Build Android APK** workflow (`.github/workflows/build-apk.yml`) automatically sets up Go and Android SDK 35, runs unit tests, cross-compiles the ARM64 binary, and builds the APK.
4. Download `twitch-badge-miner-debug-apk` from the workflow **Artifacts**.

### Local Build

**1. Compile the Go Engine (Android ARM64):**
```bash
cd go-miner
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build \
  -trimpath \
  -ldflags="-s -w" \
  -o ../app/src/main/jniLibs/arm64-v8a/libtwitchminer.so \
  ./cmd/twitch-miner-go
```

**2. Assemble the Android APK:**
*Requires Android SDK (API 35, build-tools `34.0.0` or `35.0.0`) and JDK 17 or 21.*
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
The compiled APK outputs to `app/build/outputs/apk/debug/app-debug.apk`.

---

## Android Permissions

- **`INTERNET`**: Communicates with Twitch GraphQL APIs, HLS manifests, segment servers, local integrity bridge, and loads badge icons via Coil.
- **`ACCESS_NETWORK_STATE`**: Monitors Wi-Fi and mobile data state to pause/resume network activity on connection changes.
- **`FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC`**: Keeps `MinerForegroundService` alive in the background on Android 9+ and Android 14+.
- **`POST_NOTIFICATIONS`**: Displays the live progress notification (with Pause/Resume/Stop controls) and alerts when badges are claimed.
- **`WAKE_LOCK`**: Holds `PARTIAL_WAKE_LOCK` while actively mining so heartbeats fire with the screen off (released during Zero-Battery Standby).
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`**: Exempts the app from Doze mode and OEM background battery restrictions for uninterrupted mining.
- **`PACKAGE_USAGE_STATS`**: Allows `AppUsageMonitor` to detect when the official Twitch app (`tv.twitch.android.app`) is in the foreground to trigger Auto-Pause.

---

## Security & Privacy

- **Zero Third-Party Trackers:** No Firebase, analytics SDKs, or telemetry endpoints.
- **Encrypted Storage:** Session cookies and tokens remain inside Android Keystore `EncryptedSharedPreferences`—never written in plaintext to disk.
- **Authenticated Loopback IPC:** Communication between Kotlin and the Go engine runs exclusively over `127.0.0.1` HTTP sockets secured with session-scoped random secrets.
- **Standard App Sandbox:** Requires no root or Shizuku privileges.

---

## Upstream Attribution & License

- **Upstream Go Engine:** [Guliveer/twitch-miner-go](https://github.com/Guliveer/twitch-miner-go) (commit `e98e7d639a9397a01d2a245ed35df489ff61c3ed`), enhanced for Android with a pure-Go DNS resolver (`internal/androidnet/dns_android.go` with Cloudflare DoH `1.1.1.1` fallback), Android 14+ Conscrypt `SSL_CERT_DIR` discovery, WebView integrity bridge integration, multi-tier badge scraper with 60m daily cap rotation, live progress analytics (`/api/streamers`), loopback control endpoints (`/api/control/*`), and Cgo/X11 systray removal.
- **Badge Catalog Source:** [StreamDatabase Twitch Global Badges](https://www.streamdatabase.com/twitch/global-badges)

Licensed under the **GNU General Public License v3.0 (GPL-3.0)** (see `go-miner/LICENSE.txt`). Anyone redistributing or modifying this software must provide source code under the terms of GPL-3.0[span_2](start_span)[span_2](end_span).
