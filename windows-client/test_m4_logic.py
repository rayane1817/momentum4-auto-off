"""Tests for the Windows app's frames and decoding (m4_logic.py). No headset, no tkinter."""

import os
import py_compile
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import m4_logic as L  # noqa: E402

G, M4 = L.G, L.M4


def hx(frames):
    return [G.hexstr(f) for f in frames]


def frame(cmd, payload, vendor=0x0495):
    return G.Frame(3, vendor, cmd, bytes(payload))


def every_app_frame():
    out = list(L.connect_frames())
    for mode in range(3):
        for level in (0, 1, 50, 100):
            out += L.noise_frames(mode, level)
    out += L.level_frames(0) + L.level_frames(100)
    out += [L.anti_wind_frame(i) for i in range(3)]
    for _n, gains in L.BUILTIN_PRESETS:
        out += L.eq_curve_frames(gains)
    out += [L.eq_band_frame(b, g) for b in range(5) for g in (-60, -5, 0, 5, 60)]
    for key, *_rest in L.TOGGLES:
        out += L.toggle_frames(key, True) + L.toggle_frames(key, False)
    out += L.speech_frames(True) + L.speech_frames(False)
    out += [f for lv in range(5) for f in L.call_transparency_frames(lv)]
    out.append(L.battery_frame())
    return out


class SafetyTest(unittest.TestCase):
    def test_every_frame_is_documented_and_not_blocked(self):
        for f in every_app_frame():
            vendor, cmd, payload = (f[4] << 8) | f[5], (f[6] << 8) | f[7], f[8:]
            self.assertIsNone(G.single_frame_problem(f), G.hexstr(f))
            self.assertIsNone(G.blocked_reason(f), G.hexstr(f))
            self.assertIsNone(M4.documented_reason(vendor, cmd, payload), G.hexstr(f))
            self.assertNotEqual(f, L.POWER_OFF)

    def test_power_off_is_exactly_0605_and_only_via_its_constant(self):
        self.assertEqual(G.hexstr(L.POWER_OFF), "FF 03 00 00 04 95 06 05")
        self.assertIsNone(G.blocked_reason(L.POWER_OFF))
        with self.assertRaises(L.Refused):
            L.req(0x0605)

    def test_blocked_and_undocumented_commands_are_refused(self):
        for cmd, payload in ((0x0040, ()), (0x0200, ()), (0x1403, (0,)), (0x1405, (0,)), (0x1406, ()),
                             (0x0607, ()), (0x0609, ()), (0x1500, ()), (0x0606, ()), (0x1800, ())):
            with self.assertRaises(L.Refused, msg="%04X" % cmd):
                L.req(cmd, *payload)

    def test_out_of_range_payloads_are_refused(self):
        with self.assertRaises(L.Refused):
            L.req(0x1001, 5, 0)          # band 5 does not exist
        with self.assertRaises(L.Refused):
            L.req(0x1001, 0, 61)         # +6.1 dB
        with self.assertRaises(L.Refused):
            L.req(0x1A02, 101)


class FrameTest(unittest.TestCase):
    """Bytes match the Android app, verified on hardware in T47-T49."""

    def test_noise_modes(self):
        self.assertEqual(hx(L.noise_frames(0, 30)), ["FF 03 00 01 04 95 1A 04 01", "FF 03 00 02 04 95 1A 00 03 01"])
        self.assertEqual(hx(L.noise_frames(1, 50)), ["FF 03 00 02 04 95 1A 00 03 00", "FF 03 00 01 04 95 1A 04 01",
                                                     "FF 03 00 01 04 95 1A 02 32"])
        self.assertEqual(hx(L.noise_frames(2, 50)), ["FF 03 00 02 04 95 1A 00 03 00", "FF 03 00 01 04 95 1A 04 00"])

    def test_anti_wind_wire_values(self):
        self.assertEqual([G.hexstr(L.anti_wind_frame(i))[-5:] for i in range(3)], ["01 00", "01 02", "01 01"])

    def test_eq(self):
        self.assertEqual(G.hexstr(L.eq_band_frame(1, 30)), "FF 03 00 02 04 95 10 01 01 1E")
        self.assertEqual(G.hexstr(L.eq_band_frame(0, -20)), "FF 03 00 02 04 95 10 01 00 EC")
        rock = dict(L.BUILTIN_PRESETS)["Rock"]
        self.assertEqual([G.hexstr(f)[-5:] for f in L.eq_curve_frames(rock)],
                         ["00 00", "01 14", "02 19", "03 0F", "04 EC"])
        self.assertEqual(G.hexstr(L.eq_band_frame(2, 99))[-5:], "02 3C")   # clamped to +6.0

    def test_inverted_toggles(self):
        self.assertEqual(hx(L.toggle_frames("touch", True)), ["FF 03 00 01 04 95 16 06 00", "FF 03 00 00 04 95 16 07"])
        self.assertEqual(hx(L.toggle_frames("hires", True))[0], "FF 03 00 01 04 95 04 05 00")
        self.assertEqual(hx(L.toggle_frames("smart_pause", True))[0], "FF 03 00 01 04 95 08 0C 01")

    def test_speech_and_calls(self):
        self.assertEqual(hx(L.speech_frames(True))[0], "FF 03 00 02 04 95 08 03 00 02")
        self.assertEqual(hx(L.call_transparency_frames(9))[0], "FF 03 00 01 04 95 08 05 04")

    def test_connect_registers_then_reads(self):
        f = hx(L.connect_frames())
        self.assertEqual(f[:3], ["FF 03 00 01 04 95 00 07 0D", "FF 03 00 01 04 95 00 07 08",
                                 "FF 03 00 01 04 95 00 07 04"])
        self.assertIn("FF 03 00 01 04 95 10 03 00", f)


