# MOMENTUM 4 auto-off: handoff

Owner: Ryane (Brussels). Sennheiser MOMENTUM 4 (fw 3.38.3), Galaxy Z Flip6 (Android 16) + Nokia XR20 (Android 14).
Full history and test log T1-T42: `docs/history.md`. Read it only when a task needs a past result.

## Communication
- Direct, no preamble, no fluff. Ryane often reads on a phone: outcome first, short.
- Correct Ryane's English/Dutch/French phrasing briefly and show a better version.
- No em-dashes in texts written for others.

## The problem and the fix (settled)
- The firmware's auto-off timer only reaches a standby that keeps Bluetooth on (T1-T6). The case and the 3 s button press are true off.
- `0495:0605` (GAIA v3, feature 3, cmd 5, empty payload) is a remote true off. Proven on 3.38.3, also while the phone holds audio (T8-T10). Wake only with the button.
- The app's guard (AutoOffService) judges idle on the PHONE (no music, no call) for N min, then connects and sends 0605 (T12, T20, T29, T35, T42). Headset-side idle signals are useless here (T11).
- Known tradeoff: wearing them in silence past the idle time switches them off.

## Safety rules (never break)
- Never send `0495:0200` (DFU), `0040` (factory reset), feature 1, `1403/1405/1406`, `0607`, `0609-06FF`, `1500`, Qualcomm feature 6. App `Gaia.blockedReason` and PC `blocked_reason` must stay in parity (`tools/blocklist_parity_test.py`).
- Only documented commands (`docs/command-map.md`). No sweeps. `0605` only via the app (button or guard) or a planned test.
- No Bluetooth addresses or other identifiers in git. Raw captures stay in `%USERPROFILE%\m4-private\` on the laptop.
- No Sennheiser or Nothing marks or fonts in the app.

## Current state (1 Nov 2026)
- Released: v0.14.0 (1 Nov 2026) = design v4 (`docs/design-spec-v4.md`: Nothing X home, Poweramp `EqFaders` + `EqGesture`, dot-matrix `EqCurve`, preset sheet, grouped rows + line icons `res/drawable/ic_*`, guard page + tile switch) on top of 0.13.0 (guard back-off, Copy report, issue form). Laptop run of v0.14.0 done 1 Nov 2026 (T43-T57, Z Flip6): protocol, guard and widgets PASS; open bugs: blank device picker (T50), guard UI stale after the guard fires (T53), release APK hash differs from `SHA256SUMS` (T43), plus visual notes.
- Next: Ryane posts `docs/reddit-post-v2.md`; about 7 days later ONE session turns all replies into `docs/community-reports.md`, ranks fixes, builds one release.
- Open questions (only from community data or a planned capture): 0605 on other firmware and models; guard on aggressive battery-saver phones; meaning of `1405`, `1800`, `1407` (read-only, never probe).

## Map
- App: `android/gaia-test/` (pure Java, no libs, minSdk 26, targetSdk 35). `MainActivity` (UI + link; pages Home/Equaliser/Listening/Device/Auto power-off), `EqFaders` (vertical faders), `AutoOffService` (guard), `Gaia` (framing, decode, blocklist), `Remote` + `Widgets` + `*Tile` (one-shot sends), `Presets`, `EqSync`, `Watchdog`, `Tok` + `res/values(-night)/colors.xml` (all colours). JVM tests in `app/src/test`.
- PC client: `windows-client/gaia_client.py` (snapshot / diff / check; `--addr` or `M4_ADDR`). Tools in `tools/`.
- Specs: `docs/command-map.md` (every command), `docs/protocol.md`, `docs/config-spec.md`, `docs/design-spec-v3.md`, `docs/design-spec-v4.md` (current design, built in v0.14.0).
- Releases: bump `versionCode`/`versionName` in `app/build.gradle`, copy the APK to `android/gaia-test/release/` + `SHA256SUMS`, merge, then run `release.yml` on main (tests first; tag must equal v<versionName>). Signing key is public on purpose (`keystore/`).

## How to work (token budget)
- One task per session. Read only the files the task names; don't load `docs/history.md` or big specs unless needed.
- Before any release: sorted diff of `send(...)`/`toggle(...)` in MainActivity vs main; must be identical unless the change is intended.
- PR CI (`build.yml`) runs the unit tests; local JUnit downloads may fail in the cloud sandbox, so rely on CI.
- Laptop (hardware) sessions: one per release, combine minor versions, follow the checklist, add one line per test to the Test log below. Each entry: fw, app and version, cups, case, result.
- Reviews: diff only (code-review skill), not the whole repo. No max-effort advisor runs for docs.

## Test log (new entries only; T1-T42 are in docs/history.md)
- 1 Nov 2026, laptop run of v0.14.0 (covers 0.12.0-0.14.0; checklist docs/local-test-v0.12.0.md mapped to the design v4 labels). Laptop Windows 10, Python 3.12.10. Phone Galaxy Z Flip6 SM-F741B, now Android 17 (SDK 37), Nova Launcher. All tests: fw 3.38.3, cups up, no case. App = v0.14.0 versionCode 22, GitHub release build. Raw logs and screenshots in %USERPROFILE%\m4-private\v014-20261101. PC checks: `gaia_client.py` @9682d54.
- T43, 22:40 (setup): repo APK `e15664e1...` = `SHA256SUMS`, GitHub release asset `40dfd3f8...`: FAIL, they differ (`release.yml` rebuilds in CI; same cert `285fe924...4a78`; 0.12.0, 0.12.1, 0.13.0 differ the same way). Phone already had the release build (installed 21:36), versionCode 22: no reinstall. SCP force-stopped. Start snapshot: battery 30 %, Custom level 0, anti-wind off, Hi-Res on, touch off, EQ -5.0/0/2.6/2.7/1.7, bass on, timer 900 s. Guard was armed at 40 min (Ryane's setting), stopped for the run.
- T44 (step 1): dark and light screenshots of Home, Equaliser, preset sheet, save dialog, Listening, Device, Auto power-off, widgets: true black in dark, nothing under the status or gesture bar, save dialog copper (T37 note fixed): PASS. Visual notes: curve-strip dots under the curve look the same as the others (no visible lit dots) in both themes; the disabled Speech clarity switch is almost invisible in dark; the settings icon reads as a sun.
- T45 (step 2): Home shows dot battery 30%, "Connected · fw 3.38.3", tiles "Custom 1" / "3 of 7 on" / "Armed · 40 min" / "M4AEBT SE · hw 4": PASS.
- T46 (step 3): Disconnect: "Not connected", "Connect to change settings.", sub-pages show "Not connected. Values are from the headphones once you connect.", every switch and slider disabled, Equaliser/Listening tiles dimmed: PASS. Notes: noise labels stay full brightness; transparency value shows "1" offline vs "full ANC" online; battery last column stays copper offline; EQ ⋮ menu disabled offline.
- T47 (step 4): Adaptive `1A04 01` + `1A00 03 01`, Off `1A00 03 00` + `1A04 00`, Custom `1A00 03 00` + `1A04 01` + `1A02 [level]`, anti-wind Auto `1A00 01 02`, Max `01 01`, Off `01 00`, slider 50 % `1A02 32` + `1A03` readback: PASS.
- T48 (step 5): one-fader drag `1001 [01 1E]` (+3.0), pill -> "Custom curve"; diagonal sweep sends five `1001` in band order on release; double-tap `1001 [01 00]`; Flat five zeros; sheet Rock = Presets.java; save "TestA" (first empty slot pre-selected), rename, delete; bass `1008` 00/01; Home tile shows "Rock": PASS.
- T49 (step 6): smart pause `080C`, low latency `0817`, auto-answer `080A`, comfort `0814`, on-head `0400`, touch `1606` (inverted), Hi-Res `0405` + `0406`, call slider `0805 02` + `0806` ("2 / 4"); tile 3 -> 2 -> 3 of 7: PASS. Hi-Res off unlocks Speech clarity (`0803 [00 02]` / `[00 01]` accepted), hint shown while Hi-Res is on: PASS.
- T50 (step 7): BUG: the device picker row is blank (light and dark, also after Rescan); the dropdown lists all paired devices with MOMENTUM 4 checked, but the closed Spinner has no selected-item view. Rescan, Refresh (reads only), Info values, Guard log, Advanced (timer Read `0601 00` -> 900 s), Log: PASS.
- T51 (step 9): Ryane rotated the phone on the Equaliser page: page and link kept: PASS. Dark-mode switch: activity recreated on Home, link dropped ("Disconnected."), as documented: PASS. Back arrow and system Back return to Home; Back on Home leaves the app (the first press right after the page change was ignored once): PASS. Fold not tested. Rotation and theme via adb were blocked (system settings), done by Ryane.
- T52 (step 10): app process killed: noise widget Adaptive / Off / Custom and EQ widget next (Pop `00 E7 00 19 00`) / prev (Rock) send the documented frames via M4REMOTE, names and highlight follow: PASS.
- T53 (steps 11 + 8): 2-min guard started 23:08:34 with the M4 switched off by Ryane. 23:10:34 round 1 (3 attempts, each "read failed, socket might closed or timeout" after ~6-9 s) -> "Next try in 1 min"; 23:12:02 round 2 -> "Next try in 5 min"; notification text matches: PASS. No "connect timeout after 8000 ms" line: the socket failed on its own before the 8 s deadline. 23:13:46 silent WAV in Samsung Music -> `music=true`, idle reset; Ryane switched the M4 on; 23:16:01 `0605` on attempt 1, "power-off sent OK", guard stopped, PC connect timed out (off): PASS, fired on the normal timeout before the pending 5-min retry (failures reset). Session's only `0605`. BUG: after the guard stopped itself the Auto power-off page still said "Armed" + STOP GUARD and the Home tile "Armed · 2 min" with the switch on (service gone), no "Sent"; reopening the app re-armed the guard via autostart while the M4 was off.
- T54 (step 12): Copy report: app 0.14.0, phone samsung SM-F741B Android 17 (SDK 37), MOMENTUM 4 connected, model/fw/hw/battery, guard line, both log tails; no Bluetooth address, no other device name: PASS.
- T55 (step 13): Log card Copy (after Guard log): 3402 chars, guard attempts and the `0605` line, no address: PASS (the app log never contains an address, so the XX masking itself was not exercised).
- T56 (step 14): `.github/ISSUE_TEMPLATE/test-report.yml` ("Test report") is on main; the chooser page needs a GitHub sign-in, not seen rendered: not verified.
- T57 (end, 23:22): Ryane switched the M4 on; PC `check v014_restore.json` PASS; snapshot diff start vs end empty (1407 = 00 both). `tools/blocklist_parity_test.py` OK (1277/1277). App left at 15 min (was 40 min before the run), autostart on, guard running (23:23:06). Note: with autostart on, opening the app arms a guard with the last idle time at once.
