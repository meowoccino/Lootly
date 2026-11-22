**Twitch Watch-Time Badge Miner for Android**

> **Disclaimer:** Lootly is an independent, open-source project and is **not** affiliated with, endorsed, or sponsored by Twitch Interactive, Inc. or StreamDatabase. Automating watch-time or interacting with Twitch's internal APIs falls outside Twitch's official Terms of Service—while single-stream watch-time clients rarely face enforcement, use this software at your own discretion and risk.

---

## 1. Project Overview & Features

Lootly (`com.lootly.app`) is a native Kotlin Android application with a Jetpack Compose Material 3 dark UI for automated Twitch watch-time badge farming with zero manual token handling and minimal background data usage.

- **Architecture:** `arm64-v8a` only (virtually all modern Android phones).
- **Android versions:** **Android 8.0+ (API 26)** minimum, targeting **Android 15 (API 35)** — fully compatible with **Android 16 and 17**.

### Key Capabilities & Features

1. **Zero-Token Native WebView Authentication & Auto-Start:**
   - Dedicated native `LoginActivity` loading the official Twitch login page (`https://www.twitch.tv/login`).
   - Intercepts `auth-token`, `twilight-user`, and `unique_id` cookies directly from Android `CookieManager` upon login.
   - Stores credentials in Android Keystore-backed `EncryptedSharedPreferences` (`MasterKey` AES-256 GCM).
   - Automatically starts background mining on app launch (when signed in) and immediately after login, with no manual copy-pasting of OAuth tokens, Client IDs, or passwords.

2. **Automated Headless WebView Integrity Bridge:**
   - Boots an isolated, headless Android WebView offscreen sharing the logged-in session with image, video, and font loading disabled.
   - Intercepts and caches fresh `Client-Integrity` tokens, `User-Agent`, `X-Device-Id`, and `Client-Session-Id` headers required by Twitch GQL.
   - Runs a local, loopback-only authenticated HTTP bridge (`127.0.0.1`) protected by a per-session random secret that supplies fresh tokens to the Go engine on demand.
   - Handles token expiration and performs one-shot regeneration on integrity challenge errors.

3. **Streamless HLS Metadata & HEAD Watch Mode:**
   - Requests only HLS stream manifests (`.m3u8`) and issues HTTP `HEAD` requests to lowest-quality video segments (`usher.ttvnw.net`) alongside spade heartbeat telemetry instead of downloading video streams.
   - Watch-time minutes are recognized by Twitch's drop tracking servers while consuming less than 1% of normal video data and CPU bandwidth.

4. **Real-Time Badge Discovery & Dynamic Multi-Tier Handling:**
   - Periodically scrapes and indexes public watch-time badges from Twitch GQL and `streamdatabase.com/twitch/global-badges` using randomized **±4 minute** jitter on the 1-hour catalog scan timer, matching StreamDatabase badge campaigns against Twitch's Drops Inventory by game and badge reward name.
   - Resolves multi-tier badge progressions (e.g., Payday 3: Clover -> Pearl -> Joy; Pokemon Evolutions; GTAVI Chains) in ascending tier order and calculates per-tier step durations dynamically (e.g., `60m/day` for GTAVI, `30m` for 30m-step campaigns).
   - Prioritizes campaigns in exact Auto priority order by earliest expiration date and lowest-duration unowned tier first.
   - Respects daily watch caps on multi-day events, automatically skipping remaining tiers of a multi-tier campaign for the rest of the UTC day once a tier is earned and rotating to the next eligible campaign.

