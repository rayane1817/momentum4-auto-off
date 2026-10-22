# MOMENTUM 4 auto-off: pre-research (22 Oct 2026)

## Official documentation (primary source)
Instruction manual, Version 08/22 A01 (launch edition; same text in the Richer Sounds copy):
- Auto-off: "After taking off the headphones, they automatically switch off after 15 minutes." Time span adjustable in Smart Control. LED: red 3x = switch off. A full switch-off, not standby.
- The timer starts "after taking off": it depends on the on-head sensor detecting removal.
- Carry case: "After being placed in the supplied carry case, the headphones immediately switch off automatically."
- Manual off: multi-function button 3 s, red LED 3x.
- Auto switch-on: by lifting and putting on. Manual on: button 3 s.
- Sensor limitation (manual): when switched on and put down or transported, the sensor may NOT detect removal, e.g. ear-cup insides facing down on a table, hung over the edge of a bag, or carried inside a bag. Manual advises switching off manually when storing outside the case.
- Sensor can be deactivated in the app; smart functions such as auto switch-off then stop working.
- Standby in the manual only refers to pairing timeout / disconnected state, not to auto-off.

Later online FAQ (undated, firmware.s-consumer-cloud.com):
- "If the headphones are taken off for 15 minutes, they switch off almost completely. They switch on again automatically when you simply touch them."
- Off-head energy saving: some functions (e.g. ANC) are deactivated when on-head detection is on.
- Case: "The headphones also switch off when they are placed into their carrying case."
=> Wording shifted from "switch off" (manual 2022) to "switch off almost completely" + wake on touch (FAQ). Suggests the behaviour was redefined after launch. Date of the FAQ unknown.

Other official:
- MOMENTUM 5 support page: auto-on removed "due to feedback from MOMENTUM 4 users".
- Support site has no M4 article on auto-off failing. The M4 "cannot be switched on / deeply discharged" article sits at about -211 votes.
- u/SennheiserOfficial (r/sennheiser, 154aet4): hold the button, don't tap. No acknowledgement of a bug.
- Smart Control Plus announced on Head-Fi 5 Apr 2025: rebuilt app, old Smart Control kept in parallel, new features only in Plus.
- Firmware: 2.9.12 (launch 2022), 2.13.18 (May 2023), 3.37.3 (20 Jan 2026, Plus only). No release note mentions the auto-off timer. Reddit related-posts mention "3.83.3" (~Aug 2026, failing updates). Not verified.
- Sennheiser rep @ericpalonen answers in the Head-Fi firmware thread.

## User reports (deviations from documented behaviour)
- After timer: headphones reported as in a wake-on-touch/movement state rather than off; reconnect on movement (z54x4g, y8723m).
- Manual off reportedly undone by movement in several reports (y8723m, z54x4g, 154aet4). Not documented behaviour.
- Case shutdown: works with cups facing the back of the case (16zc679); jiggling may read as removal (y8723m).
- Note: a review quoted in z54x4g claims cups-down on a table is needed for auto-off. The manual says the opposite: cups-down can stop the sensor detecting removal. Treat the review quote as wrong.
- Sweat or dirt on the right-cup sensor breaks head detection; cleaning fixed several users.

## Android-specific findings
- z54x4g (2025 comment): Android keeps media output on MOMENTUM 4, so calls, alarms and notifications keep waking/using the headset. Workaround: switch media output to "This phone".
- y8723m: smart features disabled in the app "always get reset or re-enabled soon after on Android".
- 17meao8: app settings change on their own (ANC/transparency slider).
- 16is8pr: app running in background makes it reconnect to devices.

## Timeline of "it broke"
- Aug 2022: manual documents true auto-off.
- Oct 2022 (fw 2.9.x): reconnect/wake complaints from launch.
- Sep 2023 (fw 2.13.18): drain while switched off and in case (multiple confirmations).
- Dec 2023 (18rtq0h): firmware update not offered in North America; VPN to Germany made it appear. Not an auto-off fix.
- ~2025: "auto turn off was working half a year ago, then they crashed it with some update" (17meao8).
- ~mid 2026: "worked until ~3 months ago" (17meao8).
- Jan 2026 (fw 3.37.3): reports of powering off while worn, and reconnecting when taken off.
No source names a specific Android app version.

## Working hypothesis
1. Documented design: timer = full off, triggered by sensor-detected removal. If the sensor doesn't detect removal (cups down, in bag, dirty sensor), the timer never starts.
2. Later firmware redefined auto-off as near-off with wake-on-touch (FAQ wording), which makes accidental wake-ups and drain likely.
3. Firmware regressions (2.13.18, later) affect manual/case off too.
4. Android media-output routing is a separate trigger.
Timer, sensor logic and power states live in firmware; the app only writes the timer value.

## Experiments to run before coding
- After the timer: without touching the headphones, turn phone Bluetooth on. Instant auto-connect = not off.
- Put down with cups facing UP vs DOWN (manual says down can block detection).
- Clean the right-cup sensor.
- Check whether the auto-off setting survives an app restart (Smart Control vs Plus).

## Sources
- Manual 08/22 A01: https://media.graphassets.com/CJdvxcJRTVROai9rHI3r and https://www.richersounds.com/content/product_files/MOMENTUM4Manual.pdf
- FAQ: https://firmware.s-consumer-cloud.com/help/products/m4/en/faq/M4_FAQ_A01_EN/If_a_problem_occurs-jk6k.htm
- MOMENTUM 5 auto-on note: https://support.sennheiser-hearing.com/hc/en-at/articles/36289764183709-Do-the-headphones-switch-off-automatically-when-not-in-use
- Firmware 3.37.3: https://us.sennheiser-hearing.com/blogs/sound-space/firmware-update-version-3-37-3-for-momentum-4-wireless
- Firmware 2.13.18 / Smart Control 4.3: https://newsroom.sennheiser.com/your-headphones-your-sound-srf5we
- Head-Fi: https://www.head-fi.org/threads/momentum-4-firmware-update.979246/ and https://www.head-fi.org/threads/smart-control-plus-a-new-ish-app-for-connected-sennheiser-headphones.976419/
- r/sennheiser threads (prefix https://www.reddit.com/r/sennheiser/comments/): 18rtq0h, 16is8pr, y3csft, 17meao8, 154aet4, y8723m, 1ddwibw, 16zc679, z54x4g, 15d7qo2, y7iikd, 1d9et97, 12oht43
