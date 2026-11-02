# Lamp-Da Android

Android controller for official Lamp-Da **v1.9**, Simple and RGB / Indexable.
Control commands use Nordic UART exclusively; no ELK command frames or legacy CFW.

## Controls

- Power and full-resolution brightness (0-1024).
- Six Simple modes; 54 RGB modes arranged by category.
- Effect ramp (0-255), with mode-specific speed/palette/parameter labels.
- Custom RGB channels, color swatches and hex input.
- Eight Indexable favorite slots: recall and save with overwrite confirmation.
- Bluetooth rename (takes effect after lamp restart).
- Sunset by duration or end time, phone clock synchronization and acknowledged countdown.
- Sunrise can be attempted explicitly, with a firmware refusal reported when unavailable.
- Standard BLE battery level in the header and optional advanced UART console; no status dashboard.
- Settings gear groups app themes, lamp type, native favorites, Bluetooth rename
  and About. Themes s0ca, Half-Life (orange/black) and Lambda (pink/cyan) apply immediately and persist
  across app restarts. Mode tiles hide internal group/mode identifiers.
- French / English selection in Settings > App, applied immediately and persisted.
  Raw UART responses, command syntax and Bluetooth device names are not translated.
- The header offers Connect while disconnected and Disconnect only when UART is ready.
- UART shortcuts include official v1.9 queries and editable control templates.
  The bounded, scrollable menu separates General, sys, set and act categories.
  Templates fill the command field without sending; destructive actions have no shortcut.
  Recovery shortcuts require a corresponding manually requested firmware help entry.

Connection startup reads/subscribes to the standard battery characteristic, sends
one compact `t` query for lamp identity, and synchronizes the phone clock.
No help pages or diagnostic dumps are requested automatically. All `h`, `sys h`,
`set h`, version and diagnostic queries remain available manually in the UART tab.

Control positions represent local requests, not synchronized physical-button state.
See [protocol details and limitations](../docs/OfficialFirmwareSupport.md).

## Build

Open this directory in Android Studio, or use:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug -Pandroid.injected.testOnly=false
```

Share `app/build/outputs/apk/debug/app-debug.apk`, not intermediate outputs.
Android Studio Run can produce test-only or architecture-specific builds.
Verify exported signatures using apksigner; minimum Android version is 8.0 (API 26).

## Bluetooth permissions and scanning

Android 12+: only Nearby devices is requested. No location permission or GPS switch
is needed. The manifest declares `neverForLocation`.
Android 8-11: the existing classic BLE scan still needs fine-location permission
and enabled location services; the app does not read GPS coordinates.

Scanning has no Android-level name/service filter. The default list shows all BLE
devices on first use, or Known lamps when lamps have already been remembered.
Known lamps is the first filter. It uses remembered device addresses, not
names or generic service UUIDs. Renaming therefore does not remove a known lamp.
UART services are checked on connection; ELK is not required.
New known-lamp addresses are saved only after a recognized UART lamp-type reply,
not merely when a generic Nordic UART service is discovered.
Names and identity are retained when later advertisements omit them. The displayed
known-lamp badge only uses remembered identity. A UART UUID alone is a candidate,
not proof of lamp identity, and does not grant that badge.

Scan rows rank the strongest smoothed RSSI first, with a two-second exponential
smoothing time constant and at most one automatic reordering every three seconds.
Between rankings, newly discovered devices are appended; name changes do not
move rows. Displayed signal strengths use that same ranking snapshot rather than
flickering with every packet. Equal strengths retain the previous order. The Known lamps filter
uses the same ranking. Stopping a scan performs one final ranking.

Android may filter certain beacon packets with `neverForLocation`. Physical
scan testing is still needed on the target phones and /e/OS versions.

## Experimental local USB firmware update (0.3.3)

Settings > Lamp > USB firmware update opens a separate, manual update workflow.
Connect exactly one Lamp-Da to the phone using a USB data cable with the phone
acting as USB host. Choose a trusted application UF2 named `simple_...uf2` or
`indexable_...uf2` / `rgb_...uf2`, then read its type/version over USB CDC.
The filename is a variant hint, not proof of the firmware contents or authenticity;
the user must check its provenance and hardware compatibility.

Entering DFU sends `act dfu` only after confirmation. Select the root of the
removable bootloader volume (LMBDROOT on the observed lamp) in Android's document
picker, then confirm installation. Its label is not used as proof of identity.
The updater checks the UF2 blocks, nRF52840 family, application address range,
Lampda board ID and UF2 bootloader 0.9.2 before writing `UPDATE.UF2`.
Bootloader/SoftDevice updates and complete flash dumps are not supported.

Bootloader detection checks the mass-storage interface on supported Lamp-Da USB
identifiers: the observed bootloader retains PID 7A05, rather than switching to
8A05. `INFO_UF2.TXT` must still match the supported board and bootloader version.
An already-running bootloader cannot report the application type through UART.
If no verified runtime type is retained, the user must explicitly select and
confirm Simple or RGB after the volume is validated. This is a manual declaration,
not hardware detection; it must match both the physical lamp and chosen firmware.

This first implementation requires Android to mount the UF2 mass-storage device.
Some phones may not expose its filesystem. Do not format it: finish on a computer
if the USB volume is unavailable. GitHub downloads and raw USB mass-storage
transports are not implemented. No Internet or broad storage permission was added.

Keep USB connected while copying. An interrupted transfer has an uncertain outcome
and is never automatically retried. Copy completion is not installation verification:
after reboot, read the USB version again and check the lamp manually. The app
cannot validate battery health, signed authenticity or arbitrary firmware compatibility.
Unit tests and APK/UI checks do not replace a physical phone-to-lamp update test.
