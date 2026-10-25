#!/usr/bin/env python3
"""Blocklist parity (plan Phase 3.6).

Extracts the rules of `Gaia.blockedReason` (Android app) and `blocked_reason`
(windows-client/gaia_client.py), evaluates both for every command id of
vendors 0x0495 and 0x001D, and checks:
  - both block 0040, feature 1, 1403, 1405, 1406, 0607, 1500, 0609-06FF, Qualcomm feature 6;
  - the PC client is at least as strict as the app (it may block more).
Run: python tools/blocklist_parity_test.py   (or via unittest discover)
"""
import os
import re
import sys
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
JAVA = os.path.join(ROOT, "android", "gaia-test", "app", "src", "main", "java", "be", "ryane", "gaiatest", "Gaia.java")
PY = os.path.join(ROOT, "windows-client", "gaia_client.py")
SENNHEISER, QUALCOMM = 0x0495, 0x001D

REQUIRED = ([(SENNHEISER, 0x0040), (SENNHEISER, 0x1403), (SENNHEISER, 0x1405), (SENNHEISER, 0x1406), (SENNHEISER, 0x0607), (SENNHEISER, 0x1500)]
            + [(SENNHEISER, c) for c in range(0x0200, 0x0400)]
            + [(SENNHEISER, c) for c in range(0x0609, 0x0700)]
            + [(QUALCOMM, c) for c in range(0x0C00, 0x0E00)])


def _body(src, header_re):
    m = re.search(header_re, src)
    if not m:
        raise AssertionError("blocklist function not found")
    return src[m.end():]


def java_rules():
    """Translate Gaia.blockedReason's Java body into a Python predicate."""
    with open(JAVA, encoding="utf-8") as fh:
        src = fh.read()
    body = _body(src, r"static String blockedReason\(byte\[\] frame\)\s*\{")
    py, depth = ["def f(vendor, cmd):", "    SENNHEISER, QUALCOMM = 0x0495, 0x001D"], 1
    for raw in body.splitlines():
        line = raw.strip()
        if not line or line.startswith("//"):
            continue
        if "frame.length" in line or line.startswith("int vendor"):
            continue
        if line == "}":
            depth -= 1
            if depth == 0:
                break
            continue
        line = re.sub(r"//.*$", "", line).strip()
        expr = lambda s: s.replace("&&", " and ").replace("||", " or ").replace("!", " not ").replace(" not =", "!=")
        m = re.match(r"int feature = (.+);$", line)
        if m:
            py.append("    " * depth + "feature = " + m.group(1))
            continue
        m = re.match(r"if \((.+)\) return (\".*\"|null);$", line)
        if m:
            py.append("    " * depth + "if %s: return %s" % (expr(m.group(1)), "None" if m.group(2) == "null" else m.group(2)))
            continue
        m = re.match(r"if \((.+)\) \{$", line)
        if m:
            py.append("    " * depth + "if %s:" % expr(m.group(1)))
            depth += 1
            continue
        if line == "return null;":
            py.append("    " * depth + "return None")
            continue
        raise AssertionError("cannot translate Java line: %r" % line)
    ns = {}
    exec("\n".join(py), ns)
    return ns["f"]


def py_rules():
    sys.path.insert(0, os.path.dirname(PY))
    import gaia_client
    return lambda vendor, cmd: gaia_client.blocked_reason(gaia_client.build(vendor, cmd))


def blocked_set(rule):
    return {(v, c) for v in (SENNHEISER, QUALCOMM) for c in range(0x10000) if rule(v, c)}


class TestBlocklistParity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java = blocked_set(java_rules())
        cls.py = blocked_set(py_rules())

    def test_required_in_app(self):
        missing = [x for x in REQUIRED if x not in self.java]
        self.assertEqual(missing, [], "app blocklist misses %s" % ["%04X:%04X" % m for m in missing[:5]])

    def test_required_in_pc(self):
        missing = [x for x in REQUIRED if x not in self.py]
        self.assertEqual(missing, [], "PC blocklist misses %s" % ["%04X:%04X" % m for m in missing[:5]])

    def test_pc_at_least_as_strict(self):
        only_app = sorted(self.java - self.py)
        self.assertEqual(only_app, [], "blocked by app but not by PC: %s" % ["%04X:%04X" % m for m in only_app[:5]])

    def test_power_off_and_config_not_blocked(self):
        for c in (0x0605, 0x0601, 0x1A00, 0x1A04, 0x0805, 0x1001, 0x0007):
            self.assertNotIn((SENNHEISER, c), self.java)
            self.assertNotIn((SENNHEISER, c), self.py)


if __name__ == "__main__":
    r = TestBlocklistParity
    r.setUpClass()
    extra = sorted(r.py - r.java)
    print("app blocks %d ids, PC blocks %d ids; PC-only: %s" % (
        len(r.java), len(r.py), ", ".join("%04X:%04X" % x for x in extra[:20]) or "none"))
    unittest.main(argv=[sys.argv[0], "-v"])
