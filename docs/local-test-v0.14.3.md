# Local check: v0.14.3 (outside-audit fixes)

Screens and commands are unchanged from v0.14.2 (sorted send/toggle diff identical). This is a short check of the new input handling, the report text and the guard clock. Run it in the same session as `docs/local-test-v0.14.2.md`. No `0605` except step 5.

Record per test: fw, app + version, cups, case, result.

1. **Raw field, valid.** Device > Advanced > Raw frame: `FF 03 00 00 04 95 06 03` sends and the battery reply arrives.
2. **Raw field, two frames.** `FF 03 00 00 04 95 06 03 FF 03 00 00 04 95 00 40` is refused: log `BLOCKED (more than one frame)`, nothing written (check M4GAIA has no `->` line).
3. **Raw field, typo.** `FF 03 00 00 04 95 06 0` and `FF 03 0G` give "Bad hex", nothing sent. A blocked single frame (`FF 03 00 00 04 95 15 00`) still logs `BLOCKED (device rename)` as in T40.
4. **Copy report / Copy log.** Paste both in a note: no Bluetooth address anywhere (only `XX:XX:XX:XX:XX:XX`), no name of another paired device. The renamed-headset case can't be staged on the phone (Android's rename changes only its own label, not the name the app reads), so unit tests cover it.
5. **Guard clock.** 2-min guard, nothing playing, screen off: `0605` at idle 2m00s as in T29/T35 (the run's only `0605`). Optional: set the phone clock forward 10 min by hand during the wait; the guard must not fire early (idle counts from the monotonic clock). Set the clock back to automatic afterwards.
6. **PC.** `python gaia_client.py sweep` refuses ("sweep is disabled"). `python gaia_client.py raw "FF 03 00 00 04 95 06 03 00"` refuses (stray byte), `raw "FF 03 00 00 04 95 06 03"` works.
7. **CI.** The PR shows a "Python checks" job (tests and blocklist parity) next to the Android build. A manual `release.yml` run on a version that already has a tag fails at "Refuse to re-publish an existing version".

End: PC `check` restore file, snapshot diff vs start empty, `tools/blocklist_parity_test.py`.
