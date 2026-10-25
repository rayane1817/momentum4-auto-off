"""Offline tests for gaia_client.py snapshot / diff / check (no Bluetooth).

Run: python -m unittest discover -s windows-client
"""
import copy
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gaia_client as G  # noqa: E402
import m4_commands as M4  # noqa: E402


class FakeHeadset:
    """Answers documented GET/SET like fw 3.38.3 as far as we know it."""

    def __init__(self, six_byte_ok=False):
        self.six_byte_ok = six_byte_ok
        self.s = {
            0x0601: b"\x00\x03\x84", 0x0602: b"\x00", 0x0603: b"\x3C", 0x0401: b"\x01", 0x0402: b"\x03",
            0x0406: b"\x01", 0x0802: b"\x02", 0x0804: b"\x00\x01", 0x0806: b"\x02", 0x0807: b"\x00",
            0x080B: b"\x01", 0x080D: b"\x01", 0x0815: b"\x01", 0x0818: b"\x00", 0x1009: b"\x00",
            0x1A03: b"\x28", 0x1A05: b"\x01", 0x1607: b"\x00", 0x1407: b"\x00",
            0x1200: b"\x00\x04\x00\x00", 0x1201: b"\x00\x03\x00\x26\x00\x03", 0x1206: b"M4AEBT SE\x00",
        }
        self.mode = {1: 0, 2: 0, 3: 0}
        self.eq = [0, 0, 0, 0, 0]
        self.registered = set()
        self.sent = []
        self.out = []
        self.drop_on = None

    def _f(self, cmd, payload=b""):
        return G.Frame(4, G.SENNHEISER, cmd, bytes(payload))

    def _notify(self, get_cmd):
        feature = get_cmd >> 9
        if feature in self.registered:
            self.out.append(self._f(get_cmd | 0x80, self._get(get_cmd, b"")))

    def _get(self, cmd, payload):
        if cmd == 0x1A01:
            return bytes([1, self.mode[1], 2, self.mode[2], 3, self.mode[3]])
        if cmd == 0x1002:
            if not payload:
                return bytes((g & 0xFF) for g in self.eq)
            return bytes([self.eq[payload[0]] & 0xFF])
        if cmd == 0x1003:
            return bytes((g & 0xFF) for g in self.eq)
        return self.s[cmd]

    # Link interface
    def send(self, frame):
        reason = G.blocked_reason(frame)
        assert reason is None, reason
        self.sent.append(frame)
        f, _ = G.parse_frames(bytearray(frame))
        f = f[0]
        cmd, p = f.command, f.payload
        if self.drop_on == cmd:
            self.out.append(None)
            return True
        if cmd == 0x0007:
            self.registered.add(p[0])
            self.out.append(self._f(0x0107))
        elif M4.COMMANDS[cmd]["kind"] == "get":
            self.out.append(self._f(cmd | 0x100, self._get(cmd, p)))
        elif cmd == 0x1A00:
            if len(p) == 6 and not self.six_byte_ok:
                self.out.append(self._f(0x1A00 | 0x180, b"\x05"))
                return True
            before = dict(self.mode)
            for i in range(0, len(p), 2):
                self.mode[p[i]] = p[i + 1]
            self.out.append(self._f(0x1B00))
            if before != self.mode:
                self._notify(0x1A01)
        elif cmd == 0x1001:
            old = self.eq[p[0]]
            self.eq[p[0]] = p[1] - 256 if p[1] > 127 else p[1]
            self.out.append(self._f(0x1101))
            if old != self.eq[p[0]]:
                self.out.append(self._f(0x1082, self._get(0x1002, b""))) if 8 in self.registered else None
        else:
            get = M4.COMMANDS[cmd]["get_for"]
            old = self.s[get]
            self.s[get] = bytes(p)
            self.out.append(self._f(cmd | 0x100))
            if old != self.s[get]:
                self._notify(get)
        return True

    def read_frames(self, window=1.0):
        if None in self.out:
            self.out = []
            return None
        got, self.out = self.out, []
        return got


def load_v08():
    with open(os.path.join(HERE, "checks", "v08.json"), encoding="utf-8") as fh:
        return json.load(fh)


