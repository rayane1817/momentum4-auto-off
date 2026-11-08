"""Tests for the m4ctl command line (parsing only; no headset, no app)."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import m4ctl as C  # noqa: E402


class ParseTest(unittest.TestCase):
    def test_commands(self):
        self.assertEqual(C.parse(["status"]), ("status", None))
        self.assertEqual(C.parse(["noise", "Transparency"]), ("noise", 3))
        self.assertEqual(C.parse(["noise", "next"]), ("noise", "next"))
        self.assertEqual(C.parse(["level", "40"]), ("level", 40))
        self.assertEqual(C.parse(["eq", "prev"]), ("eq", -1))
        self.assertEqual(C.parse(["eq", "Hip-Hop"]), ("eq", "Hip-Hop"))
        self.assertEqual(C.parse(["prompts", "tones"]), ("prompts", 1))

    def test_bad_usage(self):
        for argv in ([], ["noise"], ["noise", "loud"], ["level", "101"], ["level", "-1"], ["poweroff"],
                     ["prompts", "on"], ["battery", "x"]):
            with self.assertRaises(C.Usage, msg=argv):
                C.parse(argv)
        self.assertEqual(C.main(["poweroff"]), 2)

    def test_noise_cycle_follows_the_ui_order(self):
        self.assertEqual([C.next_noise(m) for m in (0, 1, 3, 2, None)], [1, 3, 2, 0, 0])


if __name__ == "__main__":
    unittest.main()
