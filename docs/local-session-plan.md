# Local session plan: Windows laptop + phone + MOMENTUM 4 (from 25 Oct 2026)

For a NEW Claude Code session on Ryane's Windows 10 laptop. It does the work that needs real hardware: PC Bluetooth, the phone over USB, and the headphones. The cloud session cannot do any of this.

Inputs this plan builds on: `CLAUDE.md` (T1-T14), `docs/config-spec.md` (Capture 2), `docs/re-method-review.md` (decoder + snapshot/diff), `docs/prior-art.md` (omarchy conflicts, danger list), `docs/todo-cloud.md`, `windows-client/gaia_client.py`, `tools/btsnoop_parse.py`, `android/gaia-test` v0.8.

## 0. Decisions in this plan

1. **The PC does the protocol testing, the phone checks what it means.** Every v0.8 command is first checked from the PC with `gaia_client.py` (SET, then GET readback, then the notification). Smart Control Plus (SCP) is then used only as a "label oracle": the PC writes a value, then uiautomator reads what SCP shows for it. This settles anti-wind 1 vs 2 without any capture. HCI captures are only for writes we have never seen on the wire.
2. **New tools, not a bigger `btsnoop_parse.py`.** Keep `btsnoop_parse.py` as the small, stable low-level reader. Add `tools/gaia_annotate.py`, which imports `btsnoop_parse.run()` and puts the GAIA layer on top: it finds the channel by content, drops reconnect re-reads, labels commands, aligns markers and redacts. All labels come from one table, `tools/m4_commands.py`, which `gaia_client.py` also uses for its documented-only allowlist.
3. **Only one power-off, at the very end.** The only `0605` in the session is the one the idle guard fires during the final regression test. That same power cycle is the headset restart that Hi-Res and the `1407` pending flag need. Then one button press in the final human checklist.