class TestGuard(unittest.TestCase):
    def test_v08_file_is_documented(self):
        self.assertEqual(G.validate_check(load_v08()), [])

    def test_refuses_undocumented_and_dangerous(self):
        for cmd, payload in [(0x0605, b""), (0x0606, b""), (0x0607, b""), (0x1405, b"\x00"),
                             (0x1403, b"\x00"), (0x0040, b""), (0x0200, b""), (0x1A02, b"\x65"),
                             (0x0805, b"\x05"), (0x1001, b"\x05\x00"), (0x1A00, b"\x02\x01"),
                             (0x0007, b"\x03"), (0x1500, b"")]:
            with self.assertRaises(ValueError, msg="%04X" % cmd):
                G.guarded_frame(cmd, payload)

    def test_bad_check_file_refused(self):
        spec = {"steps": [{"id": "x", "actions": [{"set": ["0605", ""]}]},
                          {"id": "y", "actions": [{"set": ["1A02", "C8"]}]},
                          {"id": "z", "save": {"v": ["0606"]}, "actions": [{"set": ["0605", "{v}"]}]}]}
        self.assertEqual(len(G.validate_check(spec)), 4)


class TestCheck(unittest.TestCase):
    def test_v08_all_pass_and_restore(self):
        hs = FakeHeadset()
        before = copy.deepcopy(hs.s), dict(hs.mode), list(hs.eq)
        res = G.run_check(hs, load_v08(), out=lambda *_: None)
        self.assertIsNone(res["aborted"])
        bad = [(s["id"], s["result"], s["restore"], s["notes"]) for s in res["steps"]
               if s["result"] != "PASS" or s["restore"] not in ("OK", "n/a")]
        self.assertEqual(bad, [])
        step5 = [s for s in res["steps"] if s["id"] == "5"][0]
        self.assertEqual(len(step5["refused"]), 2)          # 6-byte form refused, recorded
        self.assertEqual((hs.s, hs.mode, hs.eq), before)    # everything restored
        for frame in hs.sent:                                # never sent anything undocumented
            f, _ = G.parse_frames(bytearray(frame))
            self.assertIsNone(M4.documented_reason(G.SENNHEISER, f[0].command, f[0].payload))

    def test_six_byte_accepted(self):
        res = G.run_check(FakeHeadset(six_byte_ok=True), load_v08(), out=lambda *_: None)
        step5 = [s for s in res["steps"] if s["id"] == "5"][0]
        self.assertEqual((step5["result"], step5.get("refused")), ("PASS", None))

    def test_mismatch_is_fail(self):
        hs = FakeHeadset()
        spec = {"register": False, "steps": [{"id": "1", "save": {"v": ["080D"]},
                                              "actions": [{"set": ["080C", "00"]}, {"get": ["080D"], "expect": "01"}],
                                              "restore": [{"set": ["080C", "{v}"]}]}]}
        res = G.run_check(hs, spec, out=lambda *_: None)
        self.assertEqual(res["steps"][0]["result"], "FAIL")
        self.assertEqual(res["steps"][0]["restore"], "OK")

    def test_link_drop_aborts(self):
        hs = FakeHeadset()
        hs.drop_on = 0x080C
        spec = {"register": False, "steps": [
            {"id": "1", "save": {"v": ["080D"]}, "actions": [{"set": ["080C", "00"]}]},
            {"id": "2", "actions": [{"get": ["0806"]}]}]}
        res = G.run_check(hs, spec, out=lambda *_: None)
        self.assertTrue(res["aborted"])
        self.assertEqual(len(res["steps"]), 1)


class TestSnapshotDiff(unittest.TestCase):
    def test_snapshot_and_diff(self):
        hs = FakeHeadset()
        a = G.take_snapshot(hs)
        self.assertEqual(a["values"]["1201"]["decoded"], "fw 3.38.3")
        self.assertEqual(a["values"]["1206"]["decoded"], "model M4AEBT SE")
        self.assertNotIn("1401", a["values"])
        hs.s[0x0603] = b"\x3B"                             # volatile: ignored
        b = G.take_snapshot(hs)
        self.assertEqual(G.diff_snapshots(a, b), [])
        hs.s[0x1009] = b"\x01"
        c = G.take_snapshot(hs)
        self.assertEqual([k for k, _, _ in G.diff_snapshots(a, c)], ["1009"])


class TestTemplates(unittest.TestCase):
    def test_expand(self):
        saved = {"m": b"\x01\x02\x02\x00\x03\x01", "b": b"\x01"}
        self.assertEqual(G.expand("01 {m[1]}", saved), "01 02")
        self.assertEqual(G.expand("{b^1}", saved), "00")
        self.assertEqual(G.expand("{m[5]^1}", saved), "00")
        self.assertEqual(G.expand("{m}", saved), "01 02 02 00 03 01")
        with self.assertRaises(ValueError):
            G.expand("{m^1}", saved)

    def test_match(self):
        self.assertTrue(G.match("01 ?? 03", b"\x01\x99\x03"))
        self.assertFalse(G.match("01 ??", b"\x01"))


if __name__ == "__main__":
    unittest.main()