5. **Live Watch Drops Sync, Manual Badge Switcher (`TARGET` Tile) & Natural Pacing:**
   - **Live Per-Channel Watch Drops Sync (`DropCurrentSessionContext`):** Syncs live watched and remaining minutes directly from Twitch for channel-level Watch Drops and badge celebrations, interpolating session progress with Twitch GQL inventory syncs to prevent stalling at static percentages.
   - Tap any badge in the expandable **ELIGIBLE** queue to pin and mine it immediately via the **TARGET** (`Auto` / `Pinned`) tile.
   - Tap **TARGET** (or **Reset Auto**) at any time to switch back to automatic priority, or let it auto-reset to `Auto` once the pinned badge is earned or hits its daily cap.
   - Transitions smoothly between campaigns with a randomized **20–55s** human-like claim delay on completed badges and a **5–12s** cooldown when switching streams, removing claimed badges from the **ELIGIBLE** queue.

6. **Official Twitch App UsageStats Auto-Pause:**
   - Integrated `AppUsageMonitor` polls `UsageStatsManager` every 3 seconds.
   - Pauses watch heartbeats immediately when the official Twitch app (`tv.twitch.android.app`) is opened to avoid concurrent stream conflicts.
   - Resumes mining automatically when you exit the official Twitch app (toggleable in settings).

7. **Resilient Foreground Service & Zero-Battery Standby:**
   - `MinerForegroundService` runs under Android's Foreground Service type `DATA_SYNC` (`START_STICKY`) with partial WakeLock, cleanly handling Android 15/16+'s 6-hour rolling `dataSync` foreground service limit via `onTimeout()`.
   - Displays an ongoing Android notification with badge progress, percentage, ETA, and Pause/Resume/Stop actions.
   - Enters Zero-Battery Standby mode when 0 eligible badges remain in the catalog, releasing the partial WakeLock until the next catalog scan.

8. **Material 3 Jetpack Compose UI:**
   - Custom symmetrical **Lootly ribbon-badge** adaptive launcher icon and matching monochrome notification bar icon.
   - Account status card displaying Twitch avatar, display name, username, user ID, and single-tap Logout.
   - Auto-hiding Permission Health Bar (collapses once Usage Stats, Battery Optimization, and Notifications are granted).
   - Active Badge Card with badge icon, title, streamer name, category, animated progress bar, split percentage (left) and ETA (`~Xm left`, right), and **TARGET** mode indicator (`Auto` / `Pinned`).
   - Expandable **ELIGIBLE** queue sorted to match Auto priority order (pinned badge first, followed by earliest expiration date) with icons, required watch minutes, and tap-to-pin selection.
   - Collapsible live monospace Miner Console (expanded by default, collapses/expands on tap) with local timestamp alignment (`HH:mm:ss`), color-coded log levels, and automatic suppression of internal debug spam.

---

## 2. How It Was Built & Compilation Instructions

Lootly consists of two integrated components:
1. Native ARM64 Go binary compiled as a shared object (`libtwitchminer.so`) inside the app's native library directory.
2. Native Kotlin / Jetpack Compose Android application package (`com.lootly.app`, distributed as `Lootly.apk`).

### Architecture Structure

<pre>
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
</pre>

### Building the Native Go Engine for Android ARM64

To compile the Go engine without desktop dependencies for Android userspace:

<pre>
cd go-miner
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build \
  -trimpath \
  -ldflags="-s -w" \
  -o ../app/src/main/jniLibs/arm64-v8a/libtwitchminer.so \
  ./cmd/twitch-miner-go
</pre>

The output file `libtwitchminer.so` is packaged into the APK's `lib/arm64-v8a/` folder and extracted by Android at install time into the app's private `nativeLibraryDir` with executable permissions.

### Building the Android APK (Local)

Requirements:
- Android SDK (API 35 platforms, build-tools `35.0.0` or `34.0.0`)
- Java 17 or 21 (JDK)
- Android `aapt2` binary

Execute:
<pre>
./gradlew testDebugUnitTest
./gradlew assembleDebug
</pre>

Output APK location:
`app/build/outputs/apk/debug/app-debug.apk` (packaged for release as `Lootly.apk`)

### Cloud Builds via GitHub Actions

