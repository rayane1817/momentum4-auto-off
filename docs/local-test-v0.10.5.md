# Local check: v0.10.5 design (light and dark)

v0.10.5 = design v2 direction A ("Copper and graphite", `docs/design-spec-v2.md`) on top of v0.10.1. Restyle only: the 28 `send(...)` calls in `MainActivity` are identical to v0.10.1 (checked by diff), `Remote`, `Gaia`, `AutoOffService` and `Presets` are untouched. Same rules as `docs/local-session-plan.md`. About 20 min, no `0605` needed.

## Install
- `adb install -r android/gaia-test/release/m4-gaia-test-0.10.5.apk` over 0.10.1 (same key, no uninstall).

## Checks
1. **Screenshots, light and dark:** `adb shell cmd uimode night no`, then `yes`. For each: open the app (connected and not connected), scroll top to bottom, `adb exec-out screencap -p > %USERPROFILE%\m4-private\v0105-<mode>-<n>.png`. Look for: text readable everywhere, no white blocks in dark, header not under the status bar, copper only on live things (status dot, selected key, active preset, armed guard, primary buttons, faders, switches).
2. **Widgets repaint in dark mode** without opening the app: toggle `cmd uimode night` with both widgets and both tiles on the home screen. Selected noise key = dark fill + dot; EQ widget shows the dot + "Preset n of N".
3. **Frame regression:** run the v0.10 UI script (noise modes, anti-wind, a preset, a slider, each switch, Hi-Res, call slider) and compare the `M4GAIA` `->` lines with the v0.10.1 run. Must be identical.
4. **Status line:** Not connected / Connecting... / Connected / "Connection lost. Tap Connect." (switch the headphones off while connected) / "Couldn't reach the headphones..." (connect while off) / Bluetooth off text.
5. **Guard panel:** big "Off" / "15 min", status text, Start guard (copper) / Stop guard (outline), dot in the title when armed.
6. Dialogs (save preset, power off) readable in dark mode; the power-off button text is red.

## Label changes (update selectors in `tools/adbui.py` callers and older test docs)
| v0.10.1 | v0.10.5 |
|---|---|
| START GUARD / STOP GUARD | Start guard / Stop guard |
| POWER OFF NOW | Power off now |
| Show guard log | Guard log |
| Save current curve as a custom preset | Save curve as custom preset |
| Hi-Res 24/96 (restart the headphones after changing) | Hi-Res 24/96 (+ caption) |
| Start automatically when the app opens | Start the guard when the app opens |
| Connect + Disconnect (two buttons) | one button, label follows the state |
| ★ Name | 1 Name (slot number) |
| Idle timeout | Idle time |
| — (unknown value) | not read |
| Section titles in capitals (NOISE CONTROL, EQUALIZER, ...) | Sentence case: Noise control, Sound, Auto power-off, Listening, Device |
| Disconnected / Connected pill | status line with a dot: Not connected / Connected |

Section order is now: Noise control, Sound, Auto power-off, Listening, Device, Power off, Advanced, Log.

## Deliver
T-entries in CLAUDE.md, screenshots stay in m4-private (they show the device name), a PR with findings. The combined result of this and the 0.10.1 retest becomes v0.11.0.
