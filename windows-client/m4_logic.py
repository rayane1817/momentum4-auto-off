"""MOMENTUM 4 Windows app: frames and decoding, no UI (unit-tested in test_m4_logic.py).

Every frame the app sends is built here. `req()` refuses anything that is not a documented,
in-range request (tools/m4_commands.py) or that the blocklist stops (gaia_client.blocked_reason).
The one exception is POWER_OFF (`0495:0605`), which the catalogue marks as never sent from the
PC CLI; the app sends it only from its button, after a confirm, like the Android app.
Frames and their order match the Android app (MainActivity, Presets) and Smart Control Plus.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gaia_client as G  # noqa: E402  (also puts ../tools on sys.path)
import m4_commands as M4  # noqa: E402

APP_VERSION = "0.1.0"
SENN = G.SENNHEISER

BANDS = ("63 Hz", "250 Hz", "1 kHz", "4 kHz", "8 kHz")
GAIN_MIN, GAIN_MAX = -60, 60                 # dB x 10, as on the wire and in the Android app

# Smart Control's 8 built-ins, dB x 10 per band (same table as Presets.java, verified vs capture 2)
BUILTIN_PRESETS = [
    ("Neutral", (0, 0, 0, 0, 0)), ("Rock", (0, 20, 25, 15, -20)), ("Pop", (0, -25, 0, 25, 0)),
    ("Dance", (35, 20, -15, 15, 30)), ("Hip-Hop", (30, 15, -15, 0, -15)),
    ("Classical", (-20, -15, 0, 35, 40)), ("Movie", (0, 0, 20, 20, -20)), ("Jazz", (-32, 0, 22, 22, 0)),
]

NOISE_MODES = ("Adaptive", "Custom", "Off")
ANTI_WIND = ("Off", "Auto", "Max")           # UI order; wire 0 off, 1 Max, 2 Auto (T17)

# Simple on/off settings: key -> (label, set cmd, get cmd, inverted). Inverted = wire 0 means on.
TOGGLES = [
    ("smart_pause", "Smart pause", 0x080C, 0x080D, False),
    ("low_latency", "Low latency", 0x0817, 0x0818, False),
    ("auto_answer", "Auto-answer calls", 0x080A, 0x080B, False),
    ("comfort_call", "Comfort call", 0x0814, 0x0815, False),
    ("on_head", "On-head detection", 0x0400, 0x0401, False),
    ("touch", "Touch controls", 0x1606, 0x1607, True),
    ("hires", "Hi-Res audio (24-bit/96 kHz)", 0x0405, 0x0406, True),
    ("bass", "Bass boost", 0x1008, 0x1009, False),
]
TOGGLE_BY_KEY = {t[0]: t for t in TOGGLES}

# Read at connect, same list as MainActivity.readAllConfig() plus the timer.
READ_AT_CONNECT = [0x1A05, 0x1A01, 0x1A03, 0x080D, 0x080B, 0x0818, 0x0815, 0x1607, 0x0806, 0x0401,
                   0x0406, 0x1009, 0x0804, 0x0603, 0x1206, 0x1201, 0x1200]
NOTIFY_FEATURES = (0x0D, 0x08, 0x04)         # noise, EQ, audio/device (as the official app)


def anti_wind_swap(v):
    """UI index (Off, Auto, Max) <-> wire value (0, 2, 1). Its own inverse."""
    return 2 if v == 1 else 1 if v == 2 else v


def clamp_gain(g):
    return max(GAIN_MIN, min(GAIN_MAX, int(g)))


def u8(v):
    return v & 0xFF


def s8(b):
    return b - 256 if b > 127 else b


class Refused(ValueError):
    pass


def req(cmd, *payload):
    """A documented, in-range, not-blocked Sennheiser request frame, or Refused."""
    data = bytes(u8(p) for p in payload)
    frame = G.build(SENN, cmd, data)
    reason = M4.documented_reason(SENN, cmd, data) or G.blocked_reason(frame) or G.single_frame_problem(frame)
    if reason:
        raise Refused("refused %04X [%s]: %s" % (cmd, G.hexstr(data), reason))
    return frame


# The single exception to req(): the true power-off, button only, confirm first.
POWER_OFF = G.build(SENN, 0x0605)


def connect_frames():
    """Register for notifications, then read everything the UI shows."""
    f = [req(0x0007, feat) for feat in NOTIFY_FEATURES]
    f += [req(c) for c in READ_AT_CONNECT]
    f.append(req(0x1003, 0))                 # all 5 EQ gains in one reply
    f.append(req(0x0601, 0))                 # firmware auto-off timer (shown, not changed)
    return f


def noise_frames(mode, level):
    """mode index into NOISE_MODES. Same frames and order as Smart Control Plus (capture 2)."""
    if mode == 0:
        return [req(0x1A04, 1), req(0x1A00, 3, 1)]
    if mode == 1:
        return [req(0x1A00, 3, 0), req(0x1A04, 1), req(0x1A02, max(0, min(100, int(level))))]
    return [req(0x1A00, 3, 0), req(0x1A04, 0)]


def level_frames(level):
    return [req(0x1A02, max(0, min(100, int(level)))), req(0x1A03)]


def anti_wind_frame(ui_idx):
    return req(0x1A00, 1, anti_wind_swap(ui_idx))


def eq_band_frame(band, gain10):
    return req(0x1001, band, clamp_gain(gain10))


def eq_curve_frames(gains10):
    """Five band writes in band order, as Smart Control applies a preset (no preset id exists)."""
    return [eq_band_frame(b, gains10[b]) for b in range(5)]


def toggle_frames(key, on):
    _key, _label, set_cmd, get_cmd, inverted = TOGGLE_BY_KEY[key]
    wire = (0 if on else 1) if inverted else (1 if on else 0)
    return [req(set_cmd, wire), req(get_cmd)]


def speech_frames(on):
    """Speech clarity: 0803 u16 00 02 on / 00 01 normal. Refused (error 83) while Hi-Res is on (T39)."""
    return [req(0x0803, 0, 2 if on else 1), req(0x0804)]


def call_transparency_frames(level):
    return [req(0x0805, max(0, min(4, int(level)))), req(0x0806)]


def battery_frame():
    return req(0x0603)


def preset_name(gains10, custom=None):
    """Name of the preset with exactly this curve, else None."""
    g = tuple(gains10)
    for name, curve in BUILTIN_PRESETS:
        if curve == g:
            return name
    for name, curve in (custom or {}).items():
        if tuple(curve) == g:
            return name
    return None


def decode(f):
    """Turn a reply or notification into UI state changes (dict). Error replies give {"error": code}.
    Mirrors MainActivity.onFrame()."""
    if f.vendor != SENN:
        return {}
    p = f.payload
    b0 = p[0] if p else None
    base = f.base
    if f.type == 3:
        return {"error": (base, b0)}
    if f.type == 0:
        return {}
    out = {}
    if base == 0x0603 and b0 is not None:
        out["battery"] = b0
    elif base == 0x1206 and p:
        out["model"] = bytes(p).split(b"\0")[0].decode("ascii", "replace").strip()
    elif base == 0x1201 and len(p) >= 6:
        out["firmware"] = "%d.%d.%d" % ((p[0] << 8) | p[1], (p[2] << 8) | p[3], (p[4] << 8) | p[5])
    elif base == 0x1200 and p:
        out["hardware"] = str((p[0] << 8) | p[1]) if len(p) >= 2 else str(b0)
    elif base == 0x1A05 and b0 is not None:
        out["anc_on"] = b0 == 1
    elif base == 0x1A01:
        for i in range(0, len(p) - 1, 2):    # [mode, state] pairs: 1 anti-wind, 3 adaptive
            mode, st = p[i], p[i + 1]
            if mode == 3:
                out["adaptive_on"] = st == 1
            if mode == 1 and st <= 2:
                out["anti_wind"] = anti_wind_swap(st)
    elif base == 0x1A03 and b0 is not None and b0 <= 100:
        out["level"] = b0
    elif base == 0x0806 and b0 is not None and b0 <= 4:
        out["call_transparency"] = b0
    elif base == 0x0804 and len(p) >= 2:
        out["speech"] = ((p[0] << 8) | p[1]) == 2
    elif base == 0x0601 and len(p) >= 3 and p[0] == 0:
        out["timer"] = (p[1] << 8) | p[2]
    elif (base == 0x1003 and f.type == 2 and len(p) >= 5) or (base == 0x1002 and f.type == 1 and len(p) >= 5):
        out["eq"] = tuple(clamp_gain(s8(b)) for b in p[:5])   # 1082 notif carries all 5 bands
    elif base == 0x0800 and f.type == 1 and b0 is not None:   # 0880 notif
        out["streaming"] = b0 != 0xFF                          # FF = no audio stream (docs/protocol.md)
    else:
        for key, _label, _set, get_cmd, inverted in TOGGLES:
            if base == get_cmd and b0 is not None:
                out[key] = (b0 == 0) if inverted else (b0 == 1)
                break
    return out


def noise_mode_index(anc_on, adaptive_on):
    return 2 if not anc_on else 0 if adaptive_on else 1


# ------------------------------------------------------------ paired devices (Windows)

def paired_devices():
    """[(name, "AA:BB:CC:DD:EE:FF")] of the Bluetooth devices paired with this PC, read from the
    registry (no admin needed). Empty list off Windows or on any error."""
    try:
        import winreg
    except ImportError:
        return []
    out = []
    path = r"SYSTEM\CurrentControlSet\Services\BTHPORT\Parameters\Devices"
    try:
        root = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, path)
    except OSError:
        return []
    i = 0
    while True:
        try:
            sub = winreg.EnumKey(root, i)
        except OSError:
            break
        i += 1
        addr = format_addr(sub)
        if not addr:
            continue
        try:
            raw, _t = winreg.QueryValueEx(winreg.OpenKey(root, sub), "Name")
            name = bytes(raw).split(b"\0")[0].decode("utf-8", "replace").strip()
        except OSError:
            name = ""
        out.append((name or "Unnamed device", addr))
    return out


def format_addr(hex12):
    h = hex12.strip().replace(":", "").upper()
    if len(h) != 12 or any(c not in "0123456789ABCDEF" for c in h):
        return None
    return ":".join(h[i:i + 2] for i in range(0, 12, 2))


def pick_default(devices, saved_addr=None):
    """Index of the device to preselect: the saved one, else the first MOMENTUM, else 0 / None."""
    if not devices:
        return None
    for i, (_n, a) in enumerate(devices):
        if saved_addr and a.upper() == saved_addr.upper():
            return i
    for i, (n, _a) in enumerate(devices):
        if "MOMENTUM" in n.upper():
            return i
    return 0