Also decided:
- Raw data lives **outside the repo**, in `%USERPROFILE%\m4-private\`. That covers bugreports, btsnoop, dumpsys output, SCP screenshots and dumps, and unredacted logs. Then a stray `git add -A` can't leak it.
- All adb I/O goes through Python helpers (`subprocess`, bytes). This avoids the two Windows traps: PowerShell `>` corrupts binary screencaps, and Git Bash rewrites `/sdcard/...` paths.
- Phone-clock markers (`adb shell log -t M4MARK` plus `adb shell date`) line frames up with actions. btsnoop and logcat use the same clock, so no clock offset needs guessing.

## 1. Hard constraints (the session must obey these, and they go in its CLAUDE.md section)

- **Only documented commands.** Documented = listed in `docs/config-spec.md`, `docs/protocol.md` or the omarchy table in `docs/prior-art.md`, with the payload form given there. No range sweeps, no probing of unknown IDs, no "try the neighbour". Explicitly never: `0606`-`06FF`, Sennheiser feature 1 (`02xx`/`03xx`, DFU), `0040` (factory reset), `1405`/`1406`, `1403`, Qualcomm (`001D`) feature 6, `1500`. Don't run `gaia_client.py sweep` at all.
- **Keep both blocklists** (`Gaia.blockedReason` in the app, `blocked_reason` in `gaia_client.py`) as they are, or stricter. Add a unit test that proves they agree.
- **`0605` is the only power-off.** After it, only the button brings the headset back. In this session it is sent once, by the app's guard, in Phase 9. Never from the PC.
- **No raw data in git.** Raw captures, bugreports, dumpsys output, uiautomator dumps and screenshots of SCP contain the MAC, the serial and device names. Commit only redacted, decoded summaries. Before every commit, run the leak check in Phase 11.
- **Every test entry records:** firmware version, the app and its version (our app vX, SCP vX, or `gaia_client.py` @ git short hash), cup orientation, case or not, and the result. Also phone model / One UI.
- Don't modify, repackage or hook SCP. Only drive its UI and read the screen.
- Restore every setting you change. A snapshot diff at the end of each phase must be empty (except volatile fields: battery, charging, codec, sample rate).

## 2. Pre-flight for the cloud session (optional, about 15 min, before kickoff)

**DONE 24 Oct 2026: v0.8.1 ships the logcat tags below** (`adb logcat -s M4GAIA M4GUARD`). Install `android/gaia-test/release/m4-gaia-test-0.8.1.apk`; the Phase 7 fallback is no longer needed.


The app has **no `android.util.Log` calls**: `MainActivity.append()` and `AutoOffService.appendLog()` only write to in-memory text. So `adb logcat` shows nothing for `be.ryane.gaiatest` today.
- Recommended: the cloud ships **v0.8.1**, which adds `Log.i("M4GAIA", line)` in `MainActivity.append` and `Log.i("M4GUARD", line)` in `AutoOffService.appendLog`. No behaviour change. Put the APK in `android/gaia-test/release/`.
- If that isn't done, the local session uses the fallback in Phase 7: expand the LOG card, tap "Show guard log", then read the log TextView's `text` from a uiautomator dump. It works with v0.8 as shipped, but it's slower.

## 3. Phases

Who: **A** = agent, **H** = human (only where it can't be avoided). Commands are for PowerShell unless marked *(bash)*. Inside Claude Code on Windows the Bash tool is Git Bash, so the helpers are Python and work the same from either shell.

### Phase 1: Toolchain (A, H only for a UAC prompt if winget asks) - 15 min
Goal: adb, Python and the repo work from a user folder.

```powershell
Set-Location $env:USERPROFILE                       # NEVER work from C:\Windows\System32
New-Item -ItemType Directory -Force src, m4-private | Out-Null
winget install --id Google.PlatformTools -e         # adb; per-user, no admin
winget install --id WiresharkFoundation.Wireshark -e  # optional, for androiddump
# open a NEW terminal so PATH updates, then:
adb version
python --version                                    # expect 3.12.x; use "python", not "py", so permission rules match
git clone https://github.com/<owner>/momentum4-auto-off $env:USERPROFILE\src\momentum4-auto-off
Set-Location $env:USERPROFILE\src\momentum4-auto-off
```
If winget has no PlatformTools: download `platform-tools-latest-windows.zip`, unzip it to `%USERPROFILE%\tools\platform-tools`, and add that folder to the user PATH.

Fixes for the UAC/system32 trap:
- Never start Claude Code from an elevated ("Run as administrator") terminal. An elevated shell starts in `C:\Windows\System32`, relative `logs/` lands there, and writes fail or get virtualized.
- Start Claude Code with `cd $env:USERPROFILE\src\momentum4-auto-off; claude`.
- Always pass absolute paths to the tools: `--log $env:USERPROFILE\m4-private\logs`.
- First command of every session: `pwd`. It must be under `C:\Users\`.
- Git Bash path mangling: set `MSYS_NO_PATHCONV=1` (done in settings below), or `adb pull /sdcard/x` turns into `C:/Program Files/Git/sdcard/x`.

Pass: `adb version` prints a version, `python --version` is 3.12, `pwd` is under `C:\Users\`.
Androiddump check: `Test-Path "C:\Program Files\Wireshark\extcap\androiddump.exe"`. Missing means the extcap component wasn't selected: rerun the installer interactively (H, optional).

### Phase 2: Preconditions, one human checklist (H once, then A)
H: the **start checklist** (section 6). After that the agent checks everything itself:

| check | command | pass |
|---|---|---|
| phone authorized | `adb devices` | `<serial>	device` (not `unauthorized`/`offline`) |
| keep screen on while on USB | `adb shell svc power stayon usb` | no output |
| phone info | `adb shell getprop ro.product.model`, `ro.build.version.release`, `ro.build.version.oneui` | recorded |
| snoop log mode | `adb shell getprop persist.bluetooth.btsnooplogmode` | `full` (else H: Developer options > HCI snoop log > Enabled, then BT off/on) |
| SCP version | `adb shell dumpsys package com.sonova.chb.control` then find `versionName` | recorded (closes "app version not yet recorded") |
| our app version | `adb shell dumpsys package be.ryane.gaiatest` then `versionName` | recorded, or "not installed" |
| free the GAIA channel | `adb shell am force-stop com.sonova.chb.control` and `adb shell am force-stop be.ryane.gaiatest` | no output |
| M4 connected to phone | `adb shell dumpsys bluetooth_manager` then the A2DP/connected section (save to m4-private, redact before quoting) | M4 listed as connected |
| PC GAIA link | `python windows-client\gaia_client.py verify --channel 15 --log $env:USERPROFILE\m4-private\logs` | `0495:0701 resp [00 xx xx] => auto power-off = N s` |
| firmware | `gaia_client.py snapshot` (Phase 3) then `1301` | `3.38.3`; anything else: stop and report, the command table is per firmware |

### Phase 3: Build the tools (A, offline, no hardware) - 3 to 4 h
Goal: one-command decode, a documented-only test runner, UI automation. Commit each tool with unit tests.

1. **`tools/m4_commands.py`**: a table of `cmd -> {name, kind: get|set|notif|event, feature, payload: (len/range/enum), get_for, notif_for, source: capture2|capture1|omarchy|oldapp, status: confirmed|claimed}`, taken from config-spec.md and prior-art.md. Response = cmd + 0x0100, notif = GET id + 0x0080, error = cmd + 0x0180 with payload 05 (invalid) or 83 (out of range, omarchy).
2. **`windows-client/gaia_client.py`**: add three subcommands. Don't change existing behaviour.
   - `snapshot --out FILE.json`: sends every documented GET once (0601 [00], 0602, 0603, 0401, 0402, 0406, 0802, 0804, 0806, 0807, 080B, 080D, 0815, 0818, 1002 [0..4], 1003, 1009, 1A01, 1A03, 1A05, 1607, 1407, 1200, 1201, 1206). Stores the decoded values. Never 1401 here (it returns device names).
   - `diff A.json B.json`: prints the changed fields and ignores the volatile ones.
   - `check FILE.json`: runs a list of steps `{set: frame, expect_resp, expect_notif?, get, expect_value, restore}`. **It refuses any frame that isn't in `m4_commands` with a payload inside the documented range.** This is the documented-only guard, on top of the blocklist. First it registers notifications only for the features seen in capture 2: `0007 [0D]`, `[08]`, `[04]`.
   - Change the default `--log` to `%USERPROFILE%\m4-private\logs` when `cwd` is under `C:\Windows`.
3. **`tools/gaia_annotate.py`** (decoder, per re-method-review section 3):
   - Takes one or more btsnoop files (`.log.last` + `.log`), merges them by time, and prints the first and last record times.
   - Finds the GAIA channel **by content**: any RFCOMM DLCI whose payloads start with `FF 03` and carry vendor `04 95` or `00 1D`. Never by a fixed DLCI (30 vs 3).
   - Splits the session into connect blocks: each block starts at a SABM/UA on the GAIA DLCI, or at the first GAIA frame after an ACL connect. In each block it drops the connect burst: known GETs, `0007` registrations, and their responses, until 2 s of quiet or the next marker. **It always keeps SETs and unknown commands.**
   - Labels every frame from `m4_commands`. Unknown IDs are shown as `UNKNOWN` (that's a finding, not something to send).
   - Pairs each SET with its response and notification in the same burst.
   - Aligns markers from `markers.jsonl` (phone epoch) and prints `--- MARK antiwind_max (t=...) ---` lines between frames.
   - `--redact` maps MACs to `<M4>`, `<PHONE>`, `<PC>`, `<DEVn>`, drops remote names and `1401` payloads, and replaces ASCII payloads of 8+ printable characters (except the model `1306`) with `<ascii:n>`.
   - Output: `decoded.md` (redacted, can be committed) and `decoded_full.txt` (private).
   - Tests: a synthetic btsnoop fixture generator (no real data) that covers a DLCI change, a reconnect burst and fragmentation. If the private Capture 2 file exists on the laptop, also check that the decoder reproduces the Capture 2 table in config-spec.md.
4. **`tools/adbui.py`** (UI driver): `dump` (`adb exec-out uiautomator dump /dev/tty`, parsed into nodes), `find --text/--desc/--class REGEX`, `tap NODE` (center of bounds), `wait --text REGEX --timeout`, `swipe-seek NODE FRACTION` (for sliders), `shot OUT.png` (`adb exec-out screencap -p`, bytes written by Python), `launch PKG` (`adb shell monkey -p PKG -c android.intent.category.LAUNCHER 1`), `stop PKG`.
5. **`tools/mark.py LABEL`**: runs `adb shell "log -t M4MARK LABEL; date +%s.%N"` and appends `{label, phone_epoch, pc_epoch}` to `m4-private/<session>/markers.jsonl`. The `pc_epoch` gives the offset for lining up PC-client logs.
6. **`tools/blocklist_parity_test.py`**: regex-extracts the rules from `Gaia.java` and `gaia_client.py`, then asserts that both block 0040, feature 1, 1405, 1406, 0607, 0609-06FF and Qualcomm feature 6.

Pass: `python -m unittest discover -s tools` and the gaia_client tests are green, and `gaia_annotate.py --selftest` decodes the fixture.

### Phase 4: v0.8 command check from the PC (A only) - 1 h
Goal: every v0.8 control confirmed at protocol level: SET, resp, notif, GET readback, restore.
Setup: SCP and our app force-stopped, headset on the desk, cups up, phone holding A2DP is fine (T10). For each test: firmware, `gaia_client.py` git hash, cups up, no case.

```powershell
$P="$env:USERPROFILE\m4-private\s1"; New-Item -ItemType Directory -Force $P | Out-Null
python windows-client\gaia_client.py snapshot --out $P\snap_before.json --log $P
python windows-client\gaia_client.py check windows-client\checks\v08.json --log $P
python windows-client\gaia_client.py snapshot --out $P\snap_after.json --log $P
python windows-client\gaia_client.py diff $P\snap_before.json $P\snap_after.json   # must be empty
```

`windows-client/checks/v08.json` (committed, no private data) holds these steps. Every SET is followed by its GET, and then the original value is restored:

| # | control | SET frames | expect resp / notif | GET -> expect |
|---|---|---|---|---|
| 1 | noise Off | `1A00 [03 00]`, `1A04 [00]` | `1B00`,`1B04` / `1A81`,`1A85` | `1A05`->`00`, `1A01` adaptive=0 |
| 2 | Adaptive | `1A04 [01]`, `1A00 [03 01]` | same | `1A05`->`01`, `1A01` adaptive=1 |
| 3 | Custom + level | `1A00 [03 00]`, `1A04 [01]`, `1A02 [00]`/`[32]`/`[64]` | `1B02` / `1A83` | `1A03`->`00`/`32`/`64` |
| 4 | anti-wind, 2-byte | in Custom: `1A00 [01 00]`,`[01 01]`,`[01 02]` | `1B00` / `1A81` | `1A01` anti-wind=0/1/2 |
| 5 | **1A00, 6-byte** (omarchy form) | first a no-op: `1A00 [01 a 02 t 03 d]` with the current a/t/d from `1A01`, then the same with a changed | `1B00` or error `1B80 [05]`/`[83]` | `1A01` |
| 6 | EQ band | `1001 [00 14]` then restore | `1101` / `1082` | `1002 [00]`->`14` |
| 7 | EQ all-gains read | none | none | `1003` returns 5 gains equal to `1002 [0..4]` (claimed, omarchy) |
| 8 | bass boost | `1008 [01]`/`[00]` | `1108` | `1009` |
| 9 | speech clarity | `0803 [00 02]`, restore the original (`00 01`; if the original is `00 00` and the restore gives error 05, use `00 01` and log it) | `0903` | `0804` |
| 10 | on-head detection | `0400 [x]` toggle | `0500` | `0401` |
| 11 | Hi-Res | `0405 [00]`/`[01]` toggle, restore | `0505` | `0406`; also read `1407` before/after (pending flag) |
| 12 | call transparency | `0805 [00]`,`[04]`, restore (default 2) | `0905` / `0886` | `0806` |
| 13 | smart pause / auto-answer / low latency | `080C`,`080A`,`0817` toggles | `090C`,`090A`,`0917` | `080D`,`080B`,`0818` |
| 14 | **comfort call** | `0814 [1-v]`, restore | `0914` / `0895` | `0815` |
| 15 | **touch controls** (inverted: 0 on) | `1606 [01]`, restore `[00]` | `1706` | `1607`->`01` |

Results:
- Test 5: accepted means both forms work, so record it and keep the 2-byte form (that's what the official app sends). Error 05/83 means only the 2-byte form works on 3.38.3, and omarchy's note applies to other firmware. Either way, close the conflict in prior-art.md.
- A readback **can't** tell Auto from Max. Phase 5 does that.
- **Hi-Res restart-pending check (`1407`), its own pass/fail.** Smart Control Plus only advises a restart after Hi-Res; we want to know if the headset itself says one is needed. `1407` is a GET only (omarchy: "audio mode change pending"), never write it.
  1. Read `1407` and `0406`. Record both (baseline, expect pending = 0).
  2. Toggle Hi-Res once with `0405` to the opposite value. Do NOT restore it yet.
  3. Read `1407` right away, then again after 10 s. PASS: it changes to a pending value (e.g. 01). If it stays 0, record "1407 does not track Hi-Res on 3.38.3".
  4. Toggle Hi-Res back to the baseline and read `1407` again: does pending clear without a restart? Record it.
  5. Toggle Hi-Res to the opposite value a second time and leave it there. The single power cycle in Phase 9 to 10 is its restart.
  6. After the restart (Phase 10): read `1407` (expect 0 again) and `0406` (expect the new value kept). Then restore Hi-Res to the baseline. That restore makes pending = 1 again; leave it, and note it in the handback (Ryane's next normal power cycle clears it).
  7. Record in docs/config-spec.md: the `1407` values seen, and whether it is a reliable "restart needed" signal. If yes, the cloud adds a "Restart needed" hint next to the Hi-Res switch in the app.
- A notification is "not expected" for features we don't register (feature 2 `04xx`, feature 3 `06xx`, feature 11 `16xx`). Don't register extra features: that would be a new, undocumented use of `0007`.

Pass: every step gets its resp (no error), the GET readback matches, every captured-feature SET has its notif, and the before/after diff is empty. Any FAIL is logged with the exact frames. Don't retry it with other payloads.

### Phase 5: Smart Control Plus as a label oracle (A only) - 45 min
Goal: find out what values *mean* (anti-wind 1/2, comfort call, touch) without a capture. This also builds the SCP selector map.

Loop per value:
1. PC: `check` a one-step file that sets the value, e.g. `1A00 [03 00]`, `1A04 [01]`, `1A00 [01 01]`. The PC client then closes the link.
2. `python tools\adbui.py launch com.sonova.chb.control`, then wait for the main screen.
3. `adbui.py dump` (save to private), navigate to the noise-control or settings screen (selectors from the first exploratory dump), `adbui.py shot`.
4. Read the selected anti-wind option. Use the node's `checked`/`selected` attribute or its `content-desc` first (SCP is Flutter and exposes semantics as `content-desc`; the UI is French: "Anti-vent", "Auto", "Max", "Adaptatif", "Personnalisé", "ARRÊT"). If the dump is empty or ambiguous, Read the screenshot PNG and judge it visually.
5. `adbui.py stop com.sonova.chb.control`. Repeat with `[01 02]`, then restore.
Then the same for comfort call (`0814 [0]`/`[1]`) and touch controls (`1606 [0]`/`[1]`).

First run: commit the selector map, without personal text, as `tools/scp_selectors.json` (French labels, screen path).
Watch out: opening SCP may make it write its own state, or re-enable features (y8723m). Take a PC snapshot before and after each SCP visit, and diff them. If SCP changes values when it opens, that's a finding: log it.
Pass: the anti-wind byte-to-label mapping is unambiguous for both 1 and 2, and comfort call and touch labels match the readback.

### Phase 6: Short marked capture of SCP writes we have never seen (A only) - 45 min
Goal: get the actual wire bytes for writes that are claimed but never captured. Keep it to these: comfort-call toggle, touch-controls toggle, anti-wind Off->Auto->Max->Off, auto power-off "Never" then back to 15 min. **Don't touch:** voice-prompt language (runs through the upgrade feature), audio-mode priority / codec switches (headset restart, later session), "disconnect device", factory reset.

1. Optional, 10 min timebox: try live streaming. In Wireshark, open the "Android Bluetooth Btsnoop" interface (androiddump). If no interface shows up or it captures nothing, drop it and use the bugreport.
2. Rotate the snoop file so the capture starts fresh: `adb shell cmd bluetooth_manager disable`, wait 3 s, `adb shell cmd bluetooth_manager enable`, then wait for the M4 to reconnect (dumpsys).
3. `tools\mark.py session_start`, then for each action: `mark.py <action>_begin`, the adbui taps, `mark.py <action>_end`, then 3 s idle.
4. `adbui.py stop com.sonova.chb.control`, then `mark.py session_end`.
5. `adb bugreport $env:USERPROFILE\m4-private\s2\bugreport.zip` (2 to 4 min). Unzip only the entries whose names contain `btsnoop` (Android: `FS/data/misc/bluetooth/logs/`, Samsung may also have `FS/data/log/bt/`).
6. `python tools\gaia_annotate.py <files> --markers ...\markers.jsonl --redact --out ...\s2`.

Pass: the first btsnoop record is before `session_start`, and every `_begin`/`_end` pair has at least one GAIA SET between them, or an explicit "no write" (also a finding). If the ring buffer wrapped (first record after `session_start`), repeat the capture in two halves, each pulled straight after.
Then: add a "Capture 3" section to config-spec.md, with redacted frames only.

### Phase 7: Our app v0.8 on the phone (A only) - 1 h
Goal: show that the v0.8 UI sends the same frames that Phase 4 confirmed, and that the UI reads back what the headset holds. v0.8 has never run against the headset before this.

```powershell
adb install -r android\gaia-test\release\m4-gaia-test-0.8.apk      # or 0.8.1 if the cloud shipped it
adb shell am force-stop com.sonova.chb.control
python tools\adbui.py launch be.ryane.gaiatest
```
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (different debug key between CI and local builds): `adb uninstall be.ryane.gaiatest` (ask rule), then install again. That loses the saved prefs (selected device, autostart). Record it.
- Grant at runtime if a prompt shows: tap "Allow" through adbui, or `adb shell pm grant be.ryane.gaiatest android.permission.BLUETOOTH_CONNECT`.
- Drive it: pick MOMENTUM in the device spinner, tap "Connect", wait for the "Connected" pill. Then exercise each control (Adaptive / Custom / Off, slider by `swipe-seek`, anti-wind Off/Auto/Max, EQ band, Flat, Bass boost, Speech clarity, the LISTENING switches, the call-transparency slider). After each action, check that the expected TX hex appears in the app log. With v0.8.1, use `adb logcat -d -v epoch -s M4GAIA:I`. With v0.8, expand LOG and read the TextView `text` from the dump.
- Seeding check: force-stop the app, have the PC set 2 or 3 values (e.g. bass on, anti-wind 1, EQ band 0 +2 dB), relaunch the app and connect, and check that the dump shows those switch and segment states.
- Cross-check: force-stop the app, run a PC `snapshot`, and compare it with what the app last set. Then restore everything from the PC to `snap_before.json`.
- v0.7 detection regression (the Galaxy Tab bug): with the app open, BT off/on via `cmd bluetooth_manager`. The device list must repopulate with no tap. Then "Rescan" works.
Pass: every UI action produces exactly the documented frames and no error responses, the seeding matches, and the list re-scans. Record each as T-entries, app v0.8 (or 0.8.1).

### Phase 8: Is 1405 a disconnect or a pairing delete? (A, H only if the PC pairing is lost) - 20 min
Rule: **we never send 1405.** Only SCP sends it, through its own UI, and we compare state before and after.
Target: the **PC** entry in SCP's connected-devices list. It's the cheapest one to re-pair, and SCP likely can't disconnect the phone it runs on.
1. Before: `adb shell dumpsys bluetooth_manager` goes to the private folder, and you extract the bonded-devices list (MACs redacted to roles). Also record the Windows-side pairing: `Get-PnpDevice -Class Bluetooth | Where-Object FriendlyName -like '*MOMENTUM*'`. Optionally read the headset-side list from the PC with documented `1401 [idx]` (names stay private).
2. Start a capture marker, then in SCP open the devices list and tap disconnect on the PC entry (via adbui), then end the marker.
3. After: the same dumpsys and Get-PnpDevice. Then `gaia_client.py verify` from the PC: reconnecting **without a pairing prompt** means a disconnect. If it fails with an auth error, or Windows shows the device as needing pairing, it's a delete.
4. Decode the capture window to confirm the frame was `1405 [index]`, and which index.
Answer rule:
- Phone bonded list unchanged, PC reconnects without re-pairing = **disconnect**.
- PC can't reconnect without pairing = **pairing delete**.
- Either way, 1405 stays blocked in both clients. Write the answer in config-spec.md. Note in the entry that dumpsys sees only the phone's bond list, which is why the PC reconnect test is the deciding check.
H, only if it was a delete: re-pair the M4 in Windows Settings > Bluetooth. Put that in the end checklist, not mid-session, unless later phases need the PC link. Phase 9 does not need it.

### Phase 9: Auto power-off guard regression on the phone (A, ends with the one 0605) - 25 min
Goal: v0.8 did not break the T12 behaviour.
Setup: PC client disconnected (the guard connects on its own when it fires), SCP force-stopped, headset on the desk, cups up, no case. Record whether the app is battery-unrestricted: `adb shell dumpsys deviceidle whitelist` and `adb shell cmd appops get be.ryane.gaiatest RUN_ANY_IN_BACKGROUND`. Record these, don't change them.
1. In the app via adbui: Idle timeout = 2, then "START GUARD". The notification and the "STOP GUARD" label must show.
2. **G1, no fire during audio.** Push a 5-min silent WAV that Python generates (`m4_silence.wav`, no personal data) to `/sdcard/Music/`. Play it: try `adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Music/m4_silence.wav -t audio/wav` first, then a MediaStore `content://` URI. Fallback: H starts any playback, folded into the start checklist. Check it's playing: `adb shell dumpsys media_session` (state=3) or `dumpsys audio` (an active player). Wait 4 min. Pass: the guard log shows the idle clock reset or audio active, and the M4 is still connected in `dumpsys bluetooth_manager`. Don't use the PC GAIA link here, it would collide with the guard.
3. **G2, fire with the screen off.** Stop playback (`adb shell cmd media_session dispatch pause`, or force-stop the player), then `adb shell input keyevent KEYCODE_SLEEP`, and wait timeout + 90 s.
   Pass:
   - The guard log shows a connect and `0605` sent, with `0705 resp`. Read it via `adb logcat -d -v epoch -s M4GUARD:I` (v0.8.1), or wake the screen and use "Show guard log" + dump.
   - The M4 is gone from the connected list in dumpsys.
   - `gaia_client.py verify` from the PC fails for 60 s.
   - Expected result: headset fully off. This is the session's only `0605`, and it doubles as the Hi-Res restart.
