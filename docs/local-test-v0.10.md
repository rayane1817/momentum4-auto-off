# Local test run: app v0.9.1 fixes + v0.9 features + v0.10 widgets

For the LOCAL Claude Code session on the Windows laptop. Same rules, tools and safety as `docs/local-session-plan.md` (sections 1, 4, 5). Expected time: about 45 min agent, 5 min human.

## Kickoff prompt (paste into a new local session in the repo folder)

```
You are the LOCAL hardware session for the MOMENTUM 4 project. git pull first.
Read: CLAUDE.md, docs/local-session-plan.md (sections 1, 4, 5: rules, failure table, setup),
then docs/local-test-v0.10.md and follow it phase by phase.
Rules stay: only documented commands via gaia_client.py check; blocklists stay; raw data in
%USERPROFILE%\m4-private; every test entry records fw, app + version, cups, case, result.
The only 0605 of the run is the guard regression at the very end.
Show me the START checklist from docs/local-test-v0.10.md now, then begin.
```

## START checklist (human, about 3 min)
1. Phone unlocked, USB in, Smart Control Plus force-stopped. Headphones on the desk, cups up, switched on.
2. When the agent says so: uninstall the old "M4 GAIA test" (one time only: v0.9.1+ uses a new fixed signing key).
3. After the agent installs v0.10: long-press the home screen > Widgets > "M4 GAIA test": add **M4 noise control** and **M4 EQ preset**.
4. Pull down Quick Settings twice > edit (pencil) > drag **M4 noise** and **M4 EQ** into the active tiles.
5. Open the app once, allow Nearby devices, pick the MOMENTUM 4. Tell the agent "done".

## Phases (agent)

### A. Install and signing
- Install the GitHub release APK (`m4-gaia-test-0.10.apk` from the v0.10 release), then `adb install -r` the APK built from the repo (`android/gaia-test/release/m4-gaia-test-0.10.apk`). PASS: the second install succeeds without uninstall (same key, CI and local). Record the cert SHA-256 (`apksigner verify --print-certs`, expect `285fe924...4a78`).
- Backup off: PASS if after the one-time uninstall + install the app starts with defaults (no guard autostart, no remembered device).

### B. v0.9.1 fixes
1. **Anti-wind mapping:** Custom mode, tap Off, Auto, Max in the app. PASS: log shows `1A00 [01 00]`, `[01 02]`, `[01 01]` in that order (wire 1 = Max, 2 = Auto, T17). Then set `1A00 [01 01]` from the PC (`check`) and reconnect the app: the app must show **Max** selected; `[01 02]` must show **Auto**.
2. **No 1407 hint:** toggle Hi-Res; no red "Restart needed" text appears; the app sends no `1407`.
3. **Guard state:** start guard, stop guard, then enable autostart and relaunch. PASS: button text and status match `dumpsys activity services be.ryane.gaiatest` each time (within 2 s).
4. No "Bluetooth is off" log line while turning Bluetooth on (`cmd bluetooth_manager disable` / `enable`).

### C. v0.9 features (first time on hardware)
1. **Presets:** tap each of the 8 presets. PASS: five `1001` writes per preset with the gains in `android/gaia-test/app/src/main/java/be/ryane/gaiatest/Presets.java`; PC `1003 [00]` readback equals them; the tapped chip is highlighted. Then open SCP (uiautomator) and confirm it shows the same preset name (label oracle). Restore Neutral.
2. **0.1 dB slider:** move one band to +0.3 dB (adb swipe or uiautomator set), PASS: `1001 [band, 03]`.
3. **Comfort call / Touch controls switches:** each on/off from the app. PASS: PC readback `0815` follows the switch; `1607` is the inverse (on = `00`). Restore.
4. **EQ read on connect:** app sends `1003 [00]` and sliders show the PC-set curve.

### D. v0.10 custom presets
1. Set a curve (e.g. +1.0/-0.5/0/+0.5/+2.0), "Save current curve as a custom preset", slot 1, name "Test A". PASS: a "★ Test A" chip appears and is highlighted.
2. Save a second one in slot 2. Force-stop and reopen the app: both chips still there.
3. Tap "★ Test A": five `1001` writes with its gains. Long-press it: Rename, Overwrite, Delete each work.
4. Fill all 5 slots once: the 6th save must overwrite the chosen slot (no crash). Delete the test presets at the end.

### E. v0.10 widgets and tiles
Use `adb shell input keyevent KEYCODE_HOME` + `uiautomator dump` to find `w_adaptive`, `w_custom`, `w_off`, `w_eq_prev`, `w_eq_next`; tiles via `adb shell cmd statusbar expand-settings` and the tile labels "Noise: ..." / "EQ: ...". Watch `adb logcat -s M4REMOTE M4GAIA`.
1. **App closed** (force-stopped): tap widget Adaptive, Custom, Off. PASS per tap: `M4REMOTE` shows the frames (Adaptive `1A04 01` + `1A00 03 01`; Custom `1A00 03 00` + `1A04 01`; Off `1A00 03 00` + `1A04 00`), the link closes after, the selected chip highlights, PC snapshot shows the mode. Time from tap to applied: record it.
2. **EQ widget:** next/prev cycle through the 8 presets plus custom ones; the name updates; PC readback matches.
3. **Tiles:** noise tile cycles Adaptive > Custom > Off; EQ tile steps presets; labels update.
4. **App open and connected:** tap widget buttons. PASS: no second connection, frames go through the app (`M4GAIA` shows them), the app UI follows within 1 s (notifications).
5. **Busy / off cases:** with SCP open and connected, tap a widget: PASS = a "busy or off" toast, no crash, no stuck tile. With the phone's Bluetooth off: "Bluetooth is off" toast.
6. Rotate / resize the widgets once (human, optional): layout stays usable.

### F. Regression (last)
- Guard: 2 min timeout, no audio, screen off. PASS: `0605` sent, headphones off. This is the run's only `0605`.
- `tools/blocklist_parity_test.py` passes.

## END checklist (human, about 2 min)
1. Headphones off after the guard test? Yes/no.
2. Press the button to switch them on.
3. Optional: look at the two widgets and say if they are readable and useful as they are.

## Deliver
- T23+ entries in CLAUDE.md (fw, app + version, cups, case, result per test).
- Branch `local/v010-validation-<yyyymmdd>`, PR with a results table and any app bugs with exact repro, like PR #12.
- Restore every setting; final snapshot diff empty.
