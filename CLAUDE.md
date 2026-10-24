# MOMENTUM 4 auto-off project: handoff

Owner: Ryane (Brussels). Device: Sennheiser MOMENTUM 4 Wireless, Android phone.
Goal: fix or work around the broken auto power-off of the MOMENTUM 4, possibly with a small Android tool built with coding agents.

## Communication preferences
- Direct, no preamble, no fluff.
- Correct Ryane's English/Dutch/French phrasing briefly and show a better alternative.
- No em-dashes in texts written for others.

## Documented behaviour (manual Version 08/22 A01, primary source)
- Auto-off: "After taking off the headphones, they automatically switch off after 15 minutes." Adjustable in Smart Control. Red LED 3x = off. Full switch-off, not standby.
- The timer starts only after the on-head sensor detects removal.
- Sensor may NOT detect removal if: ear-cup insides face down on a table, hung over a bag edge, carried inside a bag. Manual advises manual off when storing outside the case.
- Carry case: immediate automatic switch-off.
- Manual off: multi-function button 3 s. Auto-on: lifting and putting on.
- Sources: https://media.graphassets.com/CJdvxcJRTVROai9rHI3r and https://www.richersounds.com/content/product_files/MOMENTUM4Manual.pdf

## How the docs changed
- Later undated FAQ: after 15 min they "switch off almost completely" and "switch on again automatically when you simply touch them".
  https://firmware.s-consumer-cloud.com/help/products/m4/en/faq/M4_FAQ_A01_EN/If_a_problem_occurs-jk6k.htm
- MOMENTUM 5 support page: auto-on removed "due to feedback from MOMENTUM 4 users".
  https://support.sennheiser-hearing.com/hc/en-at/articles/36289764183709-Do-the-headphones-switch-off-automatically-when-not-in-use
- No release note mentions the auto-off timer.

## Firmware / app timeline
- 2.9.12: launch (Aug 2022).
- 2.13.18: May 2023 (Smart Control 4.3+). Multiple Reddit reports of drain while switched off and cased started here (r/sennheiser 16is8pr, Sep 2023).
- Smart Control Plus: announced 5 Apr 2025, rebuilt app; old Smart Control kept in parallel; new features only in Plus.
- 3.37.3: 20 Jan 2026, Plus only (on-head detection, ANC, multipoint toggle, EQ share). Head-Fi reports: powers off while worn, reconnects when taken off, multipoint broken.
- 3.38.3: Ryane's current firmware (22 Oct 2026). The Reddit "3.83.3" (~Aug 2026, failing updates) is very likely this version with digits swapped.
- No source names a specific Android app version where auto-off broke.

## User reports (r/sennheiser, read from OCR'd PDFs)
- Auto-on / reconnect from standby since launch (y8723m); movement or touch wakes it (z54x4g).
- Manual off reportedly undone by movement (y8723m, z54x4g, 154aet4); case off only if the button wasn't used first (154aet4).
- Case off requires cups facing the back of the case (16zc679).
- Dirty/sweaty right-cup sensor breaks detection; cleaning fixes it (12oht43, 16zc679).
- "Auto-off worked half a year ago, then an update broke it" (~2025) and "worked until ~3 months ago" (~mid 2026) (17meao8).
- "Solution found" (18rtq0h, Dec 2023): firmware update not offered in North America; VPN to Germany made it appear. Not an auto-off fix.
- Replies to Ryane's r/sennheiser post (22 Oct 2026): (a) "if the source device is a phone, put it on vibrate; any notification resets the timer"; (b) an owner on old fw 2.x for 4 months, now 3.38.3: same behaviour on both, after 15 min off-head it goes to a rest mode regardless of position, touching it wakes it and it reconnects. => standby-not-off is not new in 3.38.3.
- Replies to the app post (23 Oct 2026): u/razorree: same problems, reconnects to all devices all the time, does NOT turn off in the case (magnet in the right place), moving them a bit (e.g. in a drawer) wakes them. u/TheUnAmericanGenral (M4 + BTD600, Android + Windows) wants a widget for ANC mode / EQ preset, offers to donate.
- u/Ncis16 (23 Oct 2026): app works on Galaxy S26 Ultra, not on Galaxy Tab S10+ ("doesn't detect the headphones"). See docs/todo-cloud.md, Bug reports.
- Sennheiser official Reddit account: hold the button, don't tap. Sennheiser rep @ericpalonen is active in the Head-Fi firmware thread.

