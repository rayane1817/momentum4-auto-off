# M4 GAIA test (Android)

Tiny test tool for the MOMENTUM 4. It connects to the headset's GAIA control channel (RFCOMM, UUID `a2129ff3-081b-4c45-8afe-469d9c4842ec`) and sends single commands. No internet permission, no libraries, Bluetooth only.

## Install (Samsung)
1. On the phone, open this repo on GitHub, go to `android/gaia-test/release/` and download `m4-gaia-test-0.1.apk`.
2. Open it. Samsung asks to allow installs from that app (Chrome / My Files): allow once.
3. Google Play Protect may warn about an unknown app: "Install anyway". It's our own debug-signed build.

## Use
1. Force stop Smart Control Plus first (Settings > Apps > Smart Control > Force stop). Only one app can hold the control channel.
2. Headphones on and connected to the phone as usual.
3. Open "M4 GAIA test", allow "Nearby devices", pick the MOMENTUM 4, tap Connect.
4. Tap the buttons, then "Copy log" and paste the log in the chat.

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
  - Not yet confirmed on a headset end to end. Test with a short time (e.g. 2 min), play nothing, and watch "Show guard log".

## Test plan (limbo timer)
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
