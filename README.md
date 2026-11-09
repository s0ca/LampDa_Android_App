# Lamp-Da Android

Android companion application for Lamp-Da lamps running the updated official develop firmware.
Current application version: **0.4.0**. Android 8.0 (API 26) or newer.

Beta versions install as **Lamp-Da Beta** (`com.lampda.app.beta`), alongside the
stable app (`com.lampda.app`). Settings and permissions are separate; existing
settings are not automatically migrated to the beta app.

## Features

- Bluetooth scanning, remembered lamps and standard battery level.
- Experimental multi-lamp control: up to four sessions, individual or same-type groups.
- Named command targets and explicit local/requested/reported control values.
- Simple and RGB modes, brightness, effect parameters and custom colors.
- Sunrise / sunset controls, subject to firmware support.
- French / English interface and s0ca, Half-Life and Lambda themes.
- Optional fullscreen mode, saved in Settings > App.
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

## Download

Download the latest signed APK from [GitHub Releases](https://github.com/s0ca/LampDa_Android_App/releases).

The stable app checks GitHub for newer stable releases at launch. Updates are
downloaded and installed manually; the app still works offline.

Development builds are available in GitHub Actions artifacts. They are intended
for testing and may not update an existing installation.

## Known Limitations

All controls use UART, including RGB. The updated develop firmware is required
for reliable fragmented commands and live mode, brightness and ramp feedback.
BLE MTU negotiation uses larger packets when supported, with a 20-byte fallback.
Power and RGB color readback are not provided by the current firmware signals.
RGB hardware coverage is incomplete. BLE transport completion is not a firmware
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