## Android-specific findings
- Android keeps media output on MOMENTUM 4, so calls, alarms and notifications keep the headset busy/awake (z54x4g, 2025). Workaround: switch media output to "This phone".
- Smart features disabled in the app get re-enabled on Android (y8723m). App settings change on their own (17meao8).
- App running in background makes the headset reconnect (16is8pr).

## Working hypothesis
1. Timer works only if the sensor detects removal; cups-down, bag, dirty sensor block it.
2. Firmware later redefined auto-off as near-off with wake-on-touch, causing wake-ups and drain.
3. Firmware regressions (2.13.18 and later) also affect manual and case off.
4. Android media-output routing is a separate trigger.
Timer, sensor and power states live in firmware; the app only writes the timer value. Patching the Android app alone is unlikely to fix true power-off.

## Ryane's device (from app, 22 Oct 2026)
- Model M4AEBT SE, hardware version 4, firmware 3.38.3, connection type Bluetooth Classic.
- App: Smart Control Plus confirmed (package com.sonova.chb.control seen in phone logs); app version not yet recorded.
- Phone: Samsung (One UI). Samsung's memory manager (MARS) freezes the app in the background (FRZ/UFZ entries) and Device Care toggles its suspended state. Possibly related to app settings not sticking.
- HCI snoop log enabled 22 Oct 2026 17:37 (full, no filters). First log dump (Samsung /log folder) had an empty log/bluetooth folder: btsnoop not included. Needs adb bugreport from a PC, or *#9900# in Samsung's own Phone app.

