# Lamp-Da Android

Android controller for official Lamp-Da **develop 7bb2044**, Simple and RGB / Indexable.
All controls use official Nordic UART commands. No legacy CFW is required.

## Controls

Power, brightness and the current mode's adjustable ramp share the first control
block. The ramp stays outside the collapsible mode browser. Mode summaries keep
the mode name, ramp value and feedback provenance visible when collapsed.
Modes, Colors and Sunrise/Sunset start collapsed; expansion choices survive tab
switches. Colors retain their HEX/provenance summary. Sunrise/Sunset shows its
configured duration or clock deadline when idle and the acknowledged timer's local
countdown while running, including when collapsed.
The RGB tool uses the same themed surface, 8 dp corners and 16 dp padding as
the neighboring controls, with its title left-aligned and color preview on the right.

- Power and full-resolution brightness (0-1024).
- Six Simple modes; 54 RGB modes arranged by category.
- Effect ramp (0-255), with mode-specific speed/palette/parameter labels.
- Custom RGB channels, color swatches and hex input.
  RGB sliders are shown first by default; the color wheel is an alternate view.
  Both views send `set color R G B` through the serialized UART queue.
- Eight Indexable favorite slots: recall and save with overwrite confirmation.
  Favorite actions use fixed 48 dp icon buttons with localized tooltips and
  accessibility labels including the slot, so long translations cannot wrap.
- Bluetooth rename is available again with the firmware's fragmented command fix.
  The new name applies after restarting the lamp; the app does not restart it.
- Sunset by duration or end time, phone clock synchronization and acknowledged countdown.
- Sunrise can be attempted explicitly, with a firmware refusal reported when unavailable.
- Standard BLE battery level in the header and optional advanced UART console; no status dashboard.
- Settings gear groups app themes, lamp type, native favorites, Bluetooth rename
  and About. Themes s0ca, Half-Life (orange/black) and Lambda (pink/cyan) apply immediately and persist
  across app restarts. Mode tiles hide internal group/mode identifiers.
- French / English selection in Settings > App, applied immediately and persisted.
- Fullscreen switch in Settings > App, enabled by default and persisted. Disable
  it to show Android status/navigation bars; content respects the visible bars.
  The preference also applies to the USB update screen. Android's file chooser
  and permission dialogs retain their system-controlled appearance.
  Raw UART responses, command syntax and Bluetooth device names are not translated.
- The header offers Connect while disconnected and Disconnect only when UART is ready.
- UART shortcuts include official v1.9 queries and editable control templates.
  The bounded, scrollable menu separates General, sys, set, act and debug categories.
  Templates fill the command field without sending; destructive actions have no shortcut.
  Recovery shortcuts require a corresponding manually requested firmware help entry.

Connection startup reads/subscribes to the standard battery characteristic, sends
compact `t` and `sys ble_name` queries, and synchronizes the phone clock.
No help pages or diagnostic dumps are requested automatically. All `h`, `sys h`,
`set h`, version and diagnostic queries remain available manually in the UART tab.

After identifying the lamp, the app requests `sys status` once, then `sys sun` if
the status reply is supported. Mode, brightness and ramp continue to follow
`$mode`, `$brgt` and `$ramp` feedback. There is no automatic diagnostic polling.
See [protocol details and limitations](../docs/OfficialFirmwareSupport.md).

## Multi-lamp control (experimental)

Version 0.4.0 supports up to four independent BLE sessions. Connect a lamp
from Scan, then use Select lamps > Add lamp to connect another without closing
the existing connection. A newly opened connection is selected individually.

In Select lamps, simply check the lamps to control together. One checked lamp
means individual control; multiple checked lamps form a group automatically.
There is no separate group-mode switch or mandatory member. Unchecking the last
lamp leaves all connections open but disables control until a lamp is selected.

The Control target bar lists the names of eligible command recipients separately
from selected lamps that are not currently controlled. Display and UART names
the session supplying the visible positions, battery and console. The lamp list
separates connection state, checkbox selection and command eligibility, and uses
the latest UART-provided name when available.

Groups require matching profiles and verified lamp-type replies through `t`.
Checkboxes can be selected while type verification is pending; unverified group
members receive no commands. A missing identity reply is retried up to three times,
two seconds apart, then can be retried manually with Verify lamp type. A valid
reply overrides an outdated saved profile. If verification reveals mixed types,
group dispatch stops until the incompatible lamp is unchecked.
Power, brightness, modes, ramp, RGB colors, clock and sunrise/sunset controls
target eligible checked lamps. Known different-type peers cannot be added.
An unavailable checked lamp can always be unchecked. Selection is not restored
after closing the app.

The active lamp supplies the displayed name, battery, requested control positions,
timer and UART log. It stays active while selected and available; another checked
ready lamp takes over if it disconnects. UART commands, favorites and profile
settings remain active-lamp-only. Changing the profile returns to one selection.
Open in Scan likewise selects that existing connection individually.

Brightness, ramp, mode and RGB controls label value provenance: Local value,
Requested/unconfirmed, or Received from lamp. Merely sending a command does not
count as feedback, including when the requested value equals the previous reading.
An incoming value is the latest reading, not an acknowledgement of a particular
request. Feedback ignored during a slider drag does not change its provenance.
Mode changes invalidate the previous ramp reading until a new ramp signal arrives.
Disconnect invalidates provenance while retaining the last UI positions.

For a group, all eligible selected peers must report the same value for a control
to be labeled Received from lamp. Different reported values are labeled Different
values, while numeric positions still belong to the displayed session. Missing
recipients yield Not synchronized. Custom RGB colors remain unconfirmed because
the firmware has no color feedback; a custom-mode signal cannot confirm RGB values.
Power buttons are neutral commands, not indicators of the actual on/off state.

Each session has its own GATT client, queues, UART buffer, battery and reconnect
schedule. Disconnected peers miss commands, with no replay after reconnecting;
remaining checked peers can still be controlled. The counter shows eligible
versus selected lamps. Errors are reported individually in the lamp list.
Disconnect removes only that session and preserves its remembered known-lamp entry.
Reconnect attempts reset only after notification setup completes, not merely
after service discovery. Setup failures are retained in the UART log and Android
logcat (`LampDaBle`). A failed optional Battery Service subscription does not
tear down the required UART connection; the initial battery read is still attempted.

There is no group-wide acknowledgement or rollback, and animations are not
precisely synchronized. Mixed Simple/RGB groups are not supported. Physical
multi-lamp validation, particularly bonding and reconnect behavior, remains
necessary on each target phone.

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
UART services are checked on connection. No ELK characteristic is used.
The app requests MTU 247 and uses the negotiated payload, capped at 244 bytes;
20-byte fallback packets are assembled into whole commands by updated firmware.
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
