"""Tests for the Windows app's core (actions, state) and its local server. No headset, no browser."""

import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import m4_app as A  # noqa: E402
import m4_logic as L  # noqa: E402

G = L.G
DEVS = [("Keyboard", "00:00:00:00:00:01"), ("MOMENTUM 4", "00:00:00:00:00:02")]


class FakeWorker:
    def __init__(self):
        self.items = []

    def put(self, *item):
        self.items.append(item)

    def sent(self):
        return [G.hexstr(f) for it in self.items if it[0] == "send" for f in it[1]]


class CoreTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.patch = mock.patch.object(G, "default_addr", return_value=None)
        self.patch.start()
        self.w = FakeWorker()
        self.core = A.Core(app_dir=self.tmp.name, worker=self.w, devices=DEVS)

    def tearDown(self):
        self.core.log.fh.close()
        self.patch.stop()
        self.tmp.cleanup()

    def connect(self):
        self.core.on_worker(("state", "connected", None))

    def test_picks_the_momentum_and_autoconnects(self):
        self.assertEqual(self.core.device_idx, 1)
        self.assertTrue(self.core.should_autoconnect())
        self.assertEqual(self.core.handle({"action": "connect"}), {"ok": True})
        self.assertEqual(self.w.items[-1], ("connect", "00:00:00:00:00:02"))
        self.assertNotIn("00:00:00:00:00:02", json.dumps(self.core.snapshot()))   # the page never sees addresses

    def test_settings_need_a_link(self):
        r = self.core.handle({"action": "noise", "mode": 0})
        self.assertFalse(r["ok"])
        self.assertEqual(self.w.sent(), [])

    def test_noise_eq_toggle_frames(self):
        self.connect()
        self.core.handle({"action": "noise", "mode": 2})
        self.core.handle({"action": "band", "changes": [[1, 30], [4, -20]]})
        self.core.handle({"action": "toggle", "key": "touch", "on": True})
        self.assertEqual(self.w.sent(), ["FF 03 00 02 04 95 1A 00 03 00", "FF 03 00 01 04 95 1A 04 00",
                                         "FF 03 00 02 04 95 10 01 01 1E", "FF 03 00 02 04 95 10 01 04 EC",
                                         "FF 03 00 01 04 95 16 06 00", "FF 03 00 00 04 95 16 07"])
        snap = self.core.snapshot()
        self.assertEqual(snap["eq"], [0, 30, 0, 0, -20])
        self.assertTrue(snap["toggles"]["touch"])

    def test_power_off_needs_confirm(self):
        self.connect()
        self.assertFalse(self.core.handle({"action": "poweroff"})["ok"])
        self.assertNotIn(("poweroff",), self.w.items)
        self.assertTrue(self.core.handle({"action": "poweroff", "confirmed": True})["ok"])
        self.assertIn(("poweroff",), self.w.items)

    def test_bad_input_never_raises_or_sends(self):
        self.connect()
        for a in ({"action": "band", "changes": [[7, 10]]}, {"action": "noise", "mode": 9},
                  {"action": "toggle", "key": "nope", "on": True}, {"action": "band", "changes": "x"},
                  {"action": "wind", "index": 5}, {"action": "zzz"}, [], {"action": "preset", "gains": [1, 2]}):
            self.assertFalse(self.core.handle(a)["ok"], a)
        self.assertEqual(self.w.sent(), [])

    def test_frames_update_state(self):
        self.connect()
        self.core.on_worker(("frame", G.Frame(3, 0x0495, 0x1301, bytes([0, 3, 0, 0x26, 0, 3]))))
        self.core.on_worker(("frame", G.Frame(3, 0x0495, 0x0703, bytes([57]))))
        self.core.on_worker(("frame", G.Frame(3, 0x0495, 0x1082, bytes([0, 0, 0x14, 0x14, 0xEC]))))
        s = self.core.snapshot()
        self.assertEqual((s["firmware"], s["battery"], s["eq"]), ("3.38.3", 57, [0, 0, 20, 20, -20]))

    def test_in_between_curves_are_skipped_after_a_write(self):
        self.connect()
        self.core.handle({"action": "preset", "gains": [0, 0, 0, 0, 0]})
        notif = lambda p: ("frame", G.Frame(3, 0x0495, 0x1082, bytes(p)))
        self.core.on_worker(notif([0, 0, 0x19, 0x0F, 0xEC]))          # headset half-way
        self.assertEqual(self.core.snapshot()["eq"], [0, 0, 0, 0, 0])
        self.core.on_worker(notif([0, 0, 0, 0, 0]))                   # arrived
        self.core.on_worker(notif([0x14, 0, 0, 0, 0]))                # later change from elsewhere
        self.assertEqual(self.core.snapshot()["eq"], [20, 0, 0, 0, 0])

    def test_presets_persist(self):
        self.core.handle({"action": "save_preset", "name": "Mine", "gains": [1, 2, 3, 4, 5]})
        self.assertFalse(self.core.handle({"action": "save_preset", "name": "Rock", "gains": [0] * 5})["ok"])
        with open(os.path.join(self.tmp.name, "settings.json"), encoding="utf-8") as f:
            self.assertEqual(json.load(f)["presets"], {"Mine": [1, 2, 3, 4, 5]})
        self.core.handle({"action": "delete_preset", "name": "Mine"})
        self.assertEqual(self.core.snapshot()["presets"]["custom"], [])

    def test_report_scrubs_addresses(self):
        self.core._on_log("note AA:BB:CC:DD:EE:FF here")
        text = self.core.handle({"action": "report"})["text"]
        self.assertNotIn("AA:BB:CC:DD:EE:FF", text)
        self.assertIn("XX:XX:XX:XX:XX:XX", text)


class ServerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.patch = mock.patch.object(G, "default_addr", return_value=None)
        self.patch.start()
        self.core = A.Core(app_dir=self.tmp.name, worker=FakeWorker(), devices=DEVS)
        self.srv = A.Server(self.core, "tok123")
        self.base = "http://127.0.0.1:%d" % self.srv.server_port
        threading.Thread(target=self.srv.serve_forever, daemon=True).start()

    def tearDown(self):
        self.srv.shutdown()
        self.srv.server_close()
        self.core.log.fh.close()
        self.patch.stop()
        self.tmp.cleanup()

    def code(self, url, data=None, headers=None):
        req = urllib.request.Request(url, data=data, headers=headers or {})
        try:
            with urllib.request.urlopen(req, timeout=3) as r:
                return r.status, r.read()
        except urllib.error.HTTPError as e:
            return e.code, b""

    def test_token_host_and_origin_are_checked(self):
        self.assertEqual(self.code(self.base + "/")[0], 403)
        self.assertEqual(self.code(self.base + "/?t=wrong")[0], 403)
        status, body = self.code(self.base + "/?t=tok123")
        self.assertEqual(status, 200)
        self.assertIn(b"M4 Control", body)
        self.assertEqual(self.code(self.base + "/?t=tok123", headers={"Host": "evil.example:80"})[0], 403)
        post = json.dumps({"action": "report"}).encode()
        self.assertEqual(self.code(self.base + "/api?t=tok123", post, {"Origin": "http://evil.example"})[0], 403)
        status, body = self.code(self.base + "/api?t=tok123", post, {"Content-Type": "application/json"})
        self.assertEqual(status, 200)
        self.assertTrue(json.loads(body)["ok"])


if __name__ == "__main__":
    unittest.main()
