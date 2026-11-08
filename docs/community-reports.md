# Community reports (Reddit post v2, collected 8 Nov 2026)

Source: replies to `docs/reddit-post-v2.md` in r/sennheiser plus one Reddit DM, read from screenshots Ryane shared on 8 Nov 2026 (about a week after posting). No Copy-report pastes and no GitHub issue-form reports yet, so the table has no firmware or power-off data.

## Table (one row per user)

| User | Platform | Phone / Android | fw | Model | Power-off OK | Guard OK | False off while worn | Wish |
|---|---|---|---|---|---|---|---|---|
| hv_razero_15 | Android | ? | ? | M4 | ? ("features work fine") | ? | ? | 10-band EQ, or a way to put a Wavelet/Poweramp preset on the headset |
| Hot-Extent-1620 | Windows | - | ? | M4 | - | - | - | Windows app |
| Careful_Ad8704 | iPhone (Sennheiser app) | iOS | ? | M4 | - | - | - | Same "doesn't switch off" problem; asked if the fix is Android only |
| TheUnAmericanGenral (DM) | Android + Windows PC with a BTD 600 dongle | ? | ? | M4 | ? | ? | ? | PC app that works while the M4 plays from the BTD 600; asked what Low latency does |

Counted wishes (one per user): Windows app 2 (Hot-Extent-1620, TheUnAmericanGenral), better EQ 1, iOS 1.

## Findings, sorted by app

### Windows (handled in the `claude/windows-app-v0` session)
- Two users asked. An early Windows app was built in this session: `windows-client/m4_app.py` (see `windows-client/README.md`). Not part of the Android release.
- 8 Nov 2026, other MOMENTUM 4 projects on GitHub (all MIT): **Ariazonaa/momentum4-control** (WinUI 3 Windows app + Linux, signed-off on fw 3.37.3 and 3.38.3: tray quick panel, installer exe, `m4ctl` CLI, multipoint, rename, Windows battery fallback, auto-reconnect; no true power-off, its auto-off is the firmware timer), **f3Y0/momentum4-control** (Linux/KDE tray: ANC/transparency slider, bass boost), **takaok5/momentum4-audio-fix** (a guide, not an app: third-party A2DP driver for aptX HD/LDAC 24-bit and HeSuVi 7.1 on Windows 11). Our Windows app 0.2.2 took over: tray panel, Transparency button, tones/prompts, connections list + connect, charging, auto-reconnect, `m4ctl`, a PyInstaller exe. Left out on purpose: rename (`1500`) and disconnect (`1403`), both blocked. Our one feature nobody else has: the true power-off `0605` and the guard.
- Windows 11 widget board: wanted (Ryane, 8 Nov 2026), not built. It needs a C# widget provider in an MSIX package with a certificate, and a Windows 11 PC to test; the development laptop runs Windows 10. The tray panel covers the same controls until then.
- BTD 600 note for the README: the BTD 600 is an audio transmitter; the Windows app talks to the M4 over the PC's own Bluetooth, not through the dongle. The PC must also be paired with the M4. The M4 has two multipoint slots, so phone + BTD 600 + PC Bluetooth may be one too many. Needs a test with a BTD 600 (Ryane has none).

### Android
1. **Guard can switch the M4 off while you listen on another device (new, high priority).** TheUnAmericanGenral listens from the PC through a BTD 600 while the phone app is installed. The guard judges idle on the PHONE only, so if the guard is on, it will send `0605` after N min while they are listening on the PC. Same for anyone using multipoint with a laptop. Options, cheapest first:
   - Text in the guard screen and README: "The guard only sees this phone. If you also listen on a PC or tablet, it can switch them off."
   - Veto: before `0605`, read the headset's stream state (`0880` notif: `FF` = no stream, `02` = streaming, docs/protocol.md) and skip if a stream is active. T11 found the stream signal useless as an IDLE signal (Android kept the route open), but as a "don't fire" veto it may still work for other sources. Needs a planned test: phone idle, M4 playing from the laptop, check what `0880`/`0800` report. Never probe unknown commands for this.
2. **Why the widget works "without a connection" (explains the DM, no bug).** The widget opens an RFCOMM link to the bonded M4. That pages the headset and creates the Bluetooth link on demand, so it works even when Android shows the M4 as not connected, as long as the M4 has a free multipoint slot. Smart Control Plus needs the full audio connection. Worth one line in the README and the Reddit reply: it's a feature.
3. **Low latency: what does it do?** `0817`/`0818` is accepted and reads back (T13, T49), but nobody has measured whether latency changes. Smart Control Plus doesn't show it (it came from the old app). Suggest: hint text under the switch "From Sennheiser's older app. Effect not measured yet.", and ask the user to compare a video lip-sync test on and off.
4. **10-band EQ.** The headset EQ has 5 bands only (`1001 [band 0-4, gain]`, 63 Hz / 250 Hz / 1 kHz / 4 kHz / 8 kHz, ±6 dB in the app; docs/config-spec.md). There is no known 10-band command, and probing for one is against the rules. What the user really wants ("set the specific preset onto the headphone"): an **import** that takes a Wavelet / Poweramp / AutoEq GraphicEQ text and fits it to the 5 headset bands, then saves it as a custom preset. Note in the app that it's an approximation. Android first (Wavelet and Poweramp are Android apps); the Windows app can reuse the same fit later.

### iOS (no build planned)
- Careful_Ad8704 has the same problem on iPhone. An iPhone app would have to talk to the M4 through Apple's accessory framework, which needs the headset maker's MFi registration for App Store apps; sideloading is possible but awkward for normal users. Not realistic now.
- What helps iPhone users today: the Windows app's Power off button (if they have a Windows PC), or the button / case. Reply suggestion: "Android only for now, iPhone apps need Apple's accessory program. A Windows version is coming, which can switch them off too."

## Reply drafts for Ryane (no em-dashes)
- hv_razero_15: "The headphones only have 5 EQ bands in hardware, so a true 10-band EQ isn't possible on the headset. What I can do is let you import a Wavelet or Poweramp preset and fit it to the 5 bands. Would that work for you?"
- Careful_Ad8704: "Yes, Android only for now. An iPhone app would need Apple's accessory program, which I can't get as a hobby project. A Windows version is in the works and can switch them off too."
- TheUnAmericanGenral: "Thanks, this is great feedback. The widget works because it opens its own Bluetooth link to the headphones, so it doesn't need the audio connection. Low latency comes from Sennheiser's older app; I haven't measured it yet, a lip-sync test on and off would help. One warning: if you use the auto power-off guard, it only sees your phone, so it can switch the headphones off while you listen on the PC. Keep it off for now. A Windows version is in the works."
