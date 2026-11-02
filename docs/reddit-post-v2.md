# Reddit post v2 (ready, for v0.14.3)

Goal: collect data from other MOMENTUM 4 owners in one parseable format, so the next build round is driven by reports instead of guesses. Post in r/sennheiser. Labels match v0.14.3 (auto-connect, "Power off now" and the Auto power-off switch on Home, Device > Copy report, GitHub "Test report" form).

---

**Title:** MOMENTUM 4 auto-off fix app: looking for testers on other phones and firmware (5 min)

Hi all. A few weeks ago I posted about the MOMENTUM 4 not really switching off after 15 minutes. It only goes into a standby that keeps Bluetooth on and drains the battery.

What we found:
- The built-in auto-off timer only reaches that standby (seen on 2.x and on 3.38.3).
- The headphones do accept a remote "power off" command. It's the same full off as holding the button or closing the case.
- I built a small free Android app that sends it once your phone has played nothing and had no call for the idle time you pick (15 min by default). It also does noise control, EQ with presets, and home-screen widgets.

It works on my M4 (fw 3.38.3) with a Galaxy Z Flip6 and a Nokia XR20. Before I change anything else, I need data from other setups.

**How to test (about 5 min):**
1. Install the APK from the [latest release](https://github.com/rayane1817/momentum4-auto-off/releases/latest). Android only, no internet permission, open source. Play Protect will warn because it's not from the Play Store.
2. Force-stop Sennheiser Smart Control Plus first: only one app can talk to the headphones at a time.
3. Open the app with the headphones connected to your phone. It links by itself.
4. Tap **Device > Copy report** and paste it in a comment or in the [report form](https://github.com/rayane1817/momentum4-auto-off/issues/new/choose). The report has no Bluetooth addresses or personal data: only the phone model, Android version, headphone firmware, and the app's own log.
5. Tap **Power off now** on the Home screen. They should switch fully off and stay off until you press the button.
6. Turn them back on, switch on **Auto power-off** on the Home screen, leave the phone silent for the idle time, and see if they switch off.

**Then tell me:**
- Did Power off now switch them fully off?
- With Auto power-off on, did they switch off after the idle time?
- Did they ever switch off while you were wearing them? (Known tradeoff: wearing them in silence past the idle time switches them off.)

**Especially useful:**
- Firmware other than 3.38.3 (shown under Device in the app).
- Pixel, OnePlus, Xiaomi, Oppo, and other phones that close background apps aggressively.
- Other Sennheiser models (Momentum True Wireless 4, Accentum). Please only try **Power off now**, nothing else: the other settings are only mapped for the M4.

**One more thing:** which ONE feature would you want next? One only, so I can count.

Not affiliated with Sennheiser. If you'd rather not install anything, a comment with your firmware, your phone, and whether your M4 really switches off after 15 min helps too.

---

## How replies get processed (for the next session, not for the post)
- Copy every report and comment into `docs/community-reports.md`, one row per user: phone, Android, fw, model, power-off OK, guard OK, false off while worn, wish.
- Do this once, about 7 days after posting, in one session. Don't process replies one by one.
