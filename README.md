# Lamp-Da Android

Android companion application for Lamp-Da lamps running firmware v1.9.
Current application version: **0.3.9**. Android 8.0 (API 26) or newer.

## Features

- Bluetooth scanning, remembered lamps and standard battery level.
- Simple and RGB modes, brightness, effect parameters and custom colors.
- Sunrise / sunset controls, subject to firmware support.
- French / English interface and s0ca, Half-Life and Lambda themes.
- Optional advanced UART console and experimental local USB UF2 updater.

See the [application guide](android-app/README.md) and
[firmware protocol notes](docs/OfficialFirmwareSupport.md).

## Build And Test

Install Android Studio / Android SDK platform 35 and use JDK 21 to run Gradle.
The application targets Java / Kotlin 17 bytecode.
Open `android-app` in Android Studio, or set `ANDROID_HOME` to your Android SDK:

```sh
cd android-app
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -Pandroid.injected.testOnly=false
```

The debug APK is produced at `android-app/app/build/outputs/apk/debug/app-debug.apk`.
Initial builds require Internet access to download Gradle and dependencies.
No firmware binaries, SDK paths, signing keys, Gradle caches or APKs are included.

## Automatic APK Builds

The **Android APK** GitHub Actions workflow runs for pushes to `main`, pull
requests targeting `main`, and manual runs from the Actions tab. It runs unit
tests and lint before producing a debug APK, with no repository signing secrets.

After a successful run, open **Actions > Android APK > the run > Artifacts** and
download `LampDa-debug-<commit>`. Extract the archive to obtain the APK and its
SHA-256 checksum. Artifacts are retained for 14 days, subject to repository policy;
they are not GitHub Releases. Test and lint reports are uploaded separately,
including when a build step fails and reports are available.

These are testing builds. A fresh GitHub runner generates a temporary debug key,
which can differ between runs and from your locally installed app. Android will
reject an in-place update signed by another key. Do not uninstall an existing
app merely to resolve this without backing up its data. Stable production updates
require a dedicated, securely backed-up signing key and a separate release setup.
Do not commit signing keys or passwords to this repository.

## Known Limitations

Controls represent requests, not synchronized physical-button state. RGB hardware
coverage is incomplete. Long UART commands can fail over BLE with firmware that
does not retain fragmented commands until the terminating newline; renaming over
USB has been confirmed separately. BLE transport completion is not a firmware
application acknowledgement.

USB flashing requires a compatible board, trusted application UF2, a working USB
host connection and explicit confirmations. Never format the bootloader volume.
The app does not validate battery health or firmware authenticity.

## Project Status

This is an unofficial companion project. Lamp-Da firmware is maintained separately
by [BaptisteHudyma](https://github.com/BaptisteHudyma/Lamp-Da).
Product names and artwork do not imply endorsement.

This repository contains the Android application, not the firmware workspace
history. Release signing and physical hardware coverage must be reviewed before
distributing production APKs.

## License

Original application code is Copyright (c) 2026 s0ca and licensed under the
[MIT License](LICENSE). Keep the copyright and license notice when redistributing
copies or substantial portions of the code.

Dependencies retain their own licenses. See the
[third-party notices](licenses/THIRD_PARTY_NOTICES.txt) and bundled license texts
in `licenses/`, which are also packaged as APK assets. Gradle's distribution
license and notices are retained in `tools/legal/`.

The code license does not grant rights to third-party trademarks or artwork.
Product-inspired branding requires separate review and implies no endorsement.
