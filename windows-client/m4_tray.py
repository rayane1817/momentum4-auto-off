"""Tray icon for M4 Control: the Windows counterpart of the Android widgets and Quick Settings tiles.

Left-click the icon for a small panel (battery, noise Adaptive / Custom / Transparency / Off,
previous / next EQ preset), right-click for the same as a menu, double-click for the window. Like
the two Android widgets. m4_app.Core does the sending (through the open link, or connect, send,
disconnect). This file is only the icon, the panel and the menu: plain ctypes, no packages.

The icon is drawn here from the Android tile vectors (ic_tile_anc*.xml), white on a dark taskbar,
near-black on a light one, so it changes with the noise mode like the tile does.
"""

import math
import os
import re
import struct
import sys
import threading

# ------------------------------------------------------------ icon drawing (no Windows needed)

EAR = "M8,9a4,4 0,1 1,8 0c0,2.5 -2,3 -2.5,5s-1,4 -3,4a2.5,2.5 0,0 1,-2.5 -2.5"
EAR_LEFT = "M6,9a4,4 0,1 1,8 0c0,2.5 -2,3 -2.5,5s-1,4 -3,4a2.5,2.5 0,0 1,-2.5 -2.5"
ARCS = "M18.5,6.5a7,7 0,0 1,0 7M21,4a10.5,10.5 0,0 1,0 12"
EAR_RIGHT = "M10,9a4,4 0,1 1,8 0c0,2.5 -2,3 -2.5,5s-1,4 -3,4a2.5,2.5 0,0 1,-2.5 -2.5"
ARCS_IN = "M5.5,6.5a7,7 0,0 0,0 7M3,4a10.5,10.5 0,0 0,0 12"
# mode -> (stroked paths, filled rects) on the 24-unit grid of the Android vectors
GLYPHS = {
    0: ([EAR, ARCS], []),                                   # Adaptive: ear and sound arcs
    1: ([EAR_LEFT, "M19,4v16"], [(16.5, 10, 21.5, 13)]),    # Custom: ear and a fader
    2: ([EAR], []),                                         # Off: ear only
    3: ([EAR_RIGHT, ARCS_IN], []),                          # Transparency: sound coming in
}
_TOK = re.compile(r"[MmLlHhVvCcSsAaZz]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?")


def _arc(x1, y1, rx, ry, phi, large, sweep, x2, y2, n):
    """Points of an SVG arc (endpoint form, SVG spec F.6.5), excluding the start point."""
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    rx, ry = abs(rx), abs(ry)
    c, s = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    xp, yp = c * dx + s * dy, -s * dx + c * dy
    lam = xp * xp / (rx * rx) + yp * yp / (ry * ry)
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp
    den = rx * rx * yp * yp + ry * ry * xp * xp
    co = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large == sweep:
        co = -co
    cxp, cyp = co * rx * yp / ry, -co * ry * xp / rx
    cx, cy = c * cxp - s * cyp + (x1 + x2) / 2, s * cxp + c * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        return math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)

    ux, uy = (xp - cxp) / rx, (yp - cyp) / ry
    t1 = ang(1, 0, ux, uy)
    dt = ang(ux, uy, (-xp - cxp) / rx, (-yp - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    out = []
    for k in range(1, n + 1):
        t = t1 + dt * k / n
        px, py = rx * math.cos(t), ry * math.sin(t)
        out.append((c * px - s * py + cx, s * px + c * py + cy))
    return out


def _cubic(p0, p1, p2, p3, n):
    out = []
    for k in range(1, n + 1):
        t = k / n
        u = 1 - t
        out.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                    u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
    return out


def path_polylines(d, n=12):
    """The SVG path commands the tile icons use (M L H V C S A Z, both cases) as polylines."""
    toks, i = _TOK.findall(d), 0
    cmd, x, y, sx, sy, last_c = None, 0.0, 0.0, 0.0, 0.0, None
    lines, cur = [], None

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
            if cmd in "Zz":
                if cur:
                    cur.append(cur[0])
                x, y, last_c = sx, sy, None
                continue
        rel, c = cmd.islower(), cmd.upper()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if c == "M":
            x, y = ox + num(), oy + num()
            cur = [(x, y)]
            lines.append(cur)
            sx, sy = x, y
            cmd = "l" if rel else "L"
        elif c == "L":
            x, y = ox + num(), oy + num()
            cur.append((x, y))
        elif c == "H":
            x = ox + num()
            cur.append((x, y))
        elif c == "V":
            y = oy + num()
            cur.append((x, y))
        elif c in "CS":
            if c == "C":
                p1 = (ox + num(), oy + num())
            else:
                p1 = (2 * x - last_c[0], 2 * y - last_c[1]) if last_c else (x, y)
            p2 = (ox + num(), oy + num())
            p3 = (ox + num(), oy + num())
            cur.extend(_cubic((x, y), p1, p2, p3, n))
            x, y = p3
            last_c = p2
            continue
        elif c == "A":
            rx, ry, phi, large, sweep = num(), num(), num(), num(), num()
            ex, ey = ox + num(), oy + num()
            cur.extend(_arc(x, y, rx, ry, phi, int(large), int(sweep), ex, ey, n))
            x, y = ex, ey
        last_c = None
    return lines


def _seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    ll = vx * vx + vy * vy
    t = 0.0 if ll == 0 else max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / ll))
    dx, dy = px - (ax + t * vx), py - (ay + t * vy)
    return math.sqrt(dx * dx + dy * dy)