4. Optional G3, Doze (`adb shell dumpsys deviceidle force-idle`): needs a second power cycle. Only run it if Ryane agrees in the end checklist.

### Phase 10: End checklist (H, one batch), then A finishes - 10 min
H: the **end checklist** (section 6): button press, a touch test, listening.
A, afterwards:
- `gaia_client.py verify`, then `snapshot` gives `snap_post_reboot.json`. Finish the Hi-Res restart-pending check (Phase 4 results, steps 6-7): `0406` holds the new value, `1407` back to 0, then restore Hi-Res.
- Restore every value to `snap_before.json` with a `check` restore file. The final diff must be empty.
- Touch test, prepared by the agent: right before the checklist, the PC sets `1606 [01]` (touch off). After Ryane reports, it restores `[00]` and Ryane does the second tap. That's one batch.

### Phase 11: Record and hand back (A only) - 30 min
1. `git checkout -b local/v08-validation-<yyyymmdd>`
2. CLAUDE.md: test entries **T15+**, one per phase result, in the existing style. Each entry gives the firmware, the app and its version, cup orientation, case or not, the result, and the log name. Add the SCP version and the phone model to "Ryane's device".
3. docs/config-spec.md:
   - Mark each row confirmed or failed, with the T number.
   - Resolve anti-wind (1/2 meaning), 1A00 2-byte vs 6-byte, 0814, 1606/1607 and 1405, plus "Capture 3".
   - Update the conflict table in prior-art.md with the outcomes. Changing a doc that records facts is allowed. Rewriting history is not.
