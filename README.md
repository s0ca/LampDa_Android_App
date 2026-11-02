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

## Signed GitHub Releases

The **Signed release APK** workflow runs only when a `v*` tag is pushed. It tests
and lints the release variant, builds an unsigned release APK, and signs it using
repository Actions secrets. The app's Gradle configuration does not contain keys
or passwords. Signing secrets are exposed only to the signing step, not Gradle.

Configure these repository secrets first:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-encoded private release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias, for example `lampda` |
| `KEY_PASSWORD` | Key password |

Before tagging, update `versionName` and increase `versionCode` in
`android-app/app/build.gradle.kts`, then commit and push. The tag must exactly
match `v` followed by `versionName`, for example `v0.3.10`. Prerelease versions
such as `0.3.10-beta.1` must also match their tag. Do not reuse or move release tags.

```sh
git tag v0.3.10
git push origin v0.3.10
```

Only use that example after setting `versionName` to `0.3.10`. The workflow rejects
mismatches. It verifies the APK signature against the configured key certificate,
rejects debuggable APKs, and computes a SHA-256 checksum. A separate job then
creates a **draft** Release containing the signed APK, checksum and public signing
certificate report. Review the generated notes and publish manually in Releases.
Tags containing a prerelease suffix produce prerelease drafts. An existing Release
is not overwritten automatically; review an existing draft before rerunning.

Never commit the keystore or its Base64 representation. Back up the private key
and password securely outside the repository. The signing step deletes its
temporary keystore on exit. Only the draft-creation job gets repository write
permission; no source code is checked out or built in that job. No release signing
secrets are used by the debug/PR workflow.

The release key differs from a local debug key: existing debug installs may need
a one-time migration, with possible loss of app settings. Subsequent releases
must retain the same signing identity and increase `versionCode` to update in place.

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
