#!/usr/bin/env python3
"""M4 Control: early Windows app for the MOMENTUM 4 (noise control, EQ, settings, power off).

Stock Python 3.9+ on Windows 10/11, no packages. Start with start-m4-app.bat or
`pythonw m4_app.py`. The PC talks to the headphones over its own Bluetooth (GAIA over RFCOMM,
the same transport as gaia_client.py), so the M4 must be paired with this PC.

One worker thread owns the link; the UI only queues frames built by m4_logic (documented
commands only). Logs: %LOCALAPPDATA%\\M4Control\\logs (no Bluetooth address in them).
"""

import json
import os
import platform
import queue
import re
import sys
import threading
import time
import tkinter as tk
from tkinter import messagebox, simpledialog, ttk

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import m4_logic as L  # noqa: E402

G = L.G
M4 = L.M4

APP_NAME = "M4 Control"
APP_DIR = os.path.join(os.environ.get("LOCALAPPDATA") or os.path.expanduser("~"), "M4Control")
SETTINGS_PATH = os.path.join(APP_DIR, "settings.json")
LOG_DIR = os.path.join(APP_DIR, "logs")
BATTERY_POLL_S = 120
ADDR_RE = re.compile(r"(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")

# Same tokens as the Android app's dark theme (res/values-night/colors.xml)
C = dict(bg="#000000", surface="#1C1C1C", sunk="#2C2C2C", ink="#F2F2F2", ink2="#9E9E9E",
         rule="#2A2A2A", rule_strong="#3A3A3A", copper="#D98C55", danger="#FF7A6E", on_key="#000000")
FONT = "Segoe UI"


def load_settings():
    try:
        with open(SETTINGS_PATH, encoding="utf-8") as f:
            s = json.load(f)
        return s if isinstance(s, dict) else {}
    except (OSError, ValueError):
        return {}


def save_settings(s):
    os.makedirs(APP_DIR, exist_ok=True)
    tmp = SETTINGS_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(s, f, indent=2)
    os.replace(tmp, SETTINGS_PATH)


def scrub(text):
    return ADDR_RE.sub("XX:XX:XX:XX:XX:XX", text)


class UiLogger(G.Logger):
    """gaia_client's file logger, plus every line to the app's Log tab and command labels on replies."""

    def __init__(self, directory, sink):
        self.sink = sink
        super().__init__(directory)

    def _w(self, line):
        super()._w(line)
        self.sink(line)

    def rx(self, frame):
        self._w("<- %s  (%s)" % (frame, M4.label(frame.vendor, frame.command)))


class Worker(threading.Thread):
    """Owns the RFCOMM link. Items: ("connect", addr), ("disconnect",), ("send", [frames]),
    ("poweroff",), ("quit",). Posts ("state", name, text) and ("frame", Frame) to the UI."""

    def __init__(self, post, log):
        super().__init__(daemon=True, name="m4-link")
        self.q = queue.Queue()
        self.post = post
        self.log = log
        self.link = None
        self.last_poll = 0.0

    def put(self, *item):
        self.q.put(item)

    def run(self):
        while True:
            try:
                item = self.q.get(timeout=0.05 if self.link else 0.5)
            except queue.Empty:
                item = None
            try:
                if item and self._handle(item) == "quit":
                    return
                if self.link:
                    self._pump()
            except OSError as e:
                self._drop("Link error: %s" % e)
            except Exception as e:  # keep the thread alive; the log has the details
                self.log.note("worker error: %r" % e)

    def _handle(self, item):
        kind = item[0]
        if kind == "quit":
            self._close()
            return "quit"
        if kind == "connect":
            self._close()
            self.post(("state", "connecting", None))
            link = G.Link(item[1], self.log)
            if link.connect(timeout=10.0):
                self.link = link
                self.post(("state", "connected", None))
                self._send_all(L.connect_frames())
                self.last_poll = time.monotonic()
            else:
                link.close()
                self.post(("state", "failed", None))
        elif kind == "disconnect":
            self._close()
            self.post(("state", "disconnected", None))
        elif kind == "send":
            self._send_all(item[1])
        elif kind == "poweroff":
            if self.link and self.link.send(L.POWER_OFF):
                self.post(("state", "poweroff_sent", None))
        return None

    def _send_all(self, frames):
        for f in frames:
            if not self.link:
                return
            self.link.send(f)

    def _pump(self):
        frames = self.link.read_frames(0.1)
        if frames is None:
            self._drop("The headphones closed the link.")
            return
        for f in frames:
            self.post(("frame", f))
        if time.monotonic() - self.last_poll > BATTERY_POLL_S:
            self.last_poll = time.monotonic()
            self._send_all([L.battery_frame()])

    def _drop(self, text):
        self._close()
        self.post(("state", "dropped", text))

    def _close(self):
        if self.link:
            self.link.close()
            self.link = None