4. Commit: the tools, `checks/*.json`, `scp_selectors.json`, and the redacted `decoded.md` files under `captures/` (rename them `*_redacted.md`). **Leak check before every commit** *(bash)*:
   ```bash
   git diff --cached -U0 | grep -nE '([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}' && echo "MAC FOUND: unstage" || echo ok
   git diff --cached --name-only | grep -iE 'btsnoop|bugreport|\.cfa$|\.zip$|\.png$|ui.*\.xml$|dumpsys' && echo "RAW FILE: unstage" || echo ok
   ```
   Note: `gaia_client.py` and its README already contain the headset MAC as `DEFAULT_ADDR`. Don't add more copies. Flag it in the PR for Ryane to decide.
5. `git push -u origin local/v08-validation-<yyyymmdd>`, then `gh pr create` with the handback body below.

**Handback to the cloud session = the PR description**, with:
- a results table (command, PASS/FAIL/n.a., T number)
- the resolved conflicts
- the app bugs found, with redacted log lines and repro steps
- the SCP version and the selector map status
- the decoder status (which captures it decoded)
- any `UNKNOWN` IDs seen in captures (as findings only, never to probe)
- open questions
- suggested next tasks for the cloud: fixing app bugs, the QS tile, and whether to show 1A00 6-byte support.