This repository includes a CI workflow at `.github/workflows/build-apk.yml`:
1. Push code to your GitHub repository (`main` branch).
2. Go to the **Actions** tab on GitHub.
3. The **Build Android APK** workflow runs on `ubuntu-latest` runners.
4. It sets up Go and Android SDK 35, runs all Go and Kotlin unit tests, cross-compiles the ARM64 engine, assembles the debug APK, and uploads it under **Artifacts**.

---

## 3. Android Permissions & Justification

The following permissions are declared in `app/src/main/AndroidManifest.xml` and requested at runtime:

1. **`android.permission.INTERNET`:**
   - Required to communicate with Twitch GraphQL APIs, stream playlists, segment servers, loopback integrity bridge, and load badge icons via Coil.

2. **`android.permission.ACCESS_NETWORK_STATE`:**
   - Monitors Wi-Fi and cellular connectivity state to pause or resume network operations when the phone connects or disconnects.

3. **`android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_DATA_SYNC`:**
   - Required on Android 9+ (API 28) and Android 14+ (API 34) to run `MinerForegroundService` as an ongoing background service with type `dataSync`, preventing the OS from terminating the engine while the screen is off or the app is minimized (and gracefully handling Android 15/16+'s 6-hour rolling `dataSync` timeout limit via `onTimeout()`).

4. **`android.permission.POST_NOTIFICATIONS`:**
   - Required on Android 13+ (API 33) to display the ongoing foreground service notification (showing farming percentage, badge name, ETA, and Pause/Resume/Stop actions) and to alert when a badge has been claimed.

5. **`android.permission.WAKE_LOCK`:**
   - Acquires a partial wake lock (`PARTIAL_WAKE_LOCK`) while an eligible badge is actively being mined so the CPU stays awake to send watch heartbeats while the screen is locked. Automatically released when 0 badges remain (Zero-Battery Standby).

6. **`android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`:**
   - Prompts the user to exempt Lootly from OEM background battery restrictions (Doze mode, App Standby Buckets) to prevent background suspension.

7. **`android.permission.PACKAGE_USAGE_STATS`:**
   - Special system access required by `AppUsageMonitor` to detect when the official Twitch app (`tv.twitch.android.app`) is active in the foreground so Auto-Pause can pause heartbeats and avoid concurrent stream watch conflicts.

---

## 4. Security & Privacy

- **No Third-Party Analytics:** Zero tracking SDKs, telemetry, Firebase, or advertising trackers.
- **Credentials Isolated:** Login tokens are stored inside Android Keystore `EncryptedSharedPreferences` on the device, with no plaintext tokens on disk or shared storage.
- **Local Loopback Communication:** All IPC between the Kotlin UI/Service and the Go engine runs over `127.0.0.1` HTTP sockets protected by session-scoped secret tokens.
- **Standard Sandboxing:** Operates within the standard Android application sandbox without requiring root or Shizuku privileges.

---

## 5. Upstream Repositories & Attribution

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
   - Multi-level badge scraper, StreamDatabase ↔ Twitch Inventory campaign matcher, dynamic step duration resolver, priority queue sorting, randomized scan/switch/claim pacing, and daily watch-cap rotator (`internal/watcher/streamdatabase.go`, `internal/watcher/badge.go`, `internal/watcher/loop.go`, `internal/twitch/drops.go`).
   - Live per-channel Watch Drops sync (`DropCurrentSessionContext`), real-time minute watcher, and live progress calculation in embedded analytics server (`/api/streamers`).
   - Dynamic POSIX and loopback HTTP control API (`POST /api/control/pause`, `POST /api/control/resume`, `POST /api/control/refresh-badges`, `POST /api/control/pin-badge`).
   - Exclusion of desktop Cgo/X11 systray dependencies for pure Go Android ARM64 compilation.

---

## 6. License

Lootly and its vendored engine are licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

Full text of the GNU General Public License v3.0 is preserved in `go-miner/LICENSE.txt`. Anyone redistributing or modifying this software must provide source code under the terms of the GPL-3.0.
