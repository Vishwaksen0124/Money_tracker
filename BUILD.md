# Building Money Tracker

> v0.1 scaffold. See `PLAN.md` for the full plan (§1–20). This document covers
> only how to compile and install what's in this repo right now.

## What v0.1 contains

- Empty encrypted Room/SQLCipher database with hardware-backed key.
- Biometric / device-credential app lock with 60s background timeout.
- `FLAG_SECURE` on the host Activity (no screenshots, no recents preview).
- Gradle task that fails the build if the merged manifest ever contains
  `android.permission.INTERNET` or `ACCESS_NETWORK_STATE`.
- Data-extraction rules and `allowBackup=false` against Google / OEM cloud
  backup and device-to-device transfer.
- `SecureLogger` that compiles to a no-op in release builds.

No SMS capture, no parsers, no UI beyond the lock + empty dashboard yet —
that's v0.2.

## Prerequisites

This machine currently has JDK 8 and no Android SDK. Both are required.

```bash
# 1) JDK 17
sudo apt update && sudo apt install -y openjdk-17-jdk
sudo update-alternatives --config java   # choose Java 17

# 2) Android SDK — easiest via Android Studio
#    https://developer.android.com/studio
# Or command-line only:
sudo apt install -y android-sdk
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"

# 3) Environment
echo 'export ANDROID_HOME=$HOME/Android/Sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools' >> ~/.bashrc
exec $SHELL
```

## First-time setup

The Gradle wrapper jar is intentionally not committed. Generate it once:

```bash
cd /home/vishwak/Desktop/Money_tracker
gradle wrapper --gradle-version 8.10.2
```

If you don't have `gradle` on PATH, open the project in Android Studio and
let it "Sync Project with Gradle Files" — that will create the wrapper.

## Build

```bash
./gradlew assembleDebug
```

The build will **fail by design** the moment any code or library adds the
`INTERNET` permission (Hardening §20.1). That's the network-isolation
guarantee being enforced.

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Install on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Note: the device must have a screen lock (PIN / pattern / biometric)
configured, otherwise the app refuses to unlock. This is intentional —
without a device lock there's no Keystore-backed material to protect the DB.

## Verifying v0.1

After install, on the device:

1. Launch — the biometric / device-credential prompt appears.
2. Authenticate — the dashboard reads "Ready (0 transactions)".
3. Try a screenshot — the system refuses (FLAG_SECURE).
4. From a connected dev machine:
   ```bash
   adb shell run-as app.moneytracker.debug ls databases/
   adb shell run-as app.moneytracker.debug cat databases/mt.db | head -c 32 | xxd
   ```
   The file should not begin with `SQLite format 3` — it's an opaque
   ciphertext blob.
5. Background the app for >60s, return — re-auth is required.

## Release build

Release signing config is intentionally not in the repo. Provide it via a
keystore outside the source tree, e.g. in `~/.gradle/init.d/signing.gradle.kts`.
Until that's set up, `./gradlew assembleRelease` will produce an unsigned APK.

## Troubleshooting

| Symptom | Cause |
|---|---|
| `Hardening §20.1 violated: …INTERNET…` | A dependency declared `INTERNET`. Find with `./gradlew :app:processDebugManifest --info`. Remove the dep or downgrade. |
| App refuses to unlock, "No device lock set" | Set a PIN / biometric in system Settings → Security. |
| `HardwareBackingUnavailableException` | Device has no TEE / StrongBox. This app refuses software-only keys by design. Use a different device. |