def render(mode, size, rgb, alpha=1.0):
    """size x size coverage of the tile glyph for `mode` (0 Adaptive, 1 Custom, 2 Off, None
    unknown = the Off ear, dimmed by the caller). Returns rows of (r, g, b, a), top row first."""
    paths, rects = GLYPHS[mode if mode in GLYPHS else 2]
    scale = size / 24.0
    half = max(1.0, 2.0 * scale) / 2                 # 2-unit stroke, at least 1 px
    segs = []
    for d in paths:
        for line in path_polylines(d):
            segs += [(a[0] * scale, a[1] * scale, b[0] * scale, b[1] * scale) for a, b in zip(line, line[1:])]
    rows = []
    for py in range(size):
        row = []
        for px in range(size):
            cx, cy = px + 0.5, py + 0.5
            d = min(_seg_dist(cx, cy, *s) for s in segs) if segs else 99
            cov = max(0.0, min(1.0, half - d + 0.5))
            for x0, y0, x1, y1 in rects:
                ox = max(0.0, min(px + 1, x1 * scale) - max(px, x0 * scale))
                oy = max(0.0, min(py + 1, y1 * scale) - max(py, y0 * scale))
                cov = max(cov, ox * oy)
            row.append((rgb[0], rgb[1], rgb[2], int(round(255 * cov * alpha))))
        rows.append(row)
    return rows


