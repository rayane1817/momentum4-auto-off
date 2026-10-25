# M4 GAIA test (Android)

Tiny test tool for the MOMENTUM 4. It connects to the headset's GAIA control channel (RFCOMM, UUID `a2129ff3-081b-4c45-8afe-469d9c4842ec`) and sends single commands. No internet permission, no libraries, Bluetooth only.

## Install (Samsung)
1. On the phone, open this repo on GitHub, go to `android/gaia-test/release/` and download the latest `m4-gaia-test-*.apk` (currently 0.9.1), or grab it from the Releases page.
2. Open it. Samsung asks to allow installs from that app (Chrome / My Files): allow once.
3. Google Play Protect may warn about an unknown app: "Install anyway". It's our own debug-signed build.

## Use
1. Force stop Smart Control Plus first (Settings > Apps > Smart Control > Force stop). Only one app can hold the control channel.
2. Headphones on and connected to the phone as usual.
3. Open "M4 GAIA test", allow "Nearby devices", pick the MOMENTUM 4, tap Connect.
4. Tap the buttons, then "Copy log" and paste the log in the chat.

## v0.9.1 (fixes from the hardware session, PR #12)
- **Anti-wind labels fixed**: on the wire 1 = Max, 2 = Auto (T17). v0.8 and v0.9 had Auto and Max swapped.
- **"Restart needed" hint removed**: `1407` reads `01` all the time on 3.38.3, so it is not a restart flag (T15, T21).
- Guard button and status now follow the service (they were one step behind after start/stop and autostart).
- **Fixed signing key** (`keystore/m4-shared.jks`, public on purpose): from 0.9.1 on, every release installs over the previous one. **Uninstall the old app once** before installing 0.9.1, because older builds were signed with throwaway keys.
- Backup off (`allowBackup="false"`), so a reinstall no longer restores "autostart" and starts a guard unasked.
- No "Bluetooth is off" message while Bluetooth is still turning on.