class DecodeTest(unittest.TestCase):
    def test_values(self):
        self.assertEqual(L.decode(frame(0x0703, [62])), {"battery": 62})
        self.assertEqual(L.decode(frame(0x0683, [61])), {"battery": 61})                 # notif
        self.assertEqual(L.decode(frame(0x1301, [0, 3, 0, 0x26, 0, 3])), {"firmware": "3.38.3"})
        self.assertEqual(L.decode(frame(0x1300, [0, 4, 0, 0])), {"hardware": "4"})
        self.assertEqual(L.decode(frame(0x1306, b"M4AEBT SE\0")), {"model": "M4AEBT SE"})
        self.assertEqual(L.decode(frame(0x1B01, [1, 2, 3, 1])), {"anti_wind": 1, "adaptive_on": True})
        self.assertEqual(L.decode(frame(0x1B05, [0])), {"anc_on": False})
        self.assertEqual(L.decode(frame(0x0701, [0, 0x03, 0x84])), {"timer": 900})
        self.assertEqual(L.decode(frame(0x0904, [0, 2])), {"speech": True})

    def test_eq_reply_and_notification(self):
        p = [0xCE, 0, 0x16, 0x16, 0]                    # -5.0 0 +2.2 +2.2 0
        self.assertEqual(L.decode(frame(0x1103, p)), {"eq": (-50, 0, 22, 22, 0)})
        self.assertEqual(L.decode(frame(0x1082, p)), {"eq": (-50, 0, 22, 22, 0)})
        self.assertEqual(L.decode(frame(0x1102, [1, 0x14])), {})   # single-band reply is ignored

    def test_toggles_and_inversion(self):
        self.assertEqual(L.decode(frame(0x1707, [0])), {"touch": True})
        self.assertEqual(L.decode(frame(0x0506, [0])), {"hires": True})
        self.assertEqual(L.decode(frame(0x090D, [1])), {"smart_pause": True})
        self.assertEqual(L.decode(frame(0x0898, [0])), {"low_latency": False})          # notif

    def test_stream_notif_and_errors(self):
        self.assertEqual(L.decode(frame(0x0880, [0xFF])), {"streaming": False})
        self.assertEqual(L.decode(frame(0x0880, [0x02])), {"streaming": True})
        self.assertEqual(L.decode(frame(0x0983, [0x83])), {"error": (0x0803, 0x83)})
        self.assertEqual(L.decode(frame(0x0703, [50], vendor=0x001D)), {})

    def test_noise_index(self):
        self.assertEqual(L.noise_mode_index(True, True), 0)
        self.assertEqual(L.noise_mode_index(True, False), 1)
        self.assertEqual(L.noise_mode_index(False, True), 2)


class DeviceTest(unittest.TestCase):
    def test_format_addr(self):
        self.assertEqual(L.format_addr("a1b2c3d4e5f6"), "A1:B2:C3:D4:E5:F6")
        self.assertIsNone(L.format_addr("xyz"))
        self.assertIsNone(L.format_addr("a1b2c3d4e5"))

    def test_pick_default(self):
        devs = [("Keyboard", "00:00:00:00:00:01"), ("MOMENTUM 4", "00:00:00:00:00:02")]
        self.assertEqual(L.pick_default(devs), 1)
        self.assertEqual(L.pick_default(devs, "00:00:00:00:00:01"), 0)
        self.assertIsNone(L.pick_default([]))

    def test_preset_name(self):
        self.assertEqual(L.preset_name((0, 20, 25, 15, -20)), "Rock")
        self.assertEqual(L.preset_name((1, 2, 3, 4, 5), {"Mine": (1, 2, 3, 4, 5)}), "Mine")
        self.assertIsNone(L.preset_name((1, 1, 1, 1, 1)))


class AppSourceTest(unittest.TestCase):
    def test_app_compiles(self):
        here = os.path.dirname(os.path.abspath(__file__))
        py_compile.compile(os.path.join(here, "m4_app.py"), doraise=True,
                           cfile=os.path.join(here, "__pycache__", "m4_app_check.pyc"))


if __name__ == "__main__":
    unittest.main()
