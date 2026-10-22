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
