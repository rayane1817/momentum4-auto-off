# Reddit post v2 (draft, post after v0.13.0 is released)

Goal: collect data from other MOMENTUM 4 owners in one parseable format, so the next build round is driven by reports instead of guesses. Post in r/sennheiser. Links assume v0.13.0 ships the "Copy report" button and the GitHub issue form.

---

**Title:** MOMENTUM 4 auto-off fix app: looking for testers on other phones and firmware (5 min)

Hi all. A few weeks ago I posted about the MOMENTUM 4 not really switching off after 15 minutes (it only goes to a standby that keeps Bluetooth on and drains the battery).

Short version of what we found:
- The firmware's own auto-off timer only reaches that standby, on 2.x and on 3.38.3.
- The headphones do have a real remote "power off" command. It's the same full off as holding the button or closing the case.
- I built a small free Android app that sends it when your phone has played nothing and had no call for 15 minutes. It also does noise control, EQ presets, and home-screen widgets.

It works on my headphones and two phones. Now I need data from other setups before I change anything else.

**What I'd like from you (any of these helps):**
1. Install the app: [latest release](https://github.com/rayane1817/momentum4-auto-off/releases/latest). It has no internet permission and is open source.
2. Connect, then tap **Device > Copy report** and paste the text in a comment or in the [report form](https://github.com/rayane1817/momentum4-auto-off/issues/new/choose). The report has no Bluetooth addresses or personal data: only the phone model, Android version, headphone firmware, and the app's own log.
3. Tell me:
   - Did "Power off" switch them fully off? (They shouldn't reconnect until you press the button.)
   - With the guard on, did they switch off after the idle time?
   - Did they ever switch off while you were wearing them?

**Especially useful:**
- Firmware other than 3.38.3 (it's under Device in the app).
- Pixel, OnePlus, Xiaomi, and Oppo phones, which kill background apps aggressively.
- Other Sennheiser models (Momentum True Wireless 4, Accentum). Please only try **Power off**, nothing else, since the other commands are only mapped for the M4.

**Also tell me:** which one feature you'd want next (one only, so I can count).

Not affiliated with Sennheiser. If you'd rather not install anything, a comment with your firmware version, phone, and whether your M4 really switches off after 15 min helps too.

---

## How replies get processed (for the next session, not for the post)
- Copy every report and comment into `docs/community-reports.md`, one row per user: phone, Android, fw, model, power-off OK, guard OK, false off while worn, wish.
- Do this once, about 7 days after posting, in one session. Don't process replies one by one.