class App:
    def __init__(self):
        self.settings = load_settings()
        self.custom_presets = {k: tuple(L.clamp_gain(g) for g in v)
                               for k, v in (self.settings.get("presets") or {}).items()
                               if isinstance(v, list) and len(v) == 5}
        self.uiq = queue.Queue()
        self.log_lines = []
        self.connected = False
        self.poweroff_sent = False
        self.info = {}
        self.anc_on, self.adaptive_on = None, None
        self.hires_on = None

        self.root = tk.Tk()
        self.root.title(APP_NAME)
        self.root.configure(bg=C["bg"])
        self.root.geometry("460x780")
        self.root.minsize(420, 640)
        self.root.protocol("WM_DELETE_WINDOW", self.quit)
        self._style()

        self.log = UiLogger(LOG_DIR, lambda line: self.uiq.put(("log", line)))
        self.log.note("M4 Control %s, %s, Python %s" % (L.APP_VERSION, platform.platform(), platform.python_version()))
        self.worker = Worker(self.uiq.put, self.log)
        self.worker.start()

        self.controls = []
        self._build()
        self.set_connected(False)
        self.rescan(initial=True)
        self.root.after(50, self.poll)

    # ------------------------------------------------------------- styling

    def _style(self):
        s = ttk.Style(self.root)
        s.theme_use("clam")
        s.configure(".", background=C["bg"], foreground=C["ink"], font=(FONT, 10),
                    fieldbackground=C["sunk"], bordercolor=C["rule_strong"], lightcolor=C["surface"],
                    darkcolor=C["surface"], troughcolor=C["sunk"], focuscolor=C["copper"])
        s.configure("Card.TFrame", background=C["surface"])
        s.configure("Card.TLabel", background=C["surface"])
        s.configure("Dim.TLabel", foreground=C["ink2"])
        s.configure("CardDim.TLabel", background=C["surface"], foreground=C["ink2"], font=(FONT, 9))
        s.configure("Head.TLabel", background=C["surface"], font=(FONT, 11, "bold"))
        s.configure("Title.TLabel", font=(FONT, 16, "bold"))
        s.configure("Big.TLabel", font=(FONT, 22, "bold"), foreground=C["copper"])
        s.configure("TButton", background=C["sunk"], foreground=C["ink"], padding=(12, 6), borderwidth=0)
        s.map("TButton", background=[("disabled", C["surface"]), ("active", C["rule_strong"])],
              foreground=[("disabled", C["ink2"])])
        s.configure("Accent.TButton", background=C["copper"], foreground=C["on_key"])
        s.map("Accent.TButton", background=[("disabled", C["surface"]), ("active", "#E6A57A")])
        s.configure("Danger.TButton", background=C["danger"], foreground=C["on_key"], font=(FONT, 10, "bold"))
        s.map("Danger.TButton", background=[("disabled", C["surface"]), ("active", "#FF9A90")])
        s.configure("Small.TButton", padding=(9, 5))
        # Segmented buttons and On/Off pills: no indicator, the whole label changes colour.
        s.layout("Seg.TRadiobutton", [("Radiobutton.padding", {"sticky": "nswe", "children": [
            ("Radiobutton.label", {"sticky": "nswe"})]})])
        s.configure("Seg.TRadiobutton", background=C["sunk"], foreground=C["ink"], padding=(10, 6), anchor="center")
        s.layout("Switch.TCheckbutton", [("Checkbutton.padding", {"sticky": "nswe", "children": [
            ("Checkbutton.label", {"sticky": "nswe"})]})])
        s.configure("Switch.TCheckbutton", background=C["sunk"], foreground=C["ink2"], padding=(0, 4), width=5,
                    anchor="center", font=(FONT, 9, "bold"))
        s.map("Switch.TCheckbutton",
              background=[("disabled", C["surface"]), ("selected", C["copper"]), ("active", C["rule_strong"])],
              foreground=[("disabled", C["rule_strong"]), ("selected", C["on_key"])])
        for o in ("Horizontal.TScale", "Vertical.TScale"):
            s.configure(o, background=C["copper"], lightcolor=C["copper"], darkcolor=C["copper"],
                        troughcolor=C["sunk"], bordercolor=C["surface"], gripcount=0)
            s.map(o, background=[("disabled", C["rule_strong"])], lightcolor=[("disabled", C["rule_strong"])],
                  darkcolor=[("disabled", C["rule_strong"])])
        s.map("Seg.TRadiobutton", background=[("selected", C["copper"]), ("active", C["rule_strong"])],
              foreground=[("selected", C["on_key"]), ("disabled", C["ink2"])])
        s.configure("TNotebook", background=C["bg"], borderwidth=0, tabmargins=(0, 6, 0, 0))
        s.configure("TNotebook.Tab", background=C["bg"], foreground=C["ink2"], padding=(14, 6), borderwidth=0)
        s.map("TNotebook.Tab", background=[("selected", C["surface"])], foreground=[("selected", C["ink"])])
        s.configure("TCombobox", foreground=C["ink"], arrowcolor=C["ink"], background=C["sunk"])
        s.map("TCombobox", fieldbackground=[("readonly", C["sunk"])], foreground=[("disabled", C["ink2"])])
        self.root.option_add("*TCombobox*Listbox.background", C["sunk"])
        self.root.option_add("*TCombobox*Listbox.foreground", C["ink"])
        self.root.option_add("*TCombobox*Listbox.selectBackground", C["copper"])

    def card(self, parent, title=None, pady=(0, 10)):
        f = ttk.Frame(parent, style="Card.TFrame", padding=14)
        f.pack(fill="x", pady=pady)
        if title:
            ttk.Label(f, text=title, style="Head.TLabel").pack(anchor="w", pady=(0, 8))
        return f

    def scale(self, parent, lo, hi, var, orient="horizontal", step=1, length=None, on_release=None):
        w = ttk.Scale(parent, from_=lo, to=hi, orient=orient, variable=var, length=length or 200)

        def release(_e=None):
            if w.instate(["disabled"]):
                return
            var.set(round(var.get() / step) * step)          # snap, then send once on release
            if on_release:
                on_release()
        w.bind("<ButtonRelease-1>", release)
        self.controls.append(w)
        return w

    def switch_row(self, parent, label, var, command):
        """A label with an On/Off pill on the right (the Android app's switch rows)."""
        row = ttk.Frame(parent, style="Card.TFrame")
        row.pack(fill="x", pady=3)
        ttk.Label(row, text=label, style="Card.TLabel").pack(side="left")
        txt = tk.StringVar(value="Off")
        var.trace_add("write", lambda *_a: txt.set("On" if var.get() else "Off"))
        b = ttk.Checkbutton(row, textvariable=txt, variable=var, command=command, style="Switch.TCheckbutton")
        b.pack(side="right")
        self.controls.append(b)
        return b

    # --------------------------------------------------------------- layout

    def _build(self):
        top = ttk.Frame(self.root, padding=(16, 14, 16, 4))
        top.pack(fill="x")
        row = ttk.Frame(top)
        row.pack(fill="x")
        left = ttk.Frame(row)
        left.pack(side="left", fill="x", expand=True)
        ttk.Label(left, text="MOMENTUM 4", style="Title.TLabel").pack(anchor="w")
        self.status = tk.StringVar(value="Not connected")
        ttk.Label(left, textvariable=self.status, style="Dim.TLabel", wraplength=300).pack(anchor="w")
        self.battery = tk.StringVar(value="")
        ttk.Label(row, textvariable=self.battery, style="Big.TLabel").pack(side="right")

        dev = ttk.Frame(top)
        dev.pack(fill="x", pady=(10, 0))
        self.device_var = tk.StringVar()
        self.device_box = ttk.Combobox(dev, textvariable=self.device_var, state="readonly")
        self.device_box.pack(side="left", fill="x", expand=True)
        self.device_box.bind("<<ComboboxSelected>>", lambda _e: self.on_device_pick())
        ttk.Button(dev, text="Rescan", command=self.rescan).pack(side="left", padx=(6, 0))
        self.connect_btn = ttk.Button(dev, text="Connect", style="Accent.TButton", command=self.on_connect)
        self.connect_btn.pack(side="left", padx=(6, 0))

        nb = self.nb = ttk.Notebook(self.root)
        nb.pack(fill="both", expand=True, padx=16, pady=(8, 16))
        self.tab_sound = ttk.Frame(nb, padding=(0, 10, 0, 0))
        self.tab_settings = ttk.Frame(nb, padding=(0, 10, 0, 0))
        self.tab_power = ttk.Frame(nb, padding=(0, 10, 0, 0))
        self.tab_log = ttk.Frame(nb, padding=(0, 10, 0, 0))
        for t, name in ((self.tab_sound, "Sound"), (self.tab_settings, "Settings"),
                        (self.tab_power, "Power"), (self.tab_log, "Log")):
            nb.add(t, text=name)
        self._build_sound()
        self._build_settings()
        self._build_power()
        self._build_log()

    def _build_sound(self):
        nc = self.card(self.tab_sound, "Noise control")
        seg = self.noise_seg = ttk.Frame(nc, style="Card.TFrame")
        seg.pack(fill="x")
        self.noise_var = tk.StringVar(value="")
        for i, name in enumerate(L.NOISE_MODES):
            b = ttk.Radiobutton(seg, text=name, value=name, variable=self.noise_var, style="Seg.TRadiobutton",
                                command=self.on_noise)
            b.pack(side="left", fill="x", expand=True, padx=(0 if i == 0 else 4, 0))
            self.controls.append(b)
        self.custom_box = ttk.Frame(nc, style="Card.TFrame")
        ttk.Label(self.custom_box, text="ANC to transparency", style="CardDim.TLabel").pack(anchor="w", pady=(10, 2))
        self.level_var = tk.IntVar(value=0)
        self.scale(self.custom_box, 0, 100, self.level_var, on_release=self.on_level).pack(fill="x")
        ends = ttk.Frame(self.custom_box, style="Card.TFrame")
        ends.pack(fill="x")
        ttk.Label(ends, text="ANC", style="CardDim.TLabel").pack(side="left")
        ttk.Label(ends, text="Transparency", style="CardDim.TLabel").pack(side="right")
        ttk.Label(nc, text="Anti-wind", style="CardDim.TLabel").pack(anchor="w", pady=(12, 4))
        aw = ttk.Frame(nc, style="Card.TFrame")
        aw.pack(fill="x")
        self.wind_var = tk.StringVar(value="")
        for i, name in enumerate(L.ANTI_WIND):
            b = ttk.Radiobutton(aw, text=name, value=name, variable=self.wind_var, style="Seg.TRadiobutton",
                                command=self.on_wind)
            b.pack(side="left", fill="x", expand=True, padx=(0 if i == 0 else 4, 0))
            self.controls.append(b)

        eq = self.card(self.tab_sound, "Equaliser")
        prow = ttk.Frame(eq, style="Card.TFrame")
        prow.pack(fill="x")
        self.preset_var = tk.StringVar(value="")
        self.preset_box = ttk.Combobox(prow, textvariable=self.preset_var, state="readonly", width=14)
        self.preset_box.pack(side="left", fill="x", expand=True)
        self.preset_box.bind("<<ComboboxSelected>>", lambda _e: self.on_preset())
        self.controls.append(self.preset_box)
        for text, cmd in (("Save", self.on_save_preset), ("Delete", self.on_delete_preset), ("Flat", self.on_flat)):
            b = ttk.Button(prow, text=text, command=cmd, style="Small.TButton")
            b.pack(side="left", padx=(6, 0))
            self.controls.append(b)
        self.refresh_preset_list()
        faders = ttk.Frame(eq, style="Card.TFrame")
        faders.pack(fill="x", pady=(10, 0))
        self.band_vars, self.band_labels = [], []
        for i, name in enumerate(L.BANDS):
            col = ttk.Frame(faders, style="Card.TFrame")
            col.pack(side="left", fill="x", expand=True)
            v = tk.DoubleVar(value=0.0)
            lab = tk.StringVar(value="0.0")
            ttk.Label(col, textvariable=lab, style="Card.TLabel", font=(FONT, 9)).pack()
            self.scale(col, 6.0, -6.0, v, orient="vertical", step=0.5, length=150,
                       on_release=lambda b=i: self.on_band(b)).pack()
            v.trace_add("write", lambda *_a, v=v, lab=lab: lab.set("%+.1f" % (round(v.get() * 2) / 2) if round(v.get() * 2) else "0.0"))
            ttk.Label(col, text=name, style="CardDim.TLabel").pack()
            self.band_vars.append(v)
        self.toggle_vars = {}
        ttk.Frame(eq, style="Card.TFrame", height=6).pack()
        self._toggle(eq, "bass")

    def _toggle(self, parent, key):
        _k, label, _s, _g, _inv = L.TOGGLE_BY_KEY[key]
        v = tk.BooleanVar(value=False)
        self.toggle_vars[key] = v
        return self.switch_row(parent, label, v, lambda: self.on_toggle(key))

    def _build_settings(self):
        lc = self.card(self.tab_settings, "Listening")
        for key in ("smart_pause", "on_head", "touch", "low_latency", "hires"):
            self._toggle(lc, key)
        self.speech_var = tk.BooleanVar(value=False)
        self.speech_btn = self.switch_row(lc, "Speech clarity", self.speech_var, self.on_speech)
        self.speech_hint = ttk.Label(lc, text="Speech clarity needs Hi-Res off.", style="CardDim.TLabel")
        self.speech_hint.pack(anchor="w")
        ttk.Label(lc, text="Low latency comes from Sennheiser's older app. Its effect is not measured yet.",
                  style="CardDim.TLabel", wraplength=360).pack(anchor="w", pady=(6, 0))

        cc = self.card(self.tab_settings, "Calls")
        for key in ("auto_answer", "comfort_call"):
            self._toggle(cc, key)
        self.call_label = tk.StringVar(value="Transparency during calls: -")
        ttk.Label(cc, textvariable=self.call_label, style="CardDim.TLabel").pack(anchor="w", pady=(8, 2))
        self.call_var = tk.IntVar(value=0)
        self.scale(cc, 0, 4, self.call_var, on_release=self.on_call).pack(fill="x")

    def _build_power(self):
        pc = self.card(self.tab_power, "Power off")
        ttk.Label(pc, style="Card.TLabel", wraplength=380, justify="left",
                  text="Switches the headphones fully off, the same as holding the button or closing "
                       "the case. Bluetooth goes off too. Press the button to turn them on again.").pack(anchor="w")
        self.power_btn = ttk.Button(pc, text="Power off now", style="Danger.TButton", command=self.on_power)
        self.power_btn.pack(fill="x", pady=(12, 0))
        self.controls.append(self.power_btn)

        ic = self.card(self.tab_power, "Headphones")
        self.info_var = tk.StringVar(value="Connect to read model and firmware.")
        ttk.Label(ic, textvariable=self.info_var, style="Card.TLabel", justify="left").pack(anchor="w")
        ttk.Label(ic, style="CardDim.TLabel", wraplength=380, justify="left",
                  text="The built-in auto-off timer only reaches a standby that keeps Bluetooth on. "
                       "Automatic power-off when idle is in the Android app for now.").pack(anchor="w", pady=(8, 0))

    def _build_log(self):
        bar = ttk.Frame(self.tab_log)
        bar.pack(fill="x", pady=(0, 8))
        ttk.Button(bar, text="Copy report", style="Accent.TButton", command=self.copy_report).pack(side="left")
        ttk.Button(bar, text="Open log folder", command=lambda: os.startfile(LOG_DIR)).pack(side="left", padx=(6, 0))
        self.log_text = tk.Text(self.tab_log, bg=C["surface"], fg=C["ink2"], insertbackground=C["ink"],
                                relief="flat", font=("Consolas", 9), wrap="none", height=20)
        self.log_text.pack(fill="both", expand=True)
        self.log_text.configure(state="disabled")

    # --------------------------------------------------------------- state

    def set_enabled(self, w, on):
        try:
            w.state(["!disabled"] if on else ["disabled"])
        except AttributeError:
            w.configure(state="normal" if on else "disabled")

    def set_connected(self, on):
        self.connected = on
        for w in self.controls:
            self.set_enabled(w, on)
        if on:
            self.apply_speech_rule()
        self.connect_btn.configure(text="Disconnect" if on else "Connect")
        if not on:
            self.battery.set("")

    def apply_speech_rule(self):
        ok = self.connected and self.hires_on is False
        self.set_enabled(self.speech_btn, ok)
        if self.hires_on:
            self.speech_hint.pack(anchor="w", after=self.speech_btn.master)
        else:
            self.speech_hint.pack_forget()

    def render_status(self):
        if not self.connected:
            return
        parts = ["Connected"]
        if "firmware" in self.info:
            parts.append("fw " + self.info["firmware"])
        self.status.set(" · ".join(parts))
        lines = []
        for key, label in (("model", "Model"), ("firmware", "Firmware"), ("hardware", "Hardware"),
                           ("battery", "Battery")):
            if key in self.info:
                lines.append("%s: %s%s" % (label, self.info[key], " %" if key == "battery" else ""))
        if "timer" in self.info:
            t = self.info["timer"]
            lines.append("Built-in auto-off timer: %s" % ("off" if t == 0 else "%d min" % (t // 60) if t % 60 == 0
                                                          else "%d s" % t))
        self.info_var.set("\n".join(lines) or "Reading...")

    def apply(self, d):
        if "error" in d:
            base, code = d["error"]
            self.log.note("headphones refused %04X (code %s)" % (base, "%02X" % code if code is not None else "?"))
            if base == 0x0803 and code != 0x80:
                self.send(L.speech_frames(self.speech_var.get())[1:])   # read the real sound mode back
            return
        for key in ("battery", "model", "firmware", "hardware", "timer"):
            if key in d:
                self.info[key] = d[key]
        if "battery" in d:
            self.battery.set("%d %%" % d["battery"])
        if "anc_on" in d:
            self.anc_on = d["anc_on"]
        if "adaptive_on" in d:
            self.adaptive_on = d["adaptive_on"]
        if ("anc_on" in d or "adaptive_on" in d) and self.anc_on is not None:
            self.show_noise(L.noise_mode_index(self.anc_on, bool(self.adaptive_on)))
        if "anti_wind" in d:
            self.wind_var.set(L.ANTI_WIND[d["anti_wind"]])
        if "level" in d:
            self.level_var.set(d["level"])
        if "eq" in d:
            self.set_curve(d["eq"])
        if "call_transparency" in d:
            self.call_var.set(d["call_transparency"])
            self.call_label.set("Transparency during calls: %d / 4" % d["call_transparency"])
        if "speech" in d:
            self.speech_var.set(d["speech"])
        for key, v in self.toggle_vars.items():
            if key in d:
                v.set(d[key])
        if "hires" in d:
            self.hires_on = d["hires"]
            self.apply_speech_rule()
        self.render_status()

    def show_noise(self, idx):
        self.noise_var.set(L.NOISE_MODES[idx])
        if idx == 1:
            self.custom_box.pack(fill="x", after=self.noise_seg)
        else:
            self.custom_box.pack_forget()

    def curve(self):
        return tuple(L.clamp_gain(round(v.get() * 10)) for v in self.band_vars)

    def set_curve(self, gains10):
        for v, g in zip(self.band_vars, gains10):
            v.set(g / 10.0)
        self.preset_var.set(L.preset_name(gains10, self.custom_presets) or "Custom curve")

    def refresh_preset_list(self):
        self.preset_box.configure(values=[n for n, _g in L.BUILTIN_PRESETS] + sorted(self.custom_presets))

    # ------------------------------------------------------------ actions

    def send(self, frames):
        if self.connected and frames:
            self.worker.put("send", frames)

    def safe(self, fn, *args):
        try:
            self.send(fn(*args))
        except L.Refused as e:
            self.log.note(str(e))
            self.status.set("Not sent: %s" % e)

    def on_noise(self):
        idx = L.NOISE_MODES.index(self.noise_var.get())
        self.anc_on, self.adaptive_on = idx != 2, idx == 0
        self.show_noise(idx)
        self.safe(L.noise_frames, idx, self.level_var.get())

    def on_level(self):
        self.safe(L.level_frames, self.level_var.get())

    def on_wind(self):
        self.safe(lambda: [L.anti_wind_frame(L.ANTI_WIND.index(self.wind_var.get()))])

    def on_band(self, band):
        g = self.curve()
        self.preset_var.set(L.preset_name(g, self.custom_presets) or "Custom curve")
        self.safe(lambda: [L.eq_band_frame(band, g[band])])

    def on_preset(self):
        name = self.preset_var.get()
        gains = dict(L.BUILTIN_PRESETS).get(name) or self.custom_presets.get(name)
        if gains:
            self.set_curve(gains)
            self.safe(L.eq_curve_frames, gains)

    def on_flat(self):
        self.set_curve((0, 0, 0, 0, 0))
        self.safe(L.eq_curve_frames, (0, 0, 0, 0, 0))

    def on_save_preset(self):
        name = simpledialog.askstring("Save preset", "Name for this curve:", parent=self.root)
        if not name or not name.strip():
            return
        name = name.strip()[:30]
        if name in dict(L.BUILTIN_PRESETS):
            messagebox.showinfo(APP_NAME, "That name is a built-in preset. Pick another name.", parent=self.root)
            return
        self.custom_presets[name] = self.curve()
        self._store_presets()
        self.preset_var.set(name)

    def on_delete_preset(self):
        name = self.preset_var.get()
        if name not in self.custom_presets:
            messagebox.showinfo(APP_NAME, "Only your own presets can be deleted.", parent=self.root)
            return
        if messagebox.askyesno(APP_NAME, "Delete preset \"%s\"?" % name, parent=self.root):
            del self.custom_presets[name]
            self._store_presets()
            self.preset_var.set(L.preset_name(self.curve(), self.custom_presets) or "Custom curve")

    def _store_presets(self):
        self.settings["presets"] = {k: list(v) for k, v in self.custom_presets.items()}
        save_settings(self.settings)
        self.refresh_preset_list()

    def on_toggle(self, key):
        self.safe(L.toggle_frames, key, self.toggle_vars[key].get())
        if key == "hires":
            self.hires_on = self.toggle_vars[key].get()
            self.apply_speech_rule()

    def on_speech(self):
        self.safe(L.speech_frames, self.speech_var.get())

    def on_call(self):
        self.call_label.set("Transparency during calls: %d / 4" % self.call_var.get())
        self.safe(L.call_transparency_frames, self.call_var.get())

    def on_power(self):
        if not self.connected:
            return
        if messagebox.askyesno("Power off", "Switch the headphones fully off?\n\n"
                               "They disconnect from every device. Press the button on the headphones "
                               "to turn them on again.", icon="warning", parent=self.root):
            self.worker.put("poweroff")

    # ------------------------------------------------------------ devices

    def rescan(self, initial=False):
        self.devices = L.paired_devices()
        known = {a.upper() for _n, a in self.devices}
        cli = G.default_addr()                           # M4_ADDR or m4_addr.txt, like the CLI
        if cli and cli.upper() not in known and L.format_addr(cli.replace(":", "")):
            self.devices.append(("Address from M4_ADDR / m4_addr.txt", cli.upper()))
        names, seen = [], {}
        for n, _a in self.devices:
            seen[n] = seen.get(n, 0) + 1
            names.append(n if seen[n] == 1 else "%s (%d)" % (n, seen[n]))
        self.device_box.configure(values=names)
        idx = L.pick_default(self.devices, self.settings.get("address"))
        if idx is None:
            self.device_var.set("")
            self.status.set("No paired Bluetooth devices. Pair the headphones in Windows Settings, then Rescan.")
            return
        self.device_box.current(idx)
        if initial and ("MOMENTUM" in self.devices[idx][0].upper() or self.settings.get("address")):
            self.root.after(300, self.on_connect)          # auto-connect while the app is open (as Android 0.14.2)

    def selected_addr(self):
        i = self.device_box.current()
        return self.devices[i][1] if 0 <= i < len(self.devices) else None

    def on_device_pick(self):
        addr = self.selected_addr()
        if addr:
            self.settings["address"] = addr
            save_settings(self.settings)
        if self.connected:
            self.worker.put("connect", addr)

    def on_connect(self):
        if self.connected:
            self.worker.put("disconnect")
            return
        addr = self.selected_addr()
        if not addr:
            self.status.set("Pick the headphones first.")
            return
        self.settings["address"] = addr
        save_settings(self.settings)
        self.poweroff_sent = False
        self.worker.put("connect", addr)

    # ------------------------------------------------------------ plumbing

    def poll(self):
        try:
            while True:
                item = self.uiq.get_nowait()
                kind = item[0]
                if kind == "frame":
                    self.apply(L.decode(item[1]))
                elif kind == "log":
                    self.add_log(item[1])
                elif kind == "state":
                    self.on_state(item[1], item[2])
        except queue.Empty:
            pass
        self.root.after(50, self.poll)

    def on_state(self, name, text):
        if name == "connecting":
            self.status.set("Connecting...")
            self.set_enabled(self.connect_btn, False)
            return
        self.set_enabled(self.connect_btn, True)
        if name == "connected":
            self.info = {}
            self.set_connected(True)
            self.status.set("Connected, reading settings...")
        elif name == "poweroff_sent":
            self.poweroff_sent = True
            self.status.set("Power off sent. Press the button on the headphones to turn them on.")
        else:
            self.set_connected(False)
            if self.poweroff_sent:
                self.status.set("Switched off. Press the button on the headphones to turn them on.")
            elif name == "failed":
                self.status.set("Couldn't connect. Check the headphones are on and paired with this PC, "
                                "and close other apps on this PC that control them.")
            elif name == "dropped":
                self.status.set("Disconnected. %s" % (text or ""))
            else:
                self.status.set("Not connected")

    def add_log(self, line):
        self.log_lines.append(line)
        del self.log_lines[:-500]
        self.log_text.configure(state="normal")
        self.log_text.insert("end", line + "\n")
        if int(self.log_text.index("end-1c").split(".")[0]) > 600:
            self.log_text.delete("1.0", "100.0")
        self.log_text.see("end")
        self.log_text.configure(state="disabled")

    def copy_report(self):
        i = self.info
        lines = ["M4 Control %s (Windows app, early version)" % L.APP_VERSION,
                 "PC: %s, Python %s" % (platform.platform(), platform.python_version()),
                 "Headphones: %s" % ("connected" if self.connected else "not connected"),
                 "Model: %s, firmware: %s, hardware: %s, battery: %s" % (
                     i.get("model", "?"), i.get("firmware", "?"), i.get("hardware", "?"), i.get("battery", "?")),
                 "", "Log (last 80 lines):"] + self.log_lines[-80:]
        self.root.clipboard_clear()
        self.root.clipboard_append(scrub("\n".join(lines)))
        self.status.set("Report copied. It has no Bluetooth address or device names.")

    def quit(self):
        self.worker.put("quit")
        self.worker.join(timeout=1.5)
        self.root.destroy()


def main():
    if sys.platform.startswith("win"):
        try:
            import ctypes
            ctypes.windll.shcore.SetProcessDpiAwareness(1)   # sharp text on scaled displays
        except Exception:
            pass
    App().root.mainloop()


if __name__ == "__main__":
    main()