## 4. Failure handling

| symptom | detect | action |
|---|---|---|
| phone `unauthorized` / `offline` | `adb devices` | `adb kill-server; adb start-server`. Still unauthorized: one H prompt, "unlock the phone and tap Allow (always)". `offline`: H replugs, USB mode File transfer. |
| no device listed | `adb devices` empty | check the cable is a data cable, and Developer options > USB debugging. One H prompt. |
| RFCOMM busy | `verify` times out or connects then gets nothing | force-stop SCP and `be.ryane.gaiatest` (this also stops the guard), wait 5 s, retry once with `--channel 15`. Still failing: check that Windows Settings shows the M4 connected, that no other PC app is open, and the multipoint limit (two hosts): disconnect a third device. |
| headset asleep or off | phone dumpsys shows the M4 not connected, and `verify` times out | before Phase 9 this is unexpected. Log it as a finding (time, last command), then one H prompt: "press the button once". Don't retry commands blindly. |
| headset error on a documented SET | `xx80 [05]`/`[83]` | log it as FAIL, restore, and continue. Never try other payloads to "find" the right one. |
| link closes after a non-0605 command | `link closed by headset` | stop the phase, run `verify` after 10 s. Reconnects: log a "link reset after X" finding and continue. No reconnect for 60 s: stop everything and report (unexpected power-off). |
| btsnoop wrapped | first record after the first marker | rerun that capture in halves, BT off/on before each, bugreport straight after. |
| no btsnoop in bugreport | zip listing | check `btsnooplogmode`. One H prompt: Samsung `*#9900#` > "Copy to sdcard", then `adb pull /sdcard/log/` (ask rule). |
| SCP dump empty (Flutter semantics off) | `dump` has no content-desc | use a screenshot plus visual read. If taps can't be placed reliably, skip Phase 5/6 for that item and write it down. |
| SCP UI changed or logged out | selectors not found | screenshot, stop the SCP phases, report. Don't log in on Ryane's behalf. |
| app install fails | adb message | signature mismatch: uninstall (ask), then install. `INSUFFICIENT_STORAGE`: report. |
| Samsung kills the guard | guard notification gone, no fire | record the MARS/Device Care state, report. Don't change battery settings without asking. |
| file shows up in `C:\Windows\System32` | `pwd` / paths | stop, `Set-Location $env:USERPROFILE\src\momentum4-auto-off`, rerun with absolute `--log`. |

