# Offline Messenger

**Offline Messenger** is a native Android application built with Kotlin, Jetpack Compose, and Material Design 3 for encrypted peer-to-peer and host-relayed group communication **without Internet access**.

---

## Core Features (12 Modules / 20 Screens)

1. **Private Chat with Secret Code**: Random 8-character Room Code + 6-digit Pairing PIN paired via ephemeral ECDH (`secp256r1`) + HKDF-SHA256 + mutual HMAC-SHA256 confirmation + Safety Verification Code.
2. **Offline Group Chat**: Host-and-Members hub model where the group owner relays encrypted messages, files, polls, and SOS alerts to connected devices.
3. **Nearby Device Discovery**: Google Nearby Connections API (`Strategy.P2P_STAR`) with advertising, discovery, connection accept/reject prompts, and automatic Play Services availability detection.
4. **Wi-Fi & Mobile Hotspot Networking**: Local `NsdManager` (`_offlinemsg._tcp.`) discovery plus TCP socket server/client (`48219`) with manual Host IP fallback for Mobile Hotspot without SIM/Internet.
5. **Bluetooth Classic RFCOMM**: Discovery, paired device list, RFCOMM server/client sockets, and API-scoped runtime permissions (`BLUETOOTH_SCAN`/`CONNECT`/`ADVERTISE` on Android 12+, `ACCESS_FINE_LOCATION` on Android 8–11).
6. **Push-to-Talk Walkie-Talkie**: Low-latency 16 kHz PCM streaming using `AudioRecord`, `AudioTrack`, `AudioFocusRequest`, and `AcousticEchoCanceler`.
7. **Photo & File Sharing**: Android Photo Picker & Storage Access Framework (`OpenDocument`), 25 MB size validation, executable extension blocking, SHA-256 verification, chunked transfer progress, and interrupted transfer detection.
8. **Voice Messages**: Local `.m4a` AAC recording via `MediaRecorder` and playback with duration & progress via `MediaPlayer`.
9. **Offline Group Polls**: Synchronized group polls with single/multi-choice options and transactional duplicate-vote prevention.
10. **Emergency SOS**: Confirmed high-priority local SOS broadcast to connected/reachable nearby devices with explicit disclaimer that it does not reach the Internet or emergency services (999/911/112).
11. **Local Message History**: Persistent Room SQLite database with explicit migration (`MIGRATION_1_2`), full-text `LIKE` search, and individual/conversation deletion.
12. **Member & Group Management**: Edit group details, inspect member roles & live connection statuses, owner kick/remove, and English / বাংলা (Bengali) runtime localization.

---

## Security & Cryptography

- **No custom encryption**: Uses standard Java/Android JCA (`EC` `secp256r1`, `ECDH`, `HmacSHA256`, `PBKDF2WithHmacSHA256`, `AES/GCM/NoPadding`).
- **Key Wrapping at Rest**: Room keys and pairing PINs are encrypted via hardware-backed `AndroidKeyStore` (`RoomKeyStore`) before writing to SQLite.
- **Input & File Safety**: `PacketValidator` enforces 1.5 MB wire frame caps, 25 MB file limits, executable extension blacklisting, path traversal confinement, and packet replay protection.

---

## GitHub Repository & Automatic APK Build (GitHub Actions)

### 1. Upload Project Files to GitHub
1. Create a new repository on GitHub (e.g., `Offline-Messenger`).
2. Extract or copy the project folder contents so `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `app/`, `gradle/`, and `.github/` sit **directly at the repository root** (never inside a nested `.zip` or extra subfolder).
3. Push to the `main` branch:
   ```bash
   git init
   git add .
   git commit -m "Initial commit: Offline Messenger"
   git branch -M main
   git remote add origin https://github.com/<YOUR_USERNAME>/Offline-Messenger.git
   git push -u origin main
   ```
4. Confirm `.github/workflows/android-apk.yml` is at the exact root path `.github/workflows/android-apk.yml`.

### 2. Download the Built APK
1. Open your repository on GitHub and select the **Actions** tab.
2. Click **Android Debug APK Build** (triggers automatically on push to `main`, or click **Run workflow** manually via `workflow_dispatch`).
3. Wait for the green checkmark on `Build & Test Offline Messenger APK`.
4. Scroll to **Artifacts** at the bottom of the workflow summary and download **`offline-messenger-debug-apk`** (contains `app-debug.apk`).

### 3. Install on an Android Phone
1. Transfer and unzip `app-debug.apk` onto an Android 8.0+ (API 26+) phone.
2. Tap `app-debug.apk` and allow **Install unknown apps** for your file manager/browser if prompted.
3. Open **Offline Messenger**, complete onboarding/profile setup, and grant Nearby/Bluetooth/Microphone permissions when prompted.

---

## Alternative Build Methods

### A. Android Studio (Windows / macOS / Linux)
1. Open **Android Studio** -> **Open** -> select the root `Offline-Messenger` folder.
2. Wait for Gradle Sync (uses JDK 17+ and Gradle 9.3.1).
3. Select **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** or run `./gradlew assembleDebug`.
4. Output: `app/build/outputs/apk/debug/app-debug.apk`.

### B. Command Line / Cloud CI
Any Linux/macOS CI container with JDK 17+ and Android SDK 36 (`build-tools;36.0.0`, `platforms;android-36`) can build the exact same project:
```bash
chmod +x gradlew
./gradlew clean testDebugUnitTest assembleDebug
```

---

## Fixing Common Build & GitHub Actions Errors

| Error | Cause | Fix |
|---|---|---|
| `Permission denied: ./gradlew` | Missing executable bit on Linux | Workflow runs `chmod +x gradlew` (or run `git update-index --chmod=+x gradlew`). |
| `Unsupported class file major version` / JDK error | Running Gradle 9 / AGP 9 with JDK 11 or 8 | Ensure JDK 17+ (`Temurin 17` or `21`) is configured in CI and Android Studio Gradle JDK settings. |
| `Workflow not appearing in Actions` | `.github/workflows/android-apk.yml` nested inside a subfolder or ZIP | Move project files so `.github/` is at the repository root. |
| `SDK location not found` | Missing `ANDROID_HOME` / `ANDROID_SDK_ROOT` locally | Open in Android Studio once or set `ANDROID_HOME` in your environment (do not commit `local.properties`). |
| `Nearby Connections unavailable` at runtime | Device lacks Google Play Services | Use the built-in **Wi-Fi / Hotspot** or **Bluetooth Classic** tabs as offline fallbacks. |
| `Wi-Fi NSD peers not visible` | Router has AP Isolation enabled | Use **Mobile Hotspot** mode on one phone and enter Host IP `192.168.43.1` manually. |

---

## Known Limitations & Device Compatibility
- **Physical Range**: Bluetooth (~10m), Wi-Fi Direct / Hotspot (~20–100m), Nearby Connections (~10–50m) depend heavily on walls, interference, and OEM radio power management.
- **No Internet Relay**: Offline Messenger v1 uses a Host-and-Members hub topology within a local radio link; it does not relay messages over the Internet or across multi-hop disconnected meshes.
- **OEM Battery Optimizations**: Some Android devices restrict background Bluetooth/Wi-Fi sockets if battery optimization kills the app; keep the app active during live radio sessions.
