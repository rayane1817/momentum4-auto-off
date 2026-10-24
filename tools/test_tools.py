"""Unit tests for the local-session tools. Run: python -m unittest discover -s tools"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adbui  # noqa: E402
import gaia_annotate  # noqa: E402
import m4_commands as M4  # noqa: E402
import mark  # noqa: E402
from blocklist_parity_test import TestBlocklistParity  # noqa: E402,F401  (runs under discover)


class TestCommands(unittest.TestCase):
    def test_payload_ranges(self):
        ok = [(0x1A02, b"\x00"), (0x1A02, b"\x64"), (0x1A00, b"\x01\x02"), (0x1A00, b"\x03\x01"),
              (0x1A00, b"\x01\x00\x02\x01\x03\x00"), (0x1001, b"\x04\xC4"), (0x1001, b"\x00\x3C"),
              (0x0803, b"\x00\x02"), (0x0805, b"\x04"), (0x0601, b"\x00"), (0x0600, b"\x00\x03\x84"),
              (0x1A05, b""), (0x0007, b"\x0D")]
        bad = [(0x1A02, b"\x65"), (0x1A00, b"\x01\x03"), (0x1A00, b"\x02\x01"), (0x1A00, b"\x03"),
               (0x1001, b"\x05\x00"), (0x1001, b"\x00\x3D"), (0x1001, b"\x00\xC3"), (0x0805, b"\x05"),
               (0x1A05, b"\x00"), (0x0601, b"\x01"), (0x0600, b"\x01\x00\x3C"), (0x0007, b"\x06"),
               (0x0803, b"\x00\x03"), (0x1408, b"")]
        for c, p in ok:
            self.assertTrue(M4.payload_ok(c, p), "%04X %s" % (c, p.hex()))
        for c, p in bad:
            self.assertFalse(M4.payload_ok(c, p), "%04X %s" % (c, p.hex()))

    def test_ids_consistent(self):
        for cmd, e in M4.COMMANDS.items():
            self.assertEqual(M4.ftype(cmd), 0, "%04X" % cmd)
            self.assertEqual(e["feature"], cmd >> 9)
            if e["get_for"]:
                self.assertEqual(M4.COMMANDS[e["get_for"]]["kind"], "get")
            if e["notif"]:
                self.assertEqual(M4.ftype(e["notif"]), 1)
        for key, cmd, payload in M4.SNAPSHOT_GETS:
            self.assertEqual(M4.COMMANDS[cmd]["kind"], "get", key)
            self.assertIsNone(M4.documented_reason(M4.SENNHEISER, cmd, payload), key)
        self.assertNotIn(0x1401, [c for _, c, _ in M4.SNAPSHOT_GETS])

    def test_never_from_pc(self):
        for cmd in (0x0605, 0x0606, 0x0607, 0x1403, 0x1405, 0x1406, 0x0040, 0x0200, 0x1500):
            self.assertIsNotNone(M4.documented_reason(M4.SENNHEISER, cmd, b""), "%04X" % cmd)
        self.assertIsNotNone(M4.documented_reason(M4.QUALCOMM, 0x0001, b""))

    def test_labels_and_decode(self):
        self.assertEqual(M4.label(0x0495, 0x1B05), "ANC master get (resp)")
        self.assertEqual(M4.label(0x0495, 0x1A85), "ANC master (notif)")
        self.assertEqual(M4.label(0x0495, 0x2011), "UNKNOWN")
        self.assertEqual(M4.decode(0x1201, bytes.fromhex("000300260003")), "fw 3.38.3")
        self.assertEqual(M4.decode(0x1A01, bytes.fromhex("010202000301")), "antiWind=2 transparency=0 adaptive=1")
        self.assertEqual(M4.decode(0x1002, bytes.fromhex("14EC")), "EQ dB 2.0 -2.0")


class TestAnnotate(unittest.TestCase):
    def test_selftest(self):
        self.assertEqual(gaia_annotate.selftest(verbose=False), [])

    def test_parse_rfcomm(self):
        self.assertEqual(gaia_annotate.parse_rfcomm(bytes([0x7B, 0xEF, 0x05, 0xAA, 0xBB, 0x00])),
                         (30, "UIH", b"\xAA\xBB"))
        self.assertEqual(gaia_annotate.parse_rfcomm(bytes([0x0F, 0xFF, 0x03, 0x01, 0xCC, 0x00])),
                         (3, "UIH", b"\xCC"))


class TestAdbui(unittest.TestCase):
    XML = (b'<?xml version="1.0" encoding="UTF-8"?><hierarchy rotation="0">'
           b'<node index="0" text="" content-desc="Anti-vent" class="android.view.View" bounds="[0,100][1080,200]"'
           b' checked="false" selected="false" clickable="true"/>'
           b'<node index="1" text="Max" content-desc="" class="android.widget.Button" bounds="[700,300][900,400]"'
           b' checked="true" selected="false"/>'
           b'<node index="2" text="" content-desc="" class="android.widget.SeekBar" bounds="[100,500][1000,560]"/>'
           b'</hierarchy>')

    def test_find(self):
        nodes = adbui.parse_nodes(self.XML)
        self.assertEqual(len(adbui.find(nodes, desc="anti-vent")), 1)
        hit = adbui.find(nodes, text="^max$")[0]
        self.assertEqual(hit["_center"], (800, 350))
        self.assertEqual(hit["checked"], "true")
        self.assertEqual(adbui.find(nodes, cls="SeekBar")[0]["_box"], (100, 500, 1000, 560))

    def test_seek_point(self):
        box = (100, 500, 1000, 560)
        self.assertEqual(adbui.seek_point(box, 0.0, pad=50), (150, 530))
        self.assertEqual(adbui.seek_point(box, 1.0, pad=50), (950, 530))
        self.assertEqual(adbui.seek_point(box, 0.5, pad=50), (550, 530))


class TestMark(unittest.TestCase):
    def test_parse(self):
        self.assertEqual(mark.parse_out("1761400000.123456789\n+0100\n"), (1761400000.123456789, 3600))
        self.assertEqual(mark.parse_tz("-0530"), -19800)


if __name__ == "__main__":
    unittest.main()