## 5. Session setup for autonomy

**Permission mode:** `acceptEdits` plus the allowlist below. Use `auto` mode if the account has it. Never `bypassPermissions`: this session drives real hardware, and the ask/deny rules are part of the safety net.
**Model:** Opus (the strongest available), effort **high**. Use max effort only for decoder debugging on a real capture.
**Settings file:** `.claude/settings.local.json` (machine-specific, Claude Code keeps it out of git). Paths in the rules are relative to the repo root, so always run from there.

```json
{
  "env": { "MSYS_NO_PATHCONV": "1", "PYTHONUTF8": "1" },
  "permissions": {
    "allow": [
      "Bash(pwd)",
      "Bash(adb version)", "Bash(adb devices:*)", "Bash(adb kill-server)", "Bash(adb start-server)",
      "Bash(adb install -r android/gaia-test/release/:*)",
      "Bash(adb shell getprop:*)", "Bash(adb shell dumpsys:*)", "Bash(adb shell date:*)",
      "Bash(adb shell log -t M4MARK:*)", "Bash(adb logcat -d:*)", "Bash(adb logcat -c)",
      "Bash(adb shell input:*)", "Bash(adb shell uiautomator dump:*)", "Bash(adb exec-out uiautomator dump:*)",
      "Bash(adb exec-out screencap:*)", "Bash(adb shell monkey -p:*)",
      "Bash(adb shell am force-stop:*)", "Bash(adb shell am start:*)",
      "Bash(adb shell svc power stayon:*)", "Bash(adb shell cmd media_session:*)",
      "Bash(adb shell cmd bluetooth_manager enable)", "Bash(adb shell cmd bluetooth_manager disable)",
      "Bash(adb shell cmd appops get:*)", "Bash(adb shell pm grant be.ryane.gaiatest:*)",
      "Bash(adb push:*)", "Bash(adb bugreport:*)",
      "Bash(python windows-client/gaia_client.py verify:*)", "Bash(python windows-client/gaia_client.py battery:*)",
      "Bash(python windows-client/gaia_client.py get-timer:*)", "Bash(python windows-client/gaia_client.py snapshot:*)",
      "Bash(python windows-client/gaia_client.py diff:*)", "Bash(python windows-client/gaia_client.py check:*)",
      "Bash(python tools/:*)", "Bash(python -m unittest:*)", "Bash(python -m pytest:*)",
      "Bash(git status:*)", "Bash(git diff:*)", "Bash(git log:*)", "Bash(git add:*)", "Bash(git commit:*)",
      "Bash(git checkout -b local/:*)", "Bash(git push -u origin local/:*)", "Bash(gh pr create:*)",
      "Bash(powershell -Command Get-PnpDevice:*)"
    ],
    "ask": [
      "Bash(adb uninstall be.ryane.gaiatest)", "Bash(adb pull:*)",
      "Bash(python windows-client/gaia_client.py set-timer:*)",
      "Bash(adb shell dumpsys deviceidle force-idle:*)"
    ],
    "deny": [
      "Bash(python windows-client/gaia_client.py raw:*)", "Bash(python windows-client/gaia_client.py sweep:*)",
      "Bash(adb uninstall com.sonova:*)", "Bash(adb shell pm clear:*)", "Bash(adb shell pm uninstall:*)",
      "Bash(adb shell rm:*)", "Bash(adb reboot:*)", "Bash(adb shell settings put:*)", "Bash(adb root:*)",
      "Bash(adb shell dumpsys deviceidle whitelist +:*)", "Bash(adb shell dumpsys deviceidle whitelist -:*)",
      "Bash(git push --force:*)", "Bash(git push -f:*)", "Bash(git add -f:*)", "Bash(git add --force:*)",
      "Bash(git push origin main:*)", "Bash(git reset --hard:*)"
    ]
  }
}
```
Notes:
- `raw` is denied on purpose. Every protocol write goes through `check`, which only accepts documented frames. If a test really needs `raw`, the session stops and explains why.
- `adb pull` is "ask" so private data only lands where Ryane approves. Bugreports go straight to `m4-private` by path.
- Rule matching is by command prefix: always write `python` and forward-slash paths as shown.