def icon_resource(rows):
    """Bytes for CreateIconFromResourceEx: BITMAPINFOHEADER, 32-bit BGRA bottom-up, empty AND mask."""
    size = len(rows)
    head = struct.pack("<IiiHHIIiiII", 40, size, size * 2, 1, 32, 0, 0, 0, 0, 0, 0)
    xor = b"".join(bytes((b, g, r, a)) for row in reversed(rows) for (r, g, b, a) in row)
    mask = b"\0" * (((size + 31) // 32) * 4 * size)
    return head + xor + mask


# ----------------------------------------------------------------- start with Windows

RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
RUN_NAME = "M4 Control"


def autostart_command(script):
    """The exe, or pythonw (no console) running the script, in tray-only mode."""
    if getattr(sys, "frozen", False):
        return '"%s" --tray' % sys.executable
    exe = sys.executable
    folder, base = os.path.split(exe)
    if base.lower() == "python.exe" and os.path.isfile(os.path.join(folder, "pythonw.exe")):
        exe = os.path.join(folder, "pythonw.exe")
    return '"%s" "%s" --tray' % (exe, os.path.abspath(script))


def autostart_enabled():
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            winreg.QueryValueEx(k, RUN_NAME)
        return True
    except (ImportError, OSError):
        return False


def set_autostart(on, script):
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
        if on:
            winreg.SetValueEx(k, RUN_NAME, 0, winreg.REG_SZ, autostart_command(script))
        else:
            try:
                winreg.DeleteValue(k, RUN_NAME)
            except FileNotFoundError:
                pass


def _personalize(value):
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER,
                            r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize") as k:
            return winreg.QueryValueEx(k, value)[0] == 1
    except (ImportError, OSError):
        return False


def taskbar_is_light():
    return _personalize("SystemUsesLightTheme")


def apps_are_light():
    return _personalize("AppsUseLightTheme")


def ico_file(sizes=(16, 20, 24, 32, 40, 48, 64, 128, 256)):
    """The app icon (.ico bytes) for the exe and shortcuts: the Adaptive glyph in copper on a
    rounded dark square (design v4 colours)."""
    entries = []
    for n in sizes:
        glyph = render(0, n, (0xD9, 0x8C, 0x55))
        r = n * 0.22
        rows = []
        for y in range(n):
            row = []
            for x in range(n):
                dx = max(r - (x + 0.5), (x + 0.5) - (n - r), 0)
                dy = max(r - (y + 0.5), (y + 0.5) - (n - r), 0)
                inside = max(0.0, min(1.0, r - math.hypot(dx, dy) + 0.5)) if (dx or dy) else 1.0
                ga = glyph[y][x][3] / 255
                c = [int(0x1C * (1 - ga) + v * ga) for v in (0xD9, 0x8C, 0x55)]
                row.append((c[0], c[1], c[2], int(255 * inside)))
            rows.append(row)
        entries.append((n, icon_resource(rows)))
    out = struct.pack("<HHH", 0, 1, len(entries))
    offset = 6 + 16 * len(entries)
    for n, data in entries:
        out += struct.pack("<BBBBHHII", n % 256, n % 256, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    return out + b"".join(d for _n, d in entries)


# ------------------------------------------------------------------ menu text (testable)

NOISE_NAMES = ("Adaptive", "Custom", "Off", "Transparency")   # m4_logic.NOISE_MODES
NOISE_ORDER = (0, 1, 3, 2)
ID_OPEN, ID_NOISE, ID_PREV, ID_NEXT, ID_AUTOSTART, ID_QUIT = 1, 10, 20, 21, 30, 40


def tooltip(st):
    """At most 127 characters (the Windows limit)."""
    noise = NOISE_NAMES[st["noise"]] if st.get("noise") in (0, 1, 2, 3) else "?"
    lines = ["M4 Control", "Noise: %s, EQ: %s" % (noise, st.get("eq") or "custom curve")]
    if st.get("busy"):
        lines.append("Sending...")
    elif st.get("battery") is not None:
        lines.append("Battery %d %%%s" % (st["battery"], ", charging" if st.get("charging") else ""))
    return "\n".join(lines)[:127]


def menu_items(st):
    """[(id or None, text, flags)] with flags from {"sep", "grey", "check", "radio", "default"}."""
    busy = bool(st.get("busy"))
    off = {"grey"} if busy else set()
    items = [(ID_OPEN, "Open M4 Control", {"default"}), (None, "", {"sep"}),
             (None, "Noise control" + (": sending..." if busy else ""), {"grey"})]
    for i in NOISE_ORDER:
        items.append((ID_NOISE + i, NOISE_NAMES[i], off | ({"check", "radio"} if st.get("noise") == i else set())))
    items += [(None, "", {"sep"}), (None, "EQ: %s" % (st.get("eq") or "custom curve"), {"grey"}),
              (ID_PREV, "Previous preset", set(off)), (ID_NEXT, "Next preset", set(off)),
              (None, "", {"sep"}),
              (ID_AUTOSTART, "Start with Windows", {"check"} if st.get("autostart") else set()),
              (ID_QUIT, "Quit M4 Control", set())]
    return items


# ------------------------------------------------------------- quick panel layout (testable)

PANEL_W, PANEL_H = 328, 252
PANEL_LABELS = {0: "Adaptive", 1: "Custom", 3: "Transparent", 2: "Off"}
# design v4 tokens (ui/index.html :root), (r, g, b)
DARK = dict(bg=(0x1C, 0x1C, 0x1C), btn=(0x2C, 0x2C, 0x2C), hover=(0x3A, 0x3A, 0x3A), ink=(0xF2, 0xF2, 0xF2),
            ink2=(0x9E, 0x9E, 0x9E), sel=(0xF2, 0xF2, 0xF2), onsel=(0, 0, 0), copper=(0xD9, 0x8C, 0x55),
            rule=(0x2A, 0x2A, 0x2A))
LIGHT = dict(bg=(0xFF, 0xFF, 0xFF), btn=(0xE6, 0xE6, 0xE6), hover=(0xD6, 0xD6, 0xD6), ink=(0x0A, 0x0A, 0x0A),
             ink2=(0x5E, 0x5E, 0x5E), sel=(0x0A, 0x0A, 0x0A), onsel=(0xFF, 0xFF, 0xFF), copper=(0x9A, 0x4E, 0x1C),
             rule=(0xE0, 0xE0, 0xE0))


def panel_status(st):
    if st.get("busy"):
        return "Sending..."
    if st.get("linked"):
        return "Connected"
    return "Not connected. A tap connects, sends and lets go"


def panel_layout(st):
    """Everything the panel draws, in logical pixels (96 dpi): dicts with kind, rect (x, y, w, h),
    cmd (None = not clickable) and text. Pure, so it is unit-tested."""
    pad, w = 16, PANEL_W
    batt = "--%" if st.get("battery") is None else "%d%%" % st["battery"]
    items = [dict(kind="title", rect=(pad, 14, 200, 28), cmd=None, text="MOMENTUM 4"),
             dict(kind="battery", rect=(w - pad - 110, 14, 110, 28), cmd=None,
                  text=batt + (" ⚡" if st.get("charging") else "")),
             dict(kind="status", rect=(pad, 42, w - 2 * pad, 18), cmd=None, text=panel_status(st))]
    col = (w - 2 * pad) / 4
    for k, mode in enumerate(NOISE_ORDER):
        cx = pad + col * k + col / 2
        items.append(dict(kind="mode", rect=(int(cx - 26), 74, 52, 52), cmd=ID_NOISE + mode, mode=mode,
                          sel=st.get("noise") == mode, text=PANEL_LABELS[mode], grey=bool(st.get("busy"))))
    items.append(dict(kind="rule", rect=(pad, 160, w - 2 * pad, 1), cmd=None, text=""))
    items += [dict(kind="step", rect=(pad, 172, 40, 40), cmd=ID_PREV, text="‹", grey=bool(st.get("busy"))),
              dict(kind="preset", rect=(pad + 48, 172, w - 2 * pad - 96, 40), cmd=None,
                   text=st.get("eq") or "Custom curve"),
              dict(kind="step", rect=(w - pad - 40, 172, 40, 40), cmd=ID_NEXT, text="›", grey=bool(st.get("busy"))),
              dict(kind="link", rect=(pad - 6, 218, 150, 26), cmd=ID_OPEN, text="Open M4 Control")]
    return items


def hit(items, x, y):
    for it in items:
        rx, ry, rw, rh = it["rect"]
        if it["cmd"] and not it.get("grey") and rx <= x < rx + rw and ry <= y < ry + rh + (22 if it["kind"] == "mode" else 0):
            return it["cmd"]
    return None


def panel_origin(cursor, work, size):
    """Top-left of the panel: next to the taskbar the cursor is on, inside the work area."""
    (cx, cy), (l, t, r, b), (w, h) = cursor, work, size
    gap = 12
    x = min(max(cx - w // 2, l + gap), r - w - gap)
    y = min(max(cy - h // 2, t + gap), b - h - gap)
    if cy >= b:
        y = b - h - gap
    elif cy <= t:
        y = t + gap
    if cx >= r:
        x = r - w - gap
    elif cx <= l:
        x = l + gap
    return x, y


# ------------------------------------------------------------------- the Win32 panel

class Panel:
    """The quick panel: a borderless popup above the tray, GDI+ shapes and GDI text, closes when
    it loses focus or on Esc. Lives on the tray thread; built by Tray._setup()."""

    WM_PAINT, WM_ERASEBKGND, WM_ACTIVATE, WM_KEYDOWN = 0x000F, 0x0014, 0x0006, 0x0100
    WM_MOUSEMOVE, WM_LBUTTONUP, WM_SETCURSOR = 0x0200, 0x0202, 0x0020

    def __init__(self, tray, hinst):
        import ctypes
        from ctypes import wintypes as W
        self.tray, self.ctypes, self.W = tray, ctypes, W
        u = self.u = tray.u
        g = self.gdi = ctypes.WinDLL("gdi32")
        gp = self.gp = ctypes.WinDLL("gdiplus")
        vp, fl = ctypes.c_void_p, ctypes.c_float

        class StartupInput(ctypes.Structure):
            _fields_ = [("version", ctypes.c_uint32), ("cb", vp), ("nothread", W.BOOL), ("nocodecs", W.BOOL)]

        class PAINTSTRUCT(ctypes.Structure):
            _fields_ = [("hdc", W.HDC), ("fErase", W.BOOL), ("rcPaint", W.RECT), ("fRestore", W.BOOL),
                        ("fIncUpdate", W.BOOL), ("rgbReserved", ctypes.c_byte * 32)]

        class MONITORINFO(ctypes.Structure):
            _fields_ = [("cbSize", W.DWORD), ("rcMonitor", W.RECT), ("rcWork", W.RECT), ("dwFlags", W.DWORD)]

        self.PAINTSTRUCT, self.MONITORINFO = PAINTSTRUCT, MONITORINFO
        for name, args, res in (
                ("GdiplusStartup", [ctypes.POINTER(ctypes.c_size_t), ctypes.POINTER(StartupInput), vp], ctypes.c_int),
                ("GdipCreateFromHDC", [W.HDC, ctypes.POINTER(vp)], ctypes.c_int),
                ("GdipSetSmoothingMode", [vp, ctypes.c_int], ctypes.c_int),
                ("GdipSetPixelOffsetMode", [vp, ctypes.c_int], ctypes.c_int),
                ("GdipCreateSolidFill", [ctypes.c_uint32, ctypes.POINTER(vp)], ctypes.c_int),
                ("GdipCreatePen1", [ctypes.c_uint32, fl, ctypes.c_int, ctypes.POINTER(vp)], ctypes.c_int),
                ("GdipFillEllipse", [vp, vp, fl, fl, fl, fl], ctypes.c_int),
                ("GdipCreatePath", [ctypes.c_int, ctypes.POINTER(vp)], ctypes.c_int),
                ("GdipAddPathArc", [vp, fl, fl, fl, fl, fl, fl], ctypes.c_int),
                ("GdipClosePathFigure", [vp], ctypes.c_int),
                ("GdipFillPath", [vp, vp, vp], ctypes.c_int),
                ("GdipDrawPath", [vp, vp, vp], ctypes.c_int),
                ("GdipCreateBitmapFromScan0", [ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_char_p,
                                               ctypes.POINTER(vp)], ctypes.c_int),
                ("GdipDrawImageRectI", [vp, vp, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int], ctypes.c_int),
                ("GdipDeletePath", [vp], ctypes.c_int), ("GdipDeleteBrush", [vp], ctypes.c_int),
                ("GdipDeletePen", [vp], ctypes.c_int), ("GdipDeleteGraphics", [vp], ctypes.c_int)):
            fn = getattr(gp, name)
            fn.argtypes, fn.restype = args, res
        g.CreateCompatibleDC.argtypes = [W.HDC]
        g.CreateCompatibleDC.restype = W.HDC
        g.CreateCompatibleBitmap.argtypes = [W.HDC, ctypes.c_int, ctypes.c_int]
        g.CreateCompatibleBitmap.restype = W.HBITMAP
        g.SelectObject.argtypes = [W.HDC, W.HGDIOBJ]
        g.SelectObject.restype = W.HGDIOBJ
        g.DeleteObject.argtypes = [W.HGDIOBJ]
        g.DeleteDC.argtypes = [W.HDC]
        g.BitBlt.argtypes = [W.HDC, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, W.HDC, ctypes.c_int,
                             ctypes.c_int, W.DWORD]
        g.CreateSolidBrush.argtypes = [W.COLORREF]
        g.CreateSolidBrush.restype = W.HBRUSH
        g.CreateFontW.argtypes = [ctypes.c_int] * 5 + [W.DWORD] * 8 + [W.LPCWSTR]
        g.CreateFontW.restype = W.HFONT
        g.SetTextColor.argtypes = [W.HDC, W.COLORREF]
        g.SetBkMode.argtypes = [W.HDC, ctypes.c_int]
        g.CreateRoundRectRgn.restype = W.HRGN
        u.FillRect.argtypes = [W.HDC, ctypes.POINTER(W.RECT), W.HBRUSH]
        u.DrawTextW.argtypes = [W.HDC, W.LPCWSTR, ctypes.c_int, ctypes.POINTER(W.RECT), W.UINT]
        u.BeginPaint.argtypes = [W.HWND, ctypes.POINTER(PAINTSTRUCT)]
        u.BeginPaint.restype = W.HDC
        u.EndPaint.argtypes = [W.HWND, ctypes.POINTER(PAINTSTRUCT)]
        u.InvalidateRect.argtypes = [W.HWND, vp, W.BOOL]
        u.SetWindowPos.argtypes = [W.HWND, W.HWND, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, W.UINT]
        u.ShowWindow.argtypes = [W.HWND, ctypes.c_int]
        u.SetWindowRgn.argtypes = [W.HWND, W.HRGN, W.BOOL]
        u.MonitorFromPoint.argtypes = [W.POINT, W.DWORD]
        u.MonitorFromPoint.restype = W.HMONITOR
        u.GetMonitorInfoW.argtypes = [W.HMONITOR, ctypes.POINTER(MONITORINFO)]
        u.LoadCursorW.argtypes = [W.HINSTANCE, vp]
        u.LoadCursorW.restype = W.HANDLE
        u.SetCursor.argtypes = [W.HANDLE]
        u.IsWindowVisible.argtypes = [W.HWND]

        token = ctypes.c_size_t()
        gp.GdiplusStartup(ctypes.byref(token), ctypes.byref(StartupInput(1, None, False, False)), None)
        self._proc = tray.WNDPROC(self._wndproc)
        wc = tray.WNDCLASSW(style=0x00020000, lpfnWndProc=self._proc, hInstance=hinst,   # CS_DROPSHADOW
                            lpszClassName="M4ControlPanel")
        u.RegisterClassW(ctypes.byref(wc))
        self.hwnd = u.CreateWindowExW(0x00000080 | 0x00000008, "M4ControlPanel", "M4 Control",  # TOOLWINDOW|TOPMOST
                                      0x80000000, 0, 0, 10, 10, None, None, hinst, None)    # WS_POPUP
        self.items, self.hover, self.scale, self.closed_at = [], None, 1.0, 0.0
        self._bitmaps = {}
        self.hand = u.LoadCursorW(None, ctypes.c_void_p(32649))
        self.arrow = u.LoadCursorW(None, ctypes.c_void_p(32512))

    def visible(self):
        return bool(self.u.IsWindowVisible(self.hwnd))

    def toggle(self):
        import time
        if self.visible():
            self.hide()
        elif time.monotonic() - self.closed_at > 0.3:          # the click that just closed it
            self.show()

    def show(self):
        u, W, c = self.u, self.W, self.ctypes
        try:
            dpi = u.GetDpiForSystem()
        except AttributeError:
            dpi = 96
        self.scale = dpi / 96.0
        w, h = int(PANEL_W * self.scale), int(PANEL_H * self.scale)
        pt = W.POINT()
        u.GetCursorPos(c.byref(pt))
        mi = self.MONITORINFO()
        mi.cbSize = c.sizeof(self.MONITORINFO)
        u.GetMonitorInfoW(u.MonitorFromPoint(pt, 2), c.byref(mi))          # MONITOR_DEFAULTTONEAREST
        wr = mi.rcWork
        x, y = panel_origin((pt.x, pt.y), (wr.left, wr.top, wr.right, wr.bottom), (w, h))
        r = int(16 * self.scale)
        u.SetWindowRgn(self.hwnd, self.gdi.CreateRoundRectRgn(0, 0, w + 1, h + 1, r, r), True)
        u.SetWindowPos(self.hwnd, W.HWND(-1), x, y, w, h, 0x0040)          # HWND_TOPMOST, SWP_SHOWWINDOW
        u.SetForegroundWindow(self.hwnd)
        self.refresh()

    def hide(self):
        import time
        if self.visible():
            self.u.ShowWindow(self.hwnd, 0)
            self.closed_at = time.monotonic()
        self.hover = None

    def refresh(self):
        if self.visible():
            self.u.InvalidateRect(self.hwnd, None, False)

    # ---- drawing
    def _argb(self, rgb, a=255):
        return (a << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]

    def _cref(self, rgb):
        return rgb[0] | (rgb[1] << 8) | (rgb[2] << 16)

    def _glyph(self, mode, size, rgb):
        key = (mode, size, rgb)
        if key not in self._bitmaps:
            rows = render(mode, size, rgb)
            buf = self.ctypes.create_string_buffer(b"".join(bytes((b, g, r, a)) for row in rows for (r, g, b, a) in row))
            bmp = self.ctypes.c_void_p()
            self.gp.GdipCreateBitmapFromScan0(size, size, size * 4, 0x26200A, buf, self.ctypes.byref(bmp))  # 32bppARGB
            self._bitmaps[key] = (bmp, buf)
        return self._bitmaps[key][0]

    def _round_rect(self, gfx, x, y, w, h, r, brush=None, pen=None):
        c, gp = self.ctypes, self.gp
        path = c.c_void_p()
        gp.GdipCreatePath(0, c.byref(path))
        d = 2 * r
        gp.GdipAddPathArc(path, x, y, d, d, 180, 90)
        gp.GdipAddPathArc(path, x + w - d, y, d, d, 270, 90)
        gp.GdipAddPathArc(path, x + w - d, y + h - d, d, d, 0, 90)
        gp.GdipAddPathArc(path, x, y + h - d, d, d, 90, 90)
        gp.GdipClosePathFigure(path)
        if brush:
            gp.GdipFillPath(gfx, brush, path)
        if pen:
            gp.GdipDrawPath(gfx, pen, path)
        gp.GdipDeletePath(path)

    def _paint(self):
        c, W, u, g, gp = self.ctypes, self.W, self.u, self.gdi, self.gp
        st = self.tray._state()
        tok = LIGHT if apps_are_light() else DARK
        S = lambda v: int(round(v * self.scale))                            # noqa: E731
        self.items = panel_layout(st)
        ps = self.PAINTSTRUCT()
        hdc = u.BeginPaint(self.hwnd, c.byref(ps))
        w, h = S(PANEL_W), S(PANEL_H)
        mem = g.CreateCompatibleDC(hdc)
        bmp = g.CreateCompatibleBitmap(hdc, w, h)
        old = g.SelectObject(mem, bmp)
        bg = g.CreateSolidBrush(self._cref(tok["bg"]))
        u.FillRect(mem, c.byref(W.RECT(0, 0, w, h)), bg)
        g.DeleteObject(bg)

        gfx = c.c_void_p()
        gp.GdipCreateFromHDC(mem, c.byref(gfx))
        gp.GdipSetSmoothingMode(gfx, 4)                                   # anti-alias
        gp.GdipSetPixelOffsetMode(gfx, 4)

        def fill(rgb):
            b = c.c_void_p()
            gp.GdipCreateSolidFill(self._argb(rgb), c.byref(b))
            return b
        for it in self.items:
            x, y, iw, ih = [S(v) for v in it["rect"]]
            hov = self.hover is not None and it["cmd"] == self.hover and not it.get("grey")
            if it["kind"] == "mode":
                b = fill(tok["sel"] if it["sel"] else tok["hover"] if hov else tok["btn"])
                gp.GdipFillEllipse(gfx, b, x, y, iw, ih)
                gp.GdipDeleteBrush(b)
                gs = S(24)
                col = tok["onsel"] if it["sel"] else tok["ink2"] if it.get("grey") else tok["ink"]
                gp.GdipDrawImageRectI(gfx, self._glyph(it["mode"], gs, col), x + (iw - gs) // 2, y + (ih - gs) // 2, gs, gs)
            elif it["kind"] == "step":
                b = fill(tok["hover"] if hov else tok["btn"])
                gp.GdipFillEllipse(gfx, b, x, y, iw, ih)
                gp.GdipDeleteBrush(b)
            elif it["kind"] == "preset":
                pen = c.c_void_p()
                gp.GdipCreatePen1(self._argb(tok["ink2"]), S(1.5), 2, c.byref(pen))
                self._round_rect(gfx, x, y, iw, ih, ih / 2, pen=pen)
                gp.GdipDeletePen(pen)
            elif it["kind"] == "rule":
                b = fill(tok["rule"])
                self._round_rect(gfx, x, y, iw, max(1, ih), 0.5, brush=b)
                gp.GdipDeleteBrush(b)
        gp.GdipDeleteGraphics(gfx)

        g.SetBkMode(mem, 1)                                               # transparent
        fonts = {}

        def font(face, px, weight=400):
            k = (face, px, weight)
            if k not in fonts:
                fonts[k] = g.CreateFontW(-S(px), 0, 0, 0, weight, 0, 0, 0, 1, 0, 0, 5, 0, face)  # CLEARTYPE
            return fonts[k]
        for it in self.items:
            x, y, iw, ih = [S(v) for v in it["rect"]]
            k, text = it["kind"], it["text"]
            flags = 0x0004 | 0x0020 | 0x8000                                  # VCENTER | SINGLELINE | END_ELLIPSIS
            if k == "title":
                f, col = font("Sitka Display", 21), tok["ink"]
            elif k == "battery":
                f, col, flags = font("Cascadia Mono", 18), tok["ink"], flags | 0x0002   # RIGHT
            elif k == "status":
                f, col = font("Segoe UI", 12), tok["ink2"]
            elif k == "mode":
                f = font("Segoe UI", 12, 600 if it["sel"] else 400)
                col = tok["ink"] if it["sel"] else tok["ink2"]
                x, y, iw, ih, flags = x - S(14), y + ih + S(4), iw + S(28), S(18), flags | 0x0001  # CENTER
            elif k == "step":
                f, col, flags = font("Segoe UI", 22), tok["ink2"] if it.get("grey") else tok["ink"], flags | 0x0001
                y -= S(2)
            elif k == "preset":
                f, col, flags = font("Segoe UI", 15, 600), tok["ink"], flags | 0x0001
                x, iw = x + S(12), iw - S(24)
            elif k == "link":
                f, col = font("Segoe UI", 13, 600), tok["copper"]
                x += S(6)
            else:
                continue
            old_f = g.SelectObject(mem, f)
            g.SetTextColor(mem, self._cref(col))
            u.DrawTextW(mem, text, -1, c.byref(W.RECT(x, y, x + iw, y + ih)), flags)
            g.SelectObject(mem, old_f)
        g.BitBlt(hdc, 0, 0, w, h, mem, 0, 0, 0x00CC0020)                   # SRCCOPY
        g.SelectObject(mem, old)
        g.DeleteObject(bmp)
        g.DeleteDC(mem)
        for f in fonts.values():
            g.DeleteObject(f)
        u.EndPaint(self.hwnd, c.byref(ps))

    # ---- input
    def _xy(self, lp):
        c = self.ctypes
        return (c.c_short(lp & 0xFFFF).value / self.scale, c.c_short((lp >> 16) & 0xFFFF).value / self.scale)

    def _wndproc(self, hwnd, msg, wp, lp):
        try:
            if msg == self.WM_PAINT:
                self._paint()
                return 0
            if msg == self.WM_ERASEBKGND:
                return 1
            if msg == self.WM_ACTIVATE and (wp & 0xFFFF) == 0:              # WA_INACTIVE
                self.hide()
                return 0
            if msg == self.WM_KEYDOWN and wp == 0x1B:                        # Esc
                self.hide()
                return 0
            if msg == self.WM_MOUSEMOVE:
                cmd = hit(self.items, *self._xy(lp))
                self.u.SetCursor(self.hand if cmd else self.arrow)
                if cmd != self.hover:
                    self.hover = cmd
                    self.refresh()
                return 0
            if msg == self.WM_SETCURSOR:
                return 1 if self.hover else self.u.DefWindowProcW(hwnd, msg, wp, lp)
            if msg == self.WM_LBUTTONUP:
                cmd = hit(self.items, *self._xy(lp))
                if cmd == ID_OPEN:
                    self.hide()
                if cmd:
                    self.tray.on_command(cmd)
                    self.refresh()
                return 0
        except Exception:
            pass
        return self.u.DefWindowProcW(hwnd, msg, wp, lp)


# ------------------------------------------------------------------- the Win32 tray

class Tray(threading.Thread):
    """Owns a hidden window, the notification-area icon and its menu, on its own thread.
    get_state() -> dict (noise, eq, battery, busy, autostart); on_command(id) runs on this thread
    and must not block. refresh(), balloon() and stop() are safe from any thread."""

    WM_TRAY, WM_REFRESH, WM_BALLOON = 0x8000 + 1, 0x8000 + 2, 0x8000 + 3

    def __init__(self, get_state, on_command):
        super().__init__(daemon=True, name="m4-tray")
        self.get_state = get_state
        self.on_command = on_command
        self.ready = threading.Event()
        self.ok = False
        self.hwnd = None
        self._balloon = None
        self._icon = None
        self._icon_key = None
        self.panel = None
        self._lock = threading.Lock()

    # ---- any thread
    def refresh(self):
        self._post(self.WM_REFRESH)

    def balloon(self, title, text):
        with self._lock:
            self._balloon = (title[:63], text[:255])
        self._post(self.WM_BALLOON)

    def stop(self):
        self._post(0x0010)                                    # WM_CLOSE

    def _post(self, msg):
        if self.hwnd:
            self.u.PostMessageW(self.hwnd, msg, 0, 0)

    # ---- tray thread
    def run(self):
        try:
            self._setup()
        except Exception:                                     # no tray: the app still works
            self.ready.set()
            return
        self.ok = True
        self.ready.set()
        msg = self.W.MSG()
        while self.u.GetMessageW(self.ctypes.byref(msg), None, 0, 0) > 0:
            self.u.TranslateMessage(self.ctypes.byref(msg))
            self.u.DispatchMessageW(self.ctypes.byref(msg))

    def _setup(self):
        import ctypes
        from ctypes import wintypes as W
        self.ctypes, self.W = ctypes, W
        u = self.u = ctypes.WinDLL("user32", use_last_error=True)
        sh = self.sh = ctypes.WinDLL("shell32", use_last_error=True)
        k32 = ctypes.WinDLL("kernel32", use_last_error=True)
        try:
            ctypes.WinDLL("shcore").SetProcessDpiAwareness(2)  # crisp icon at 125 / 150 %
        except (OSError, AttributeError):
            pass
        LRESULT = ctypes.c_ssize_t
        self.WNDPROC = ctypes.WINFUNCTYPE(LRESULT, W.HWND, W.UINT, W.WPARAM, W.LPARAM)

        class WNDCLASSW(ctypes.Structure):
            _fields_ = [("style", W.UINT), ("lpfnWndProc", self.WNDPROC), ("cbClsExtra", ctypes.c_int),
                        ("cbWndExtra", ctypes.c_int), ("hInstance", W.HINSTANCE), ("hIcon", W.HICON),
                        ("hCursor", W.HANDLE), ("hbrBackground", W.HBRUSH), ("lpszMenuName", W.LPCWSTR),
                        ("lpszClassName", W.LPCWSTR)]

        class GUID(ctypes.Structure):
            _fields_ = [("a", W.DWORD), ("b", W.WORD), ("c", W.WORD), ("d", ctypes.c_ubyte * 8)]

        class NOTIFYICONDATAW(ctypes.Structure):
            _fields_ = [("cbSize", W.DWORD), ("hWnd", W.HWND), ("uID", W.UINT), ("uFlags", W.UINT),
                        ("uCallbackMessage", W.UINT), ("hIcon", W.HICON), ("szTip", W.WCHAR * 128),
                        ("dwState", W.DWORD), ("dwStateMask", W.DWORD), ("szInfo", W.WCHAR * 256),
                        ("uVersion", W.UINT), ("szInfoTitle", W.WCHAR * 64), ("dwInfoFlags", W.DWORD),
                        ("guidItem", GUID), ("hBalloonIcon", W.HICON)]

        self.NID, self.WNDCLASSW = NOTIFYICONDATAW, WNDCLASSW
        u.DefWindowProcW.argtypes = [W.HWND, W.UINT, W.WPARAM, W.LPARAM]
        u.DefWindowProcW.restype = LRESULT
        u.CreateWindowExW.argtypes = [W.DWORD, W.LPCWSTR, W.LPCWSTR, W.DWORD, ctypes.c_int, ctypes.c_int,
                                      ctypes.c_int, ctypes.c_int, W.HWND, W.HMENU, W.HINSTANCE, W.LPVOID]
        u.CreateWindowExW.restype = W.HWND
        u.RegisterClassW.argtypes = [ctypes.POINTER(WNDCLASSW)]
        u.PostMessageW.argtypes = [W.HWND, W.UINT, W.WPARAM, W.LPARAM]
        u.CreatePopupMenu.restype = W.HMENU
        u.AppendMenuW.argtypes = [W.HMENU, W.UINT, ctypes.c_size_t, W.LPCWSTR]
        u.SetMenuDefaultItem.argtypes = [W.HMENU, W.UINT, W.UINT]
        u.TrackPopupMenu.argtypes = [W.HMENU, W.UINT, ctypes.c_int, ctypes.c_int, ctypes.c_int, W.HWND, W.LPVOID]
        u.DestroyMenu.argtypes = [W.HMENU]
        u.SetForegroundWindow.argtypes = [W.HWND]
        u.DestroyWindow.argtypes = [W.HWND]
        u.DestroyIcon.argtypes = [W.HICON]
        u.CreateIconFromResourceEx.argtypes = [ctypes.c_char_p, W.DWORD, W.BOOL, W.DWORD, ctypes.c_int,
                                               ctypes.c_int, W.UINT]
        u.CreateIconFromResourceEx.restype = W.HICON
        u.GetMessageW.argtypes = [ctypes.POINTER(W.MSG), W.HWND, W.UINT, W.UINT]
        sh.Shell_NotifyIconW.argtypes = [W.DWORD, ctypes.POINTER(NOTIFYICONDATAW)]
        k32.GetModuleHandleW.restype = W.HMODULE

        self._proc = self.WNDPROC(self._wndproc)              # keep a reference
        hinst = k32.GetModuleHandleW(None)
        wc = WNDCLASSW(lpfnWndProc=self._proc, hInstance=hinst, lpszClassName="M4ControlTray")
        u.RegisterClassW(ctypes.byref(wc))
        self.taskbar_created = u.RegisterWindowMessageW("TaskbarCreated")
        self.hwnd = u.CreateWindowExW(0, "M4ControlTray", "M4 Control", 0, 0, 0, 0, 0, None, None, hinst, None)
        if not self.hwnd:
            raise OSError("CreateWindowExW failed")
        try:
            self.panel = Panel(self, hinst)
        except Exception:                                      # no panel: left-click shows the menu
            self.panel = None
        self._notify(0)                                        # NIM_ADD

    def _nid(self, flags):
        n = self.NID()
        n.cbSize = self.ctypes.sizeof(self.NID)
        n.hWnd, n.uID, n.uFlags = self.hwnd, 1, flags
        return n

    def _notify(self, op):
        """op 0 add, 1 modify: icon, tooltip, callback."""
        st = self._state()
        n = self._nid(0x01 | 0x02 | 0x04)                     # NIF_MESSAGE | NIF_ICON | NIF_TIP
        n.uCallbackMessage = self.WM_TRAY
        n.hIcon = self._make_icon(st)
        n.szTip = tooltip(st)
        if not self.sh.Shell_NotifyIconW(op, self.ctypes.byref(n)) and op == 1:
            self.sh.Shell_NotifyIconW(0, self.ctypes.byref(n))  # explorer lost it: add again

    def _state(self):
        try:
            return self.get_state() or {}
        except Exception:
            return {}

    def _make_icon(self, st):
        light = taskbar_is_light()
        key = (st.get("noise"), bool(st.get("busy")), light)
        if key == self._icon_key and self._icon:
            return self._icon
        size = self.u.GetSystemMetrics(49) or 16               # SM_CXSMICON, DPI-aware
        rgb = (0x1A, 0x1A, 0x1A) if light else (0xFF, 0xFF, 0xFF)
        alpha = 0.55 if st.get("busy") or st.get("noise") not in (0, 1, 2, 3) else 1.0
        res = icon_resource(render(st.get("noise"), size, rgb, alpha))
        icon = self.u.CreateIconFromResourceEx(res, len(res), True, 0x00030000, size, size, 0)
        if icon:
            if self._icon:
                self.u.DestroyIcon(self._icon)
            self._icon, self._icon_key = icon, key
        return self._icon

    def _show_menu(self):
        u, W = self.u, self.W
        st = self._state()
        st["autostart"] = autostart_enabled()
        h = u.CreatePopupMenu()
        for mid, text, flags in menu_items(st):
            if "sep" in flags:
                u.AppendMenuW(h, 0x800, 0, None)              # MF_SEPARATOR
                continue
            f = (0x1 if "grey" in flags else 0) | (0x8 if "check" in flags else 0) | (0x200 if "radio" in flags else 0)
            u.AppendMenuW(h, f, mid or 0, text)
        u.SetMenuDefaultItem(h, ID_OPEN, 0)
        pt = W.POINT()
        u.GetCursorPos(self.ctypes.byref(pt))
        u.SetForegroundWindow(self.hwnd)
        cmd = u.TrackPopupMenu(h, 0x0100 | 0x0002 | 0x0020, pt.x, pt.y, 0, self.hwnd, None)  # RETURNCMD|RIGHTBUTTON|BOTTOMALIGN
        u.PostMessageW(self.hwnd, 0, 0, 0)                    # WM_NULL, so the menu closes properly
        u.DestroyMenu(h)
        if cmd:
            try:
                self.on_command(cmd)
            except Exception:
                pass

    def _wndproc(self, hwnd, msg, wp, lp):
        try:
            if msg == self.WM_TRAY:
                if lp == 0x0202 and self.panel:               # WM_LBUTTONUP: the quick panel
                    self.panel.toggle()
                elif lp in (0x0202, 0x0205):                  # WM_RBUTTONUP: the menu
                    if self.panel:
                        self.panel.hide()
                    self._show_menu()
                elif lp == 0x0203:                            # WM_LBUTTONDBLCLK: the window
                    if self.panel:
                        self.panel.hide()
                    self.on_command(ID_OPEN)
                return 0
            if msg == self.WM_REFRESH or msg == 0x001A:       # our refresh, WM_SETTINGCHANGE (theme)
                if msg == 0x001A:
                    self._icon_key = None
                self._notify(1)
                if self.panel:
                    self.panel.refresh()
                return 0
            if msg == self.WM_BALLOON:
                with self._lock:
                    b, self._balloon = self._balloon, None
                if b:
                    n = self._nid(0x10)                       # NIF_INFO
                    n.szInfoTitle, n.szInfo = b
                    n.dwInfoFlags = 0x02 | 0x10               # NIIF_WARNING | NIIF_NOSOUND
                    self.sh.Shell_NotifyIconW(1, self.ctypes.byref(n))
                return 0
            if msg == self.taskbar_created:                   # explorer restarted
                self._notify(0)
                return 0
            if msg == 0x0010:                                 # WM_CLOSE
                self.sh.Shell_NotifyIconW(2, self.ctypes.byref(self._nid(0)))   # NIM_DELETE
                self.u.DestroyWindow(hwnd)
                return 0
            if msg == 0x0002:                                 # WM_DESTROY
                self.hwnd = None
                if self._icon:
                    self.u.DestroyIcon(self._icon)
                self.u.PostQuitMessage(0)
                return 0
        except Exception:
            pass
        return self.u.DefWindowProcW(hwnd, msg, wp, lp)
