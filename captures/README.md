# Captures

Short logs from the PC client (`windows-client/gaia_client.py`) for the power-off tests T8 to T10:

- `gaia_sweep_f3_20261022_232249.log`: the feature 3 sweep that found `0495:0605`.
- `gaia_replicate_0605_20261022_232705.log`: replication with only `0605`.
- `gaia_phone_connected_0605_20261022_234434.log`: same, with a phone holding the audio link.

The raw Bluetooth HCI capture that the protocol was first decoded from is not published. It also contains the names and addresses of other Bluetooth devices nearby, the phone's name and the headset's serial number. Everything relevant from it is written up in `../docs/protocol.md`. To make your own: Android Developer options > "Enable Bluetooth HCI snoop log", then parse it with `../tools/btsnoop_parse.py`.