**CLAUDE.md section to add** (local session adds it, in its first commit, under "## Local session (Windows)"):
- Work only from `%USERPROFILE%\src\momentum4-auto-off`. Raw data goes to `%USERPROFILE%\m4-private\`, never into the repo. Absolute `--log`.
- Only documented commands. `gaia_client.py check` only. Blocklists stay. No sweeps. `0605` only via the app guard in the final phase.
- Each test entry: fw, app and version, cups, case, result.
- Group human requests. Only the start and end checklists, plus failure-table prompts.

**Human batching:** two checklists (section 6) and no mid-session prompts except the failure table. The agent checks everything itself (Phase 2), keeps its own clock (markers), frees the GAIA channel (force-stop), and keeps the screen on (`stayon`).

## 6. Human checklists (phone-screen size)

**START (about 3 min)**
1. Laptop: open a normal (not admin) terminal, `cd` to the repo, `claude`.
2. Phone: plug in USB, unlock, tap "Allow USB debugging" (Always).
3. Phone: Developer options > HCI snoop log = Enabled (full).
4. Headphones: switched on, paired with phone AND laptop, on the desk, cups up, not in the case.
5. Smart Control Plus logged in (open it once, then leave it).
6. Leave the phone unlocked and plugged in. Go.

**END (about 5 min, when the agent asks)**
1. Look at the headphones: LED off? (guard power-off) Tell the agent yes/no.
2. Press the power button to switch them on.
3. Put them on. Tap the right cup: nothing should happen (touch disabled). Tell the agent.
4. When the agent says so, tap again: play/pause should work.
5. Optional: play noise or music, switch Adaptive / Custom / Off in our app. Any audible difference?
6. If the agent asks: re-pair the laptop in Windows Bluetooth settings.

## 7. Optional extras, ranked against testing

1. **Testing (Phases 4 to 9) comes first.** v0.8 is untested on the headset, so nothing gets built on top of it until it passes.
2. **Android Quick Settings tile** (ANC / Transparency / Off, plus power off). Users asked for it, and the commands are confirmed after Phase 4. The cloud session should build it; the local session only tests it next time (same install + adbui pattern; tap the tile via `adb shell cmd statusbar expand-settings` and a dump).
3. **Windows tray on `gaia_client.py`** (pystray: battery, ANC cycle, power off). Lowest priority. BTD600 users can't use it (the dongle holds the link), and Ryane's daily use is the phone. Prototype it only if all phases passed and time is left, as `windows-client/tray.py`, a separate commit, using `check`-style documented frames.
Not in this session: Gadgetbridge port, widget, codec/audio-mode-priority tests (headset restarts), voice-prompt language.

## 8. Kickoff prompt (paste into the local Claude Code session)

```
You are the LOCAL hardware session for the MOMENTUM 4 project on Ryane's Windows 10 laptop.
Read, in order: CLAUDE.md, docs/local-session-plan.md (your plan, follow it phase by phase),
docs/config-spec.md, docs/re-method-review.md, docs/prior-art.md, windows-client/gaia_client.py,
tools/btsnoop_parse.py, android/gaia-test/README.md.

