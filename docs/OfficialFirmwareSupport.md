# Official firmware UART support

Reference: official **develop**, commit `78e92e3` of BaptisteHudyma/Lamp-Da,
retrieved on 2026-11-04. Uses the v1.9 command family with newer transport fixes and signals.

## Transport

Nordic UART service: `6e400001-b5a3-f393-e0a9-e50e24dcca9e`.
Write to `6e400002-b5a3-f393-e0a9-e50e24dcca9e`;
subscribe to `6e400003-b5a3-f393-e0a9-e50e24dcca9e`.
Each UTF-8 command ends in a newline. A serial operation queue splits writes into
negotiated ATT payloads (MTU minus 3, capped at 244 bytes), preserving command order.
MTU 247 is requested before service discovery; failure falls back to MTU 23.
All RGB controls use `set color R G B`, including sliders, wheel, swatches and HEX.
The updated firmware retains partial lines, so 20-byte fallback packets are safe.
The scan list defaults to all BLE devices. Its Known lamps filter and badge use
remembered addresses, not generic UUID/name matching. A new known address is saved
only after a recognized lamp-type UART reply.
The app waits for pairing and notification subscription before sending commands.
Standard Battery Service notifications remain enabled independently.

## Commands

| Control | UART command |
| --- | --- |
| Power | `set on 0` / `set on 1` |
| Brightness | `set brgt N`, integer 0-1024 |
| Effect parameter | `set ramp N`, integer 0-255 |
| Mode | `set mode GROUP MODE` |
| RGB | `set color R G B`, each integer 0-255 |
| Clock | `set time H M S DAY`, Sunday = 0 |
| Sunset | `set sunset H M S DAY` |
| Recall RGB favorite | `set favorite INDEX`, 0-7 |
| Save RGB favorite | `set new_favorite INDEX`, 0-7 |
| Rename | `set ble_name NAME` |
| Current Bluetooth name | `sys ble_name` |

Slider values are transmitted directly, without percentage quantization.
Ramps are labeled as speed, palette, color temperature or effect parameter.
Pulse 1 and Pulse 2 have no adjustable ramp in official v1.9.
Names accept 1-30 ASCII letters, digits or hyphens. Renaming applies after the
lamp restarts; the app does not reboot it automatically.

## Live feedback

`$brgt:N`, `$ramp:N` and `$mode:GROUP:MODE` update only the originating session's
controls without sending an echo command. `$ind:N` is recognized but has no UI control.
Values are range checked; unknown modes do not change the selected tile.
`ble_name: NAME` updates the header using the actual configured name, not the
Android advertising cache. A rename acknowledgement does not imply advertising
has restarted: the configured name is re-read, but advertising changes after reboot.
Startup sends only `t`, `sys ble_name` and clock synchronization in addition to
standard battery reads/notifications. Help and diagnostic dumps remain manual.
No full state snapshot, power signal or RGB color signal is currently available.
UI provenance is tracked independently for each control and session. Requests are
not treated as acknowledgements. Reconnect clears reported provenance; a group
is only labeled as reported when all recipients report the same control value.

## Mode catalogs

Simple: Fixed (0/0), Candle (1/0), Lightning (1/1), Strobe (2/0),
Pulse 1 (2/1), Pulse 2 (2/2).

Indexable groups follow `src/user/indexable_functions.cpp`:

| Group | Modes |
| --- | --- |
| 0 Palettes | 0 Black body, 1 Rainbow, 2 Papi |
| 1 Calm | 0 Rainbow swirl, 1 Rainbow fade, 2 Perlin noise, 3 Aurora, 4 Fire, 5 Sine, 6 Spiral, 7 Distortion waves, 8 Gravity, 9 Rain, 10 Bubbles, 11 Sierpinski |
| 2 Party | 0 Color wipe, 1 Double-side fill, 2 Ping pong |
| 3 Sound | 0 VU meter, 1 FFT spectrum |
| 4 Images | 0 Heineken, 1 HuitSix, 2 Violonsaouls, 3 Beer glass |
| 5 Color effects | 0 Custom RGB; 1-29 fixed/jump/fade/strobe effects from `bluetooth_group.hpp` |

Groups 4 and 5 are hidden from default physical navigation but addressable via
`set mode`. Catalogs target unmodified v1.9 builds, not arbitrary custom firmware.
CCT is not offered: its v1.9 user-command handler is empty.

## Favorites

Only Indexable implements favorites in v1.9. Capacity is eight, despite CLI help
mentioning 0-15. UI slots 1-8 map to indices 0-7. Saving asks for confirmation.
Firmware accepts replacing an existing slot or appending the next slot, not gaps.
Default Indexable firmware has six favorites. The lamp must be on; saving while
inside the favorite group is ignored. Sending is not presented as confirmed saving.

## Sunset and future Sunrise

Duration and clock scheduling both synchronize time and use an absolute deadline,
avoiding the 10-minute-per-call clamp on `set sunset_min`.
The countdown starts only after `sunset timer set to` / `sunset timer updated to`.
Refusals and confirmation timeouts are shown. The countdown is local and is not
restored on reconnect; firmware-side deadline changes are not fully tracked yet.

Sunrise is **absent from tag v1.9**. Its UI is retained for newer firmware and
can be attempted explicitly; an unknown-command reply marks it unavailable for
the current connection. Help pages are no longer requested automatically.
Future Sunrise uses `set sunrise H M S DAY`. The newer generic
`sun timer set to` / `sun timer updated to` replies are also recognized.


## Android permissions

Android 12+: Nearby devices only (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`), with
`neverForLocation`. No location permission or location-service requirement.
Android 8-11: classic BLE scans still need fine location and location services.
The app does not read GPS coordinates.
Android documents that `neverForLocation` can filter some beacon advertisements;
test physical lamp discovery on affected phones, including /e/OS.

## Validation

Unit tests cover commands, value bounds, mode addresses, favorites, naming,
timer replies, Sunrise detection, scan identity and permission selection.
Physical BLE validation remains necessary: pairing, modes, RGB, timers, favorites,
renaming and Android 12+ discovery with location disabled.