## v0.9
- **EQ presets**: Neutral, Rock, Pop, Dance, Hip-Hop, Classical, Movie, Jazz (Smart Control's values, checked against our capture). The matching preset is highlighted, like in Smart Control.
- EQ sliders in **0.1 dB** steps (was 0.5), read in one go with `1003 [00]`.
- **Comfort call** (`0814`/`0815`) and **Touch controls** (`1606`/`1607`, inverted on the wire).
- **"Restart needed" hint** under Hi-Res, from `1407` (01 = a Hi-Res change waits for a restart).
- Not yet tested on the headset.

## Smart Control Plus parity (v0.8)
Built from the capture-2 decode (`../docs/config-spec.md`), same frames as the official app:
- **Noise control**: Adaptive / Custom / Off. Custom shows an ANC ↔ Transparency slider (`1A02`, 0 = full ANC, 100 = full transparency) and anti-wind Off / Auto / Max (`1A00 01 x`). `1804` is no longer used.
- **Equalizer**: 5 bands (63 Hz, 250 Hz, 1 kHz, 4 kHz, 8 kHz), -6 to +6 dB in 0.5 dB steps (`1001`), Flat button, bass boost (`1008`), speech clarity (`0803`).
- **Listening**: on-head detection (`0400`), Hi-Res 24/96 (`0405`, restart the headphones after changing), and a 0-4 "transparency during calls" slider (`0805`) that replaces the old Sidetone switch.
- On connect the app registers for change notifications (`0007` for features 0x0d, 0x08, 0x04), so changes made on the headset show up live.
- Safety: `0607` and `0609`-`06FF` (reset commands next to power-off, per omarchy-momentum4's PROTOCOL.md) are now blocked too.
- Unconfirmed: anti-wind Auto/Max values may be swapped (another project reports 1 = Max, 2 = Auto). Sliders send only on release.

## UI (v0.5)
Redesigned to a clean card layout (spec: `../docs/ux-spec.md`): a header with a Connected/Disconnected pill, device picker and Connect/Disconnect; a DEVICE card (battery, model, firmware, hardware); a NOISE CONTROL card with a 3-way ANC / Transparency / Off segmented control and a transparency slider; a LISTENING card of switches (smart pause, auto-answer, low latency, sidetone); a prominent AUTO POWER-OFF card (default 15 min, Start/Stop, "start automatically when the app opens"); a red POWER OFF NOW; and collapsed ADVANCED (firmware timer, raw hex) and LOG sections. Controls grey out when disconnected. The idle-guard card stays usable while disconnected (the guard connects on its own).

## Config controls (v0.4)
A "Config" section exposes the reversible settings, with every reply decoded in the log. See `../docs/config-spec.md` for the exact commands and what was verified against the capture.
- **Read all config**: reads ANC, transparency, smart pause, auto-answer, low latency, sidetone, codec, voice prompts, battery, model, firmware, hardware revision.
- **ANC / Transparency**: on the M4 these are mutually exclusive, so each button sets both toggles together ("ANC on" = transparency off + ANC on, etc.). "Noise control off (both)" is the neutral state. "Set transparency level" takes 0-100.
- **Smart pause, Auto-answer, Low latency, Sidetone**: on/off.
- Anti-wind and adaptive ANC sub-modes are NOT exposed yet: their set command (`1A00`) was never captured, so it is unconfirmed. To add them safely, toggle them in Smart Control Plus with HCI logging on and capture the bytes. Details in `../docs/config-spec.md`.

## Power off and auto power-off (v0.3)
The confirmed remote switch-off is `0495:0605` (tests T8-T10). Two ways to use it:

- **POWER OFF NOW** button: connect, tap it, confirm. The headphones switch off fully (same as the case or a 3 s button hold). They can only be turned back on with the button, never remotely.
- **Auto power-off when idle**: a background guard that sends `0605` after the headphones are idle for the set minutes. Set the minutes, tap "Start idle guard". It runs as a notification; tap "Show guard log" to see what it is doing.
  - **Idle is judged from the phone, not the headset:** idle = no audio actually playing (Android's "music active") AND no call in progress. This is deliberate. The headset's own on-head sensor reports on-head even when the M4 is on a desk (T-series), and Android keeps the audio route open when nothing plays, so both headset-side signals are useless here. Any audio or call resets the clock.
  - The guard does NOT hold the control channel while waiting. It only connects to the headset at the moment it powers them off, so they (and Smart Control Plus) stay usable until then.
  - Downside of the phone-side rule: if you wear them in silence (ANC, no audio) past the timeout, they will switch off. Recoverable with the button. Raise the timeout if that bothers you.
  - Confirmed on a headset end to end (test T12, v0.3). To try it quickly, set a short time (e.g. 2 min), play nothing, and watch "Show guard log".

## Historical: limbo timer test (v0.1)
Result: the limbo timer (timer 1) doesn't exist on firmware 3.38.3, so this lead is closed (test T4b). Kept for reference.

1. Tap "Auto-off timer (0)": expect 900 s.
2. Tap "Limbo timer (1)": note the value. This is the unknown.
3. Tap "Timers 2-4" (just to see what else exists; errors are fine).
4. Type 60 in seconds, tap "Set limbo (1)". The app reads it back.
5. Disconnect, take the headphones off, cups up, phone Bluetooth off, wait 20 min, Bluetooth on. Reconnects instantly = still standby. Doesn't reconnect = fixed.
6. To undo: type the value from step 2, tap "Set limbo (1)".

## Safety
The app refuses factory reset (`0495:0040`), anything in Sennheiser feature 1 (firmware update / DFU mode, `0495:02xx-03xx`), deleting paired devices (`0495:1405/1406`) and Qualcomm's upgrade feature, including when typed in the raw field.

## Build
`./gradlew assembleRelease` (needs the Android SDK, `sdk.dir` in `local.properties`). Output: `app/build/outputs/apk/release/app-release.apk`, signed with the debug key.