Rules (non-negotiable, from section 1 of the plan):
- Only documented commands, only through `gaia_client.py check` (which you build in Phase 3).
  No sweeps, no probing, never 0606-06FF, feature 1, 0040, 1403/1405/1406, Qualcomm feature 6.
- Keep both blocklists. 0605 happens once, via the app's idle guard in Phase 9. Never from the PC.
- Raw captures, bugreports, dumpsys, screenshots and UI dumps go to %USERPROFILE%\m4-private\, never into git.
  Commit only redacted summaries. Run the leak check before every commit.
- Every test entry: firmware, app + version, cup orientation, case or not, result.
- First command: pwd. It must be under C:\Users\. Never work from System32. Absolute --log paths.

Work autonomously. Do Phase 1-2 checks yourself. Ask me for exactly two checklists (start, end,
section 6) plus anything in the failure table that truly needs hands. Group your questions.
If anything behaves unexpectedly (link drop on a non-0605 command, an unexpected power-off,
an unknown command in a capture), stop that phase, log it, and move on or report. Never improvise commands.

Deliver: tools + tests, T15+ entries in CLAUDE.md, config-spec.md/prior-art.md updates,
branch local/v08-validation-<yyyymmdd>, a PR whose description is the handback (plan section 3, Phase 11).
Show me the START checklist now, then begin Phase 1.
```
