# Capture 3: Smart Control Plus writes, marked (25 Oct 2026, redacted)

Source: phone HCI snoop (`btsnoop_hci.log` from `adb bugreport`, raw file private), decoded with
`tools/gaia_annotate.py --markers` (markers from `tools/mark.py`, phone clock, tz +7200 s).
Setup: M4 fw 3.38.3, Smart Control Plus 1.6.3 (72844), Galaxy Z Flip6 One UI 8.5, headset on the desk,
cups up, no case. Each action = `mark <x>_begin`, one tap via `tools/adbui.py`, `mark <x>_end`, 3 s idle.
Times are phone local time. Only SET frames inside this session's markers are listed.

| time | marker (SCP action) | request | reply | notification |
|---|---|---|---|---|
| 00:56:04 | custom_mode (Personnalisé) | `1A00 [03 00]`, `1A04 [01]` | `1B00`, `1B04` | `1A81 [01 00 02 00 03 00]` |
| 00:56:41 | antiwind_max (detail, tap **Max**) | `1A00 [01 01]` | `1B00` | `1A81 [01 01 02 00 03 00]` |
| 00:56:50 | antiwind_auto (detail, tap **Auto**) | `1A00 [01 02]` | `1B00` | `1A81 [01 02 02 00 03 00]` |
| 00:57:12 | adaptive_mode (Adaptatif) | `1A04 [01]`, `1A00 [03 01]` | `1B04`, `1B00` | `1A81 [01 02 02 00 03 01]` |
| 00:57:32 | custom_mode2 | `1A00 [03 00]`, `1A04 [01]` | OK | `1A81` |
| 00:58:35 | antiwind_off_b (Anti-vent switch off) | `1A00 [01 00]` | `1B00` | `1A81 [01 00 ...]` |
| 00:58:44 | antiwind_on_b (Anti-vent switch on) | `1A00 [01 02]` | `1B00` | `1A81 [01 02 ...]` |
| 00:59:09 | antiwind_max_b (tap **Max**) | `1A00 [01 01]` | `1B00` | `1A81 [01 01 ...]` |
| 00:59:20 | antiwind_auto_b (tap **Auto**) | `1A00 [01 02]` | `1B00` | `1A81 [01 02 ...]` |
| 00:59:42 | antiwind_off_c (switch off) | `1A00 [01 00]` | `1B00` | `1A81 [01 00 ...]` |
| 00:59:55 | adaptive_mode_b | `1A04 [01]`, `1A00 [03 01]` | OK | `1A81 [01 00 02 00 03 01]` |
| 01:00:24 | touch_off (Commandes switch off) | `1606 [01]` | `1706` | `1687 [01]` |
| 01:00:32 | touch_on (Commandes switch on) | `1606 [00]` | `1706` | `1687 [00]` |
| 01:00:57 | comfort_on (Appels confortables on) | `0814 [01]` | `0914` | none |
| 01:01:05 | comfort_off | `0814 [00]` | `0914` | none |
| 01:01:30 | onhead_on (Détection au niveau de la tête on) | `0400 [01]` | `0500` | none |
| 01:01:57 | autooff_never (Mise hors tension automatique: Jamais) | `0600 [00 00 00]` | `0700` | none |
| 01:02:06 | autooff_15 (15 min) | `0600 [00 03 84]` | `0700` | none |

Marker windows with no write, as expected: `antiwind_on`, `antiwind_off`, `antiwind_on2`, `antiwind_toggle3`
(taps landed on the tile label, not the switch), `onhead_off` (tap missed; restored from the PC with `0400 [00]`).

## Results
- **Anti-wind: `1A00 [01 01]` = Max, `[01 02]` = Auto, `[01 00]` = off.** Seen twice each. Switching anti-wind on
  writes `[01 02]` (Auto is the default). This matches omarchy and reverses the Capture 2 note.
- Touch controls: `1606` inverted (01 = off), with notification `1687`. Comfort call `0814` not inverted, no notification.
- Auto power-off "Jamais" (Never) = `0600 [00 00 00]`.
- SCP's connect burst also reads ids not in our table (findings only, never to probe): `0495:0001`, `0808`, `060E`,
  `1000`, `1202`, `1400`, `1404`, `1409`, `1801`, `1805`; Qualcomm core `001D:0001/0003/000C/000D/0007/0E10`.
- Earlier in the same file (24 Oct ~20:53-20:55, Ryane's own SCP use, no markers): unknown SET `0495:1800 [00]/[01]`
  (resp `1900`). Feature 12 = transparent hearing; GET `1801` -> `[00]` at connect. Best guess: "Pause automatique"
  in transparency. Unconfirmed: needs one marked toggle.