## Test log
- T1, 22 Oct 2026: firmware 3.38.3, auto-off 15 min in app, phone Bluetooth off, headphones untouched for 15 min. Turned phone Bluetooth on: headphones connected immediately => NOT switched off. Cup orientation not recorded.
- 22 Oct 2026 15:50: Ryane cleaned the IR wear sensor (inside right ear cup) with a dry cloth. Tests from here on are post-cleaning.
- T2, 22 Oct 2026 ~15:50-17:24: firmware 3.38.3, sensor cleaned, cups facing up (as instructed, not explicitly confirmed), untouched ~1.5 h, phone Bluetooth off. Turned phone Bluetooth on: connected immediately => NOT switched off. Sensor dirt/orientation ruled out as main cause; points to firmware keeping Bluetooth radio active (standby, not off).
- T3, 22 Oct 2026: carry case. Ryane reports the case switch-off works (headphones do not reconnect). Switch-off is immediate on placing in the case, as documented.
- CONCLUSION so far: firmware has a working true-off path (case magnet / Hall sensor, and the button), but the auto-off timer path only goes to standby with the Bluetooth radio active. Target: trigger the true-off path when idle.
- T4b, 22 Oct 2026 18:38-18:41 (Brussels): own test app (android/gaia-test v0.1), fw 3.38.3. Auto-off timer read 900 s. LIMBO timer (timer 1) and timers 2-4: GET and SET refused with error 05 (invalid parameter) => only timer 0 exists on 3.38.3; limbo lead dead via this command. Set timer 0 to 60 s: accepted and read back (values outside the app's 15/30/60 min list work). Took headphones off at 18:41:24: headset sent 0495:0880 [FF] and 0495:089A [00 00 00 00] at once (removal / audio stop), then the GAIA link dropped at 18:41:40 (16 s after removal, before the 60 s timer). Timer is still 60 s on the headset.
- T5, 22 Oct 2026 18:46-18:55: timer 0 = 60 s (confirmed), headphones taken off ~18:46, phone connected (A2DP + test app GAIA link), cups up. Nothing for 9 min: no disconnect, no LED, no power-off. 18:55:49 short audio stream start/stop (0880 [02] + 089A 44100 Hz, then FF/0), probably a phone notification. => With a device connected the auto-off timer does not fire. Hypothesis: timer only runs while no device is connected (usual Qualcomm ADK behaviour); Android keeps the M4 connected, so it never fires in daily use. Correction to T4b: 0880 [FF] / 089A [0] = audio stream stopped, not a removal event.
- T6, 22 Oct 2026 19:30-19:33: timer 0 = 60 s (confirmed 19:30:29), headphones off-head, phone Bluetooth off at 19:30:36 (nothing connected), ~3 min. Bluetooth on: GAIA link reconnected at 19:33:46, within 1 s. Ryane: "doesn't turn off" (no switch-off). => Timer does not switch the M4 off even with no device connected. Combined with T1/T2/T5: on fw 3.38.3 the auto-off path at best reaches a standby that keeps page scan on (matches the later FAQ: "switch off almost completely"). No remote true-off command known. Remaining real-off paths: button 3 s, case magnet.
- T7, 22 Oct 2026 19:43: legacy Qualcomm GAIA "Set Power State: off" (vendor 000A, cmd 0204, payload 00), sent in v1 framing (`FF 01 00 01 00 0A 02 04 00`) and v3 framing (`FF 03 00 01 00 0A 02 04 00`). Both answered `000A:8204 [01]` = ack with status 01 "not supported". No switch-off. Legacy remote power-off lead dead.
- T8, 22 Oct 2026 23:22-23:24 (Brussels): PC GAIA client (windows-client/gaia_client.py on Windows 10, Python 3.12, native AF_BTH transport), fw 3.38.3, Smart Control Plus force-stopped, headphones on and connected to the PC. Baseline: timer 0 = 60 s, battery 50 %. Sweep `--features 3` (0495:0604-067F, empty payload, 1/s). 0604 -> `0495:0704 resp [0E E7 FF FF]` (0x0EE7 = 3815, probably battery voltage in mV; FFFF unknown). 0605 -> `0495:0705 resp []` (OK), audio-stop notifications, then the headset closed the link at 23:22:51 and refused every reconnect for 60 s. Confirmed on the headset: fully off (true off, same as button/case). => `0495:0605` with empty payload = remote power-off. Sweep stopped there; 0606-067F not probed. Log: captures/gaia_sweep_f3_20261022_232249.log.
- T9, 22 Oct 2026 23:27 (Brussels): replication. Headphones switched back on with the button. Sent `FF 03 00 00 04 95 06 05` once: `0495:0705 resp []`, link closed by the headset 0.25 s later; reconnect checks at 23:27:15, 23:27:36 and 23:27:56 all timed out. Confirmed on the headset: fully off again. => Reproducible. Log: captures/gaia_replicate_0605_20261022_232705.log.
- T10, 22 Oct 2026 23:44 (Brussels): same as T9 but with the phone connected to the M4 for audio (Smart Control Plus force-stopped). Sent `FF 03 00 00 04 95 06 05` from the PC: `0495:0705 resp []` at 23:44:34.456, link closed by the headset 0.3 s later; reconnect checks at 23:44:44, 23:45:05 and 23:45:25 all timed out. Confirmed on the headset: fully off. => 0605 works while another device holds the audio link. Log: captures/gaia_phone_connected_0605_20261022_234434.log.
- CONCLUSION: a remote true-off command exists on fw 3.38.3: `0495:0605` (feature 3, cmd 5, no payload). It works with the phone connected. Once off, the radio is off, so it cannot be switched back on remotely: wake with the button. Target now: send it from our own app when the headphones are idle.
- DONE 22 Oct 2026: android/gaia-test v0.2 adds a "POWER OFF NOW" button (sends 0605, with confirm) and an "Auto power-off when idle" foreground service (AutoOffService). Idle = off-head (physical state 0402) AND no audio (0880 notif) for a set timeout; fires 0605 once, fail-safe (unknown head state = in use; if 0402 unsupported and "require off-head" is on, it refuses to fire). Logic unit-tested; NOT yet tested on the headset. APK: android/gaia-test/release/m4-gaia-test-0.2.apk.
- T11, 23 Oct 2026 ~00:05 (Brussels): v0.2 on the phone. Manual POWER OFF NOW (0605) works. Physical state 0402 -> `0502 resp [03]` = ON_HEAD (answered, so 0402 IS supported on 3.38.3). Idle guard never fired in any mode: off-head-required mode cannot fire because 0402 reports on-head even when idle (the known sensor bug), and audio-only mode did not fire either (Android keeps the audio route open, so the headset audio signal never looks idle). => Both headset-side idle signals are defeated by the two root bugs. v0.2 idle guard is a dead end.
- DONE 23 Oct 2026: android/gaia-test v0.3 rebuilds the idle guard to judge idle from the PHONE: idle = AudioManager.isMusicActive() false AND no call (getMode == NORMAL) for the whole timeout, then connect + send 0605. No headset connection held while waiting (only at fire time), so the M4 stays usable until power-off. Removed the off-head/audio-from-headset logic (IdlePolicy deleted). Added a visible guard log ("Show guard log") and connect retries. Manual button unchanged. Logic reasoned, NOT yet tested end to end on the phone. APK: android/gaia-test/release/m4-gaia-test-0.3.apk.
- v0.3 idle guard CONFIRMED working (T12). Main goal reached. Open polish items: (a) default timeout to 15 min; (b) optional "only when disconnected from this phone" or longer grace to avoid powering off while worn in silence; (c) auto-start guard on boot/app launch; (d) battery-drain check of the old standby no longer urgent. Known tradeoff stays: wearing them silent past the timeout powers them off.
- T12, 23 Oct 2026 ~00:28-00:30 (Brussels): android/gaia-test v0.3 idle guard, 2 min timeout, no audio playing. Guard fired and the M4 powered off automatically. CONFIRMED by Ryane: first successful automatic true-off on Android. => MAIN GOAL REACHED: phone-side idle (no audio + no call) -> send 0495:0605 -> real power-off, no PC, no manual action.
- DONE 23 Oct 2026: android/gaia-test v0.4 adds a Config section (ANC, transparency, sidetone, smart pause, auto-answer, low latency, "Read all config" incl. battery/model/firmware/hw). Command spec verified by a max-effort advisor against the capture + old-app map: docs/config-spec.md. M4 ANC = two-layer; ANC and transparency are mutually exclusive, so the app sets both toggles in pairs (1804+1A04); transparency level 1A02 [0-100]. Anti-wind/adaptive sub-modes (combined 1A00 SET) NOT exposed: never captured, unconfirmed. To confirm them, toggle in Smart Control Plus with HCI logging and capture the 1A00 bytes. Frame decoders unit-tested vs capture; config controls NOT yet tested on the headset. APK: android/gaia-test/release/m4-gaia-test-0.4.apk.
- DONE 23 Oct 2026: android/gaia-test v0.5 UI redesign (spec docs/ux-spec.md, max-effort advisor): card layout, status pill, DEVICE readout, 3-way ANC/Transparency/Off segmented control + transparency SLIDER, LISTENING switches, prominent AUTO POWER-OFF card. Polish steps 1+2 done: idle timeout defaults to 15 min; "start automatically when the app opens" option (persisted); remembers the selected device. Step 3 (silence grace) deferred. Pure android.widget, no libs. Compiles; UI NOT yet run on device. APK: android/gaia-test/release/m4-gaia-test-0.5.apk.
- T13, 23 Oct 2026 ~01:38-02:00 (Brussels): v0.5 smoke test on the phone. App launches, connects, DEVICE/config reads work. ANC/Transparency CONFIRMED working at protocol level: "Off" (1A04 00 + 1804 00), "ANC" -> 1A85 01, "Transparency" -> 1885 01; transparency slider 1A02 [0-100] all accepted and read back (1B03). Smart pause/auto-answer/low latency toggles accepted, no echo bounce. Auto power-off guard still fires. Ryane reports no audible ANC difference = perceptual (quiet room), not a protocol fault; retest against steady noise. Two decode BUGS found and fixed in v0.6: firmware GET 1201 returns 3x u16 (00 03 00 26 00 03 = 3.38.3), was decoded as ASCII -> blank; hardware GET 1200 returns 00 04 00 00 = 4 in the u16, was reading byte 0 -> 0. Also 0880 audio-stream notif shares base 0800 with codec, was mislabelled "codec = 255"; now "audio stream stopped/active". Fixes unit-tested vs the actual log bytes. APK: android/gaia-test/release/m4-gaia-test-0.6.apk.
- DONE 23 Oct 2026: android/gaia-test v0.7 fixes the "doesn't detect the headphones" bug (Galaxy Tab S10+ report). The device list now re-scans on resume and when Bluetooth turns on or a device is paired (BroadcastReceiver), plus a "Rescan" button in the header. Clear empty-state messages (no paired devices / no MOMENTUM / permission denied) and a multipoint hint on connect failure. loadDevices preserves the current selection across a re-scan; auto-start moved out of loadDevices to onCreate. Compiles; not yet tested on the tablet. APK: android/gaia-test/release/m4-gaia-test-0.7.apk.
- T14, 23 Oct 2026 22:17-22:47 (Brussels): HCI capture of Smart Control Plus settings (adb bugreport, btsnoop_hci.log.last; raw file kept private). App used RFCOMM DLCI 3 this time. Decoded in docs/config-spec.md "Capture 2": adaptive = 1A04 01 + 1A00 03 01; custom = 1A00 03 00 + 1A04 01 + level 1A02 [0 ANC..100 transparency]; off = 1A00 03 00 + 1A04 00; anti-wind 1A00 [01, 0 off/1 Auto/2 Max]; call transparency 0805 [0-4]; Hi-Res 0405 [00 on/01 off]; EQ bands 1001 [band, dB x10] (63/250/1k/4k/8k), bass boost 1008, Speech Clarity 0803 [00 02/00 01]. Comfort-call write not captured. Language change + its reboot run through the Qualcomm upgrade feature (no standalone reboot command); not implemented.
- DONE 24 Oct 2026: android/gaia-test v0.8 = Smart Control Plus parity from capture 2: noise Adaptive/Custom/Off (1A04 + 1A00 [03 x]), custom ANC<->transparency slider 1A02 and anti-wind Off/Auto/Max (1A00 [01 x]); 5-band EQ (1001, -6..+6 dB, 0.5 steps) + Flat, bass boost 1008, speech clarity 0803; on-head 0400, Hi-Res 0405 (0 = on), call transparency slider 0805 [0-4] replaces sidetone. Registers notifs 0007 [0d/08/04] on connect; UI seeded from 1A05/1A01/1A03/0806/0406/0401/1009/0804/1002. 1804 dropped. Blocklist (app + PC client) adds 0607 and 0609-06FF (resets, per omarchy-momentum4 PROTOCOL.md, see docs/prior-art.md). Decoders checked vs capture bytes; NOT yet tested on the headset. Anti-wind 1/2 may be Max/Auto swapped (conflicting source). APK: android/gaia-test/release/m4-gaia-test-0.8.apk.
- 24 Oct 2026: docs/re-method-review.md (advisor: better RE workflow, build an auto-annotating capture decoder + snapshot/diff mode) and docs/prior-art.md (research: omarchy-momentum4 PROTOCOL.md has a full M4 table incl. touch 1606/1607, comfort call 0814; Gadgetbridge; widget/tray options).
- DONE 24 Oct 2026: docs/local-session-plan.md (advisor): phased plan + kickoff prompt + permission allowlist for a LOCAL Claude Code session on the Windows laptop (adb phone automation, PC checks of v0.8 commands, SCP as label oracle via uiautomator, marked captures, 1405 question, guard regression). v0.8.1 adds logcat tags M4GAIA / M4GUARD for it (no behaviour change).
- To record on every test: firmware version, app + app version, cup orientation, case or not, result.

## Next experiments
- Feature parity with Smart Control Plus (codecs, audio mode priority, hi-res, EQ, adaptive/anti-wind, ...): see docs/todo-cloud.md. Main method is one HCI capture session, not more decompiling.
- Does 0495:0605 work on other firmware versions and on other Sennheiser GAIA v3 models? (community reports)

## Chosen route (22 Oct 2026)
Decompile the official APK + Bluetooth HCI logging, then build our own small Android app that triggers the real switch-off when idle. Full plan: docs/re-plan.md. Start there.
Step 1 done (22 Oct 2026): docs/protocol.md. GAIA v3 over RFCOMM, vendor 0x0495, timer cmd 0600/0601. No dedicated power-off command; lead = MMI action `powerOff`. Next: Step 2 HCI capture by Ryane.
Old Smart Control 4.0.0 (oldest on mirrors, already supports M4 + auto-off) analysed: timer cmd takes a timer number, 0 = POWER_OFF_TIMER, 1 = LIMBO_TIMEOUT (never set by any app). Hypothesis: auto-off only reaches Qualcomm "limbo"; limbo timeout decides true power-off. First test: read timer 1 (`FF 03 00 01 04 95 06 01 01`), set it short, repeat T2. Never send 0495:0200 (enter DFU) or 0040 (factory reset).

## Other build directions considered
- Hardware workaround: a magnet on a desk stand/hook at the spot where the case magnet sits, to trigger the case switch-off (r/sennheiser 154aet4 user tried this). Needs locating the Hall sensor.
- Android automation: switch media output away from the M4 / send a hard power-off when idle.
- Reverse-engineer the Bluetooth protocol (Android HCI snoop log while toggling settings in Smart Control Plus) to find the power-off / timer commands.
