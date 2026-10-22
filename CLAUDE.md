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
- Next: overnight drain test (battery % before/after, off-head, phone Bluetooth on) to see whether the standby matters.
- To record on every test: firmware version, app + app version, cup orientation, case or not, result.

## Next experiments
- T4: on-head detection OFF in app, cups up, 15 min.
- Does the auto-off setting persist after an app restart (Smart Control vs Plus)?

## Chosen route (22 Oct 2026)
Decompile the official APK + Bluetooth HCI logging, then build our own small Android app that triggers the real switch-off when idle. Full plan: docs/re-plan.md. Start there.
Step 1 done (22 Oct 2026): docs/protocol.md. GAIA v3 over RFCOMM, vendor 0x0495, timer cmd 0600/0601. No dedicated power-off command; lead = MMI action `powerOff`. Next: Step 2 HCI capture by Ryane.
Old Smart Control 4.0.0 (oldest on mirrors, already supports M4 + auto-off) analysed: timer cmd takes a timer number, 0 = POWER_OFF_TIMER, 1 = LIMBO_TIMEOUT (never set by any app). Hypothesis: auto-off only reaches Qualcomm "limbo"; limbo timeout decides true power-off. First test: read timer 1 (`FF 03 00 01 04 95 06 01 01`), set it short, repeat T2. Never send 0495:0200 (enter DFU) or 0040 (factory reset).

## Other build directions considered
- Hardware workaround: a magnet on a desk stand/hook at the spot where the case magnet sits, to trigger the case switch-off (r/sennheiser 154aet4 user tried this). Needs locating the Hall sensor.
- Android automation: switch media output away from the M4 / send a hard power-off when idle.
- Reverse-engineer the Bluetooth protocol (Android HCI snoop log while toggling settings in Smart Control Plus) to find the power-off / timer commands.
