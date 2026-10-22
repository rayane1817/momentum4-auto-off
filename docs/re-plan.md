# Reverse-engineering plan (chosen route, 22 Oct 2026)

Goal: an Android app of our own that sends the MOMENTUM 4 into its real switch-off (the same state the case or the button triggers) after X minutes idle.
Route chosen by Ryane: decompile the official APK + Bluetooth HCI logging. Not the magnet workaround.

## Why build our own app instead of patching Smart Control Plus
- Recompiling a modified official APK means re-signing it: it can no longer update from Play, may fail integrity/account checks, and any update overwrites the patch.
- The firmware decides power states; a patched app only gives a new UI for the same commands.
- Decompiling is still essential: it shows the command set and message format. Use it as documentation, then implement the few commands needed in a small app of our own.
- Legal note (not legal advice): EU law allows decompiling for interoperability under conditions (Software Directive 2009/24/EC art. 6). Keep the work to interoperability, do not redistribute Sennheiser code.

## Known facts that shape the work
- Device: M4AEBT SE, hw 4, fw 3.38.3, connection "Classic" in the app => expect a vendor protocol over Bluetooth Classic (likely RFCOMM/SPP), not BLE GATT. Verify in the capture.
- Case switch-off is immediate and works (T3). Probably triggered locally by a magnet/Hall sensor, so the capture may only show a disconnect. Do not expect a "case" command on the wire.
- Auto-off timer leaves the radio active (T1, T2).

## Step 1: static analysis (cloud session) - DONE 22 Oct 2026, see docs/protocol.md
Result: Flutter app, GAIA v3 over RFCOMM (UUID a2129ff3-081b-4c45-8afe-469d9c4842ec), vendor 0x0495. Timer SET/GET = 0600/0601. No dedicated power-off command found; best lead is the MMI button action `powerOff` (value 8). Byte values for Reboot/MMI not recoverable statically (blutter gives no code bodies for Dart 3.10.8), so Step 2 matters.

1. Get APKs: Smart Control Plus `com.sonova.chb.control` (APKPure/APKMirror); also an old Smart Control (APKMirror 4.5.0 to 4.9.3) for comparison.
2. `jadx -d out/ app.apk` (or jadx-gui). Check for split APKs / native libs (`lib/*.so`); protocol code might be in native code.
3. Search: "autoOff", "auto_off", "powerOff", "power_off", "switchOff", "standby", "shutdown", "timeout", "onHead", "rfcomm", "UUID", "SPP", "gaia".
4. Write down: transport (RFCOMM UUID or GATT service), frame format (header, length, command id, payload, checksum), and every command near power/timer.
5. Output: docs/protocol.md.

## Step 2: dynamic capture (Ryane, on the phone)
1. Developer options > Enable Bluetooth HCI snoop log (Full). Toggle Bluetooth off/on.
2. Open the app and do, one action at a time with ~10 s pauses, noting the clock time:
   a. connect / open device page
   b. auto-off timer 15 > 30 > 15
   c. on-head detection off > on
   d. any power-off / switch-off option in the app, if it exists
   d2. button / touch control settings: open the page, change any option, change it back (looking for the MMI config command)
   d3. anything that says "restart" / "Apply & Restart" (looking for the Reboot command)
   e. put the headphones in the case (expect disconnect)
3. `adb bugreport bugreport.zip`, extract `btsnoop_hci.log`, add it to the repo under `captures/` (private repo).
4. Turn the snoop log off.

## Step 3: match and test
0. First test (from old app analysis): read and set the LIMBO_TIMEOUT timer (timer 1), see docs/protocol.md "Timers: the key finding".
1. Wireshark: filter on the headphones' MAC, RFCOMM (or ATT) payloads at the noted times. Match bytes against docs/protocol.md.
2. Minimal test client (Python on a laptop with Bluetooth, or a tiny Android app) that connects and replays the timer command with a new value. If that works, try the power-off command if one exists.

## Step 4: the app
- Kotlin, minimal: connect to the M4, watch for idle (no audio routed / headphones off-head / no connection activity), send power-off after N minutes.
- Only if a power-off command exists. If none exists, fallback options: very short timer value tests, or the magnet workaround.

## Open questions
- Is there a command to trigger an MMI action (powerOff) directly, or only to remap a button? Step 2 d2.
- Timer value unit (s or min)? Step 2 b.
- Answered: protocol is in Dart (libapp.so); no dedicated power-off feature in the app.
