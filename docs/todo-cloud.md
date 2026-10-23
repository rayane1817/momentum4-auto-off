# To-do for the cloud session: feature parity with Smart Control Plus

Goal: decide, per feature in Smart Control Plus, what still needs to be found out before the Android app can offer it, and the cheapest way to find it.

## How much more to decompile: as little as possible
- Smart Control Plus 1.6.x is Flutter: logic is compiled Dart (`libapp.so`). blutter recovers names and enums but no function bodies, so **byte values cannot be read from the new app** (see `protocol.md`, "App architecture").
- What decompiling is still good for: feature and enum names, option lists (e.g. which codecs a mode allows), which settings need a headset restart.
- Byte values come from, in this order:
  1. The old Smart Control (`m4.json`, readable JSON command table, already analysed in `protocol.md`).
  2. Community docs (`community-protocol-notes.md`).
  3. An HCI capture while toggling the setting once in Smart Control Plus (ground truth, ~5 minutes per batch for Ryane).
- Only interoperability use; never copy Sennheiser code or assets into this repo.

So the main work is not more decompiling but one well-planned capture session. Prepare the list below, Ryane captures, then decode.

## Feature list (from the Smart Control Plus settings screen, fw 3.38.3)

| feature in app | status in our app | known commands | what's missing | how to get it |
|---|---|---|---|---|
| Firmware version | read | `1201` | nothing | done |
| Codec readout ("aptX 44.1 kHz") | codec read | `0800` -> `0900 [id]`, sample rate notif `089A [u32]` | full codec id table (SBC, AAC, aptX, aptX Adaptive, aptX HD) | old app enums + Oein (`05` = aptX HD); confirm by connecting with each codec |
| Audio mode priority: "Reliable connection" (SBC/AAC) vs "Advanced audio quality" (aptX family) | missing | candidate: BT compatibility `0405`/`0406` (old app) | command + values; app says the change needs a headset restart | capture: switch mode once each way; check whether the app sends a reboot afterwards (Reboot command bytes still unknown) |
| Hi-Res 24-bit/96 kHz toggle | missing | none (added in fw 2.13.18, May 2023) | command + values; also needs a restart | capture: toggle once each way |
| On-head detection | missing in UI | `0400` SET / `0401` GET | nothing (capture-confirmed GET) | add to app |
| Smart pause | in app | `080C` / `080D` | nothing | done |
| Auto-answer | in app | `080A` / `080B` | nothing | done |
| Auto power-off timer | advanced section | `0600` / `0601` | "Never" encoding (likely 0) | read after setting "Never" in the app |
| Comfort call | missing | `0814` / `0815` | payload (likely 1 byte 0/1) | capture or test GET |
| Voice prompts + language | voice prompts read | `0801` / `0802`, language `0807` | language id table | capture: change language once |
| Factory reset | blocked | `0040` | do not implement | never |

From the main screen and community notes (see `community-protocol-notes.md`):

| feature | missing | how |
|---|---|---|
| Adaptive ANC, anti-wind (Off / Max / Auto) | `1A00 [mode, state]` values | capture toggling each |
| EQ (5 bands, presets, bass boost) | M4 band centres and gain range (`1000`), preset handling | read `1000` on the M4; capture choosing a preset |
| Sound personalisation / Sound Check | unknown | capture once; lower priority |
| Paired devices / multipoint connect-disconnect | list command | read `1400`/`1401`, decode; never `1405`/`1406` |
| Touch controls on/off, touch lock | `1606` / `1607` (old app) | test GET, then capture |
| Reboot (needed after codec/hi-res changes) | bytes unknown | capture the codec or hi-res change; the reboot should appear right after |

## Requested by users (r/sennheiser, 23 Oct 2026)
- **Android home-screen widget**: change ANC mode and EQ preset without opening the app (u/TheUnAmericanGenral, offered to donate). ANC/transparency/off commands are already in the app, so a widget for those is ready to build. EQ presets need the capture session below first.
- **Windows**: same user is on Windows with a BTD600 dongle. The dongle holds the headset link, so a PC tool needs the M4 paired to the PC's own Bluetooth as well (multipoint). Lower priority.

## Bug reports
- **Galaxy Tab S10+: "doesn't detect the headphones"** (u/Ncis16; works on his Galaxy S26 Ultra). Code check: `loadDevices()` only lists *paired* devices, once, at app start; nothing refreshes it if Bluetooth was off, the permission came later, or the M4 was paired after opening. Likely causes, to confirm with the user: M4 not paired to the tablet; multipoint already busy with two other devices so the RFCOMM connect fails; Nearby devices permission denied; Smart Control Plus holding the channel. Fixes: a Refresh button, re-list on Bluetooth state change, a clear message when no MOMENTUM is paired, and a hint about multipoint when connect fails.

## Capture session plan for Ryane (one session, ~15 min)
HCI snoop on, Bluetooth off/on, then in Smart Control Plus, noting the time of each step:
1. Audio mode priority: Advanced -> Reliable -> Advanced (headset restarts each time).
2. Hi-Res 24/96: on -> off.
3. Noise control: adaptive on/off, anti-wind Off -> Max -> Auto -> Off.
4. EQ: pick two presets, move one band, back to flat.
5. Voice prompt language: change and change back.
6. Comfort call: off -> on.
7. Auto power-off: Never -> 15 min.
8. Touch controls: off -> on.
Then SysDump > Copy to sdcard, send `log/bluetooth/btsnoop_hci_*.cfa`. Keep the raw capture out of the public repo (it contains nearby device names and the serial).

## Order of work
1. Prepare decoder support for the new frames (parser already exists: `tools/btsnoop_parse.py`).
2. Ryane runs the capture session above.
3. Decode, update `config-spec.md`, mark each row confirmed.
4. Add confirmed features to the app; codec/hi-res last, because they restart the headset.
