#!/usr/bin/env python3
"""Windows desktop companion for MOMENTUM 4.

The UI mirrors the Android companion's controls while keeping the Windows
notification-area shortcuts deliberately small: one click cycles EQ, the other
cycles noise control.  All headset writes use the project's documented GAIA
command table.
"""

from __future__ import annotations

import json
import os
import re
import sys
import threading
import time
from pathlib import Path
import tkinter as tk
from tkinter import messagebox, simpledialog, ttk

import pystray
from PIL import Image, ImageDraw

import gaia_client as Gaia


APP_NAME = "Momentum 4 Companion"
APP_DATA = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "Momentum4Companion"
SETTINGS = APP_DATA / "settings.json"
LOGS = APP_DATA / "logs"
ADDRESS_RE = re.compile(r"^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

BG, SURFACE, SUNK, INK, INK2, RULE = "#000000", "#1C1C1C", "#2C2C2C", "#F2F2F2", "#9E9E9E", "#2A2A2A"
COPPER, DANGER, FIELD = "#D98C55", "#FF7A6E", "#151515"
EQ_PRESETS = (
    ("Neutral", (0, 0, 0, 0, 0)), ("Rock", (0, 20, 25, 15, -20)),
    ("Pop", (0, -25, 0, 25, 0)), ("Dance", (35, 20, -15, 15, 30)),
    ("Hip-Hop", (30, 15, -15, 0, -15)), ("Classical", (-20, -15, 0, 35, 40)),
    ("Movie", (0, 0, 20, 20, -20)), ("Jazz", (-32, 0, 22, 22, 0)),
)
NOISE_MODES = ("Adaptive", "Custom", "Off")
FREQUENCIES = ("63", "250", "1K", "4K", "8K")


def _load_settings() -> dict:
    try:
        data = json.loads(SETTINGS.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def _tray_image() -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((4, 4, 60, 60), 17, fill="#1C1C1C")
    draw.arc((15, 14, 49, 49), 195, 345, fill=COPPER, width=4)
    draw.arc((15, 14, 49, 49), 15, 165, fill=INK, width=4)
    draw.ellipse((29, 28, 35, 34), fill=COPPER)
    return image


class ScrollFrame(tk.Frame):
    def __init__(self, parent: tk.Widget) -> None:
        super().__init__(parent, bg=BG)
        self.canvas = tk.Canvas(self, bg=BG, highlightthickness=0, borderwidth=0)
        bar = tk.Scrollbar(self, orient="vertical", command=self.canvas.yview)
        self.canvas.configure(yscrollcommand=bar.set)
        self.canvas.pack(side="left", fill="both", expand=True)
        bar.pack(side="right", fill="y")
        self.body = tk.Frame(self.canvas, bg=BG)
        self.window = self.canvas.create_window((0, 0), window=self.body, anchor="nw")
        self.body.bind("<Configure>", self._resize)
        self.canvas.bind("<Configure>", lambda event: self.canvas.itemconfigure(self.window, width=event.width))
        self.canvas.bind_all("<MouseWheel>", self._wheel, add="+")

    def _resize(self, _event: tk.Event) -> None:
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def _wheel(self, event: tk.Event) -> None:
        if self.winfo_ismapped():
            self.canvas.yview_scroll(int(-event.delta / 120), "units")


class CompanionApp:
    """A full desktop UI plus compact tray shortcuts."""

    def __init__(self) -> None:
        self.settings = _load_settings()
        self.state = {
            "battery": "—", "model": "MOMENTUM 4", "firmware": "—", "hardware": "—",
            "noise": self._index("noise", 1, len(NOISE_MODES)), "transparency": 0,
            "antiwind": 0, "eq": [0, 0, 0, 0, 0], "eq_index": self._index("eq_index", 0, len(EQ_PRESETS)),
            "smart_pause": False, "auto_answer": False, "low_latency": False, "hi_res": False,
            "comfort_call": False, "on_head": False, "touch": True, "call_transparency": 0,
            "bass": False, "speech": False, "timer": 0,
        }
        self.page = "Home"
        self.busy = False
        self.guard_running = False
        self.guard_stop = threading.Event()
        self.guard_status = "Guard off"
        self.app_log: list[str] = []
        self.root = tk.Tk()
        self.root.title(APP_NAME)
        self.root.configure(bg=BG)
        self.root.geometry("1040x730")
        self.root.minsize(900, 620)
        self.root.protocol("WM_DELETE_WINDOW", self.hide)
        self._set_windows_style()
        self.address = tk.StringVar(value=str(self.settings.get("address", "")).upper())
        self.status = tk.StringVar(value="Enter the headphone Bluetooth address, then connect.")
        self._build_shell()
        self._build_tray()
        self.show_page("Home")
        self.root.after(400, self._autoload_guard)

    def _index(self, key: str, default: int, maximum: int) -> int:
        try:
            value = int(self.settings.get(key, default))
            return value if 0 <= value < maximum else default
        except (TypeError, ValueError):
            return default

    def _set_windows_style(self) -> None:
        style = ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure("TCombobox", fieldbackground=FIELD, background=SUNK, foreground=INK,
                        arrowcolor=INK, bordercolor=RULE, lightcolor=RULE, darkcolor=RULE)

    def _build_shell(self) -> None:
        top = tk.Frame(self.root, bg=BG, height=58)
        top.pack(fill="x", padx=26, pady=(18, 0))
        tk.Label(top, text="MOMENTUM 4", fg=INK, bg=BG, font=("Georgia", 22)).pack(side="left")
        self.connection_dot = tk.Label(top, text="●  Disconnected", fg=INK2, bg=BG, font=("Segoe UI", 10))
        self.connection_dot.pack(side="right", pady=7)
        tk.Frame(self.root, bg=RULE, height=1).pack(fill="x", padx=26, pady=(10, 0))

        workspace = tk.Frame(self.root, bg=BG)
        workspace.pack(fill="both", expand=True, padx=26, pady=(0, 20))
        self.nav = tk.Frame(workspace, bg=BG, width=175)
        self.nav.pack(side="left", fill="y", padx=(0, 22))
        self.nav.pack_propagate(False)
        self.content = tk.Frame(workspace, bg=BG)
        self.content.pack(side="left", fill="both", expand=True)
        for item in ("Home", "Equaliser", "Listening", "Auto power-off", "Device", "Logs"):
            button = tk.Button(self.nav, text=item, anchor="w", relief="flat", bd=0, padx=14,
                               pady=10, cursor="hand2", command=lambda p=item: self.show_page(p))
            button.configure(bg=BG, fg=INK2, activebackground=SUNK, activeforeground=INK,
                             font=("Segoe UI", 11))
            button.pack(fill="x", pady=2)
            setattr(self, "nav_" + item.replace(" ", "_"), button)
        tk.Frame(self.nav, bg=BG).pack(fill="both", expand=True)
        self.tray_hint = tk.Label(self.nav, text="Runs in the notification area\nwhen this window is closed.",
                                  fg=INK2, bg=BG, justify="left", wraplength=150, font=("Segoe UI", 9))
        self.tray_hint.pack(anchor="w", pady=(10, 0))

    def _build_tray(self) -> None:
        menu = pystray.Menu(
            pystray.MenuItem("Open Momentum 4", self._tray_open, default=True),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem("Cycle EQ preset", self._tray_cycle_eq),
            pystray.MenuItem("Cycle noise control", self._tray_cycle_noise),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem("Exit", self._tray_exit),
        )
        self.tray = pystray.Icon("momentum4_companion", _tray_image(), APP_NAME, menu)
        self.tray.run_detached()

    def _tray_open(self, _icon=None, _item=None) -> None:
        self.root.after(0, self.show)

    def _tray_cycle_eq(self, _icon=None, _item=None) -> None:
        self.root.after(0, self.cycle_eq)

    def _tray_cycle_noise(self, _icon=None, _item=None) -> None:
        self.root.after(0, self.cycle_noise)

    def _tray_exit(self, _icon=None, _item=None) -> None:
        self.root.after(0, self.quit)

    def show(self) -> None:
        self.root.deiconify()
        self.root.state("normal")
        self.root.lift()
        self.root.focus_force()

    def hide(self) -> None:
        self.root.withdraw()

    def quit(self) -> None:
        self.guard_stop.set()
        try:
            self.tray.stop()
        finally:
            self.root.destroy()

    def _clear_content(self) -> None:
        for child in self.content.winfo_children():
            child.destroy()

    def show_page(self, name: str) -> None:
        self.page = name
        self._clear_content()
        for item in ("Home", "Equaliser", "Listening", "Auto power-off", "Device", "Logs"):
            button = getattr(self, "nav_" + item.replace(" ", "_"))
            selected = item == name
            button.configure(bg=SUNK if selected else BG, fg=INK if selected else INK2)
        pages = {"Home": self._home_page, "Equaliser": self._eq_page, "Listening": self._listening_page,
                 "Auto power-off": self._guard_page, "Device": self._device_page, "Logs": self._logs_page}
        pages[name]()

    def _scroll_page(self, title: str, subtitle: str = "") -> tk.Frame:
        scroll = ScrollFrame(self.content)
        scroll.pack(fill="both", expand=True)
        body = scroll.body
        tk.Label(body, text=title, fg=INK, bg=BG, font=("Georgia", 30)).pack(anchor="w", pady=(6, 0))
        if subtitle:
            tk.Label(body, text=subtitle, fg=INK2, bg=BG, font=("Segoe UI", 10)).pack(anchor="w", pady=(2, 18))
        return body

    def _card(self, parent: tk.Widget, title: str, subtitle: str = "") -> tk.Frame:
        box = tk.Frame(parent, bg=SURFACE, padx=18, pady=16, highlightthickness=1, highlightbackground=SURFACE)
        box.pack(fill="x", pady=(0, 8))
        tk.Label(box, text=title, fg=INK, bg=SURFACE, font=("Segoe UI", 13, "bold")).pack(anchor="w")
        if subtitle:
            tk.Label(box, text=subtitle, fg=INK2, bg=SURFACE, justify="left", wraplength=760,
                     font=("Segoe UI", 9)).pack(anchor="w", pady=(2, 12))
        return box

    def _button(self, parent: tk.Widget, text: str, command, filled: bool = True, danger: bool = False,
                width: int | None = None) -> tk.Button:
        bg = DANGER if danger else COPPER if filled else SUNK
        fg = BG if (filled and not danger) else INK
        if danger:
            fg = BG
        button = tk.Button(parent, text=text, command=command, relief="flat", bd=0, padx=15, pady=8,
                           cursor="hand2", bg=bg, fg=fg, activebackground="#B66D3F" if filled else RULE,
                           activeforeground=INK, font=("Segoe UI", 10, "bold"), width=width)
        return button

    def _switch(self, parent: tk.Widget, label: str, key: str, command, note: str = "") -> tk.BooleanVar:
        row = tk.Frame(parent, bg=SURFACE)
        row.pack(fill="x", pady=5)
        column = tk.Frame(row, bg=SURFACE)
        column.pack(side="left", fill="x", expand=True)
        tk.Label(column, text=label, fg=INK, bg=SURFACE, font=("Segoe UI", 10)).pack(anchor="w")
        if note:
            tk.Label(column, text=note, fg=INK2, bg=SURFACE, font=("Segoe UI", 8)).pack(anchor="w")
        value = tk.BooleanVar(value=bool(self.state[key]))
        check = tk.Checkbutton(row, variable=value, command=lambda: command(value.get()), bg=SURFACE, fg=INK,
                               selectcolor=COPPER, activebackground=SURFACE, activeforeground=INK,
                               highlightthickness=0, bd=0)
        check.pack(side="right")
        return value

    # ---- Home ----------------------------------------------------------

    def _home_page(self) -> None:
        body = self._scroll_page("Headphones")
        hero = tk.Frame(body, bg=BG)
        hero.pack(fill="x", pady=(4, 20))
        battery = self.state["battery"]
        tk.Label(hero, text=battery if battery != "—" else "--", fg=COPPER if battery != "—" else INK2,
                 bg=BG, font=("Consolas", 26, "bold")).pack()
        tk.Label(hero, text=self.state["model"], fg=INK, bg=BG, font=("Georgia", 20)).pack()
        tk.Label(hero, textvariable=self.status, fg=INK2, bg=BG, font=("Segoe UI", 9), wraplength=720).pack(pady=(4, 10))
        row = tk.Frame(hero, bg=BG)
        row.pack()
        self._button(row, "Connect and read", self.refresh_all).pack(side="left", padx=3)
        self._button(row, "Disconnect", self.disconnect, filled=False).pack(side="left", padx=3)

        card = self._card(body, "Noise control")
        modes = tk.Frame(card, bg=SURFACE)
        modes.pack(anchor="w", pady=(8, 12))
        self.noise_buttons = []
        for index, name in enumerate(NOISE_MODES):
            selected = self.state["noise"] == index
            button = tk.Button(modes, text=("◉\n" if selected else "○\n") + name,
                               command=lambda i=index: self.set_noise(i), width=13, height=3, relief="flat", bd=0,
                               bg=INK if selected else SUNK, fg=BG if selected else INK2,
                               activebackground=COPPER, cursor="hand2", font=("Segoe UI", 10, "bold"))
            button.pack(side="left", padx=(0, 6))
            self.noise_buttons.append(button)
        if self.state["noise"] == 1:
            self._custom_noise(card)

        tiles = tk.Frame(body, bg=BG)
        tiles.pack(fill="x", pady=(0, 8))
        tiles.columnconfigure((0, 1), weight=1)
        self._nav_tile(tiles, 0, 0, "Equaliser", self._preset_name(), "≋", lambda: self.show_page("Equaliser"))
        self._nav_tile(tiles, 0, 1, "Listening", self._listening_summary(), "◖", lambda: self.show_page("Listening"))
        self._nav_tile(tiles, 1, 0, "Auto power-off", self._guard_summary(), "◔", lambda: self.show_page("Auto power-off"))
        self._nav_tile(tiles, 1, 1, "Device", self._device_summary(), "◉", lambda: self.show_page("Device"))
        power = self._button(body, "Power off headphones", self.confirm_power_off, danger=True)
        power.pack(fill="x", pady=(4, 14))

    def _nav_tile(self, parent: tk.Widget, row: int, col: int, title: str, subtitle: str, icon: str, command) -> None:
        tile = tk.Button(parent, text=f"{title}\n{subtitle}\n\n{icon}", command=command, anchor="nw", justify="left",
                         relief="flat", bd=0, padx=16, pady=13, height=7, bg=SURFACE, fg=INK,
                         activebackground=SUNK, activeforeground=INK, cursor="hand2", font=("Segoe UI", 11))
        tile.grid(row=row, column=col, sticky="nsew", padx=2, pady=2)

    def _custom_noise(self, parent: tk.Widget) -> None:
        line = tk.Frame(parent, bg=SURFACE)
        line.pack(fill="x", pady=(0, 8))
        tk.Label(line, text="ANC", fg=INK2, bg=SURFACE).pack(side="left")
        level = tk.IntVar(value=int(self.state["transparency"]))
        tk.Scale(line, from_=0, to=100, orient="horizontal", variable=level, showvalue=True,
                 command=lambda _v: None, troughcolor=SUNK, bg=SURFACE, fg=INK, highlightthickness=0,
                 activebackground=COPPER).pack(side="left", fill="x", expand=True, padx=12)
        tk.Label(line, text="Transparency", fg=INK2, bg=SURFACE).pack(side="right")
        self._button(parent, "Apply ANC / transparency level", lambda: self.set_transparency(level.get()), filled=False).pack(anchor="w", pady=(0, 12))
        tk.Label(parent, text="Anti-wind", fg=INK2, bg=SURFACE, font=("Segoe UI", 9)).pack(anchor="w")
        winds = tk.Frame(parent, bg=SURFACE)
        winds.pack(anchor="w", pady=(5, 0))
        for index, name in enumerate(("Off", "Auto", "Max")):
            self._button(winds, name, lambda i=index: self.set_antiwind(i), filled=index == self.state["antiwind"]).pack(side="left", padx=(0, 5))

    # ---- Equaliser -----------------------------------------------------

    def _eq_page(self) -> None:
        body = self._scroll_page("Equaliser", "Five-band graphic EQ. Drag a band, then apply the curve to the headphones.")
        card = self._card(body, "Graphic equaliser", "Range: −6.0 to +6.0 dB. The saved custom slots live on this Windows PC.")
        gains = [tk.IntVar(value=int(v)) for v in self.state["eq"]]
        faders = tk.Frame(card, bg=SURFACE)
        faders.pack(fill="x", pady=(8, 10))
        for index, frequency in enumerate(FREQUENCIES):
            column = tk.Frame(faders, bg=SURFACE)
            column.pack(side="left", fill="x", expand=True)
            tk.Label(column, textvariable=tk.StringVar(value=self._db(gains[index].get())), fg=INK2, bg=SURFACE,
                     font=("Consolas", 10)).pack()
            scale = tk.Scale(column, from_=60, to=-60, variable=gains[index], orient="vertical", length=240,
                             showvalue=False, resolution=1, troughcolor=SUNK, bg=SURFACE, fg=COPPER,
                             activebackground=COPPER, highlightthickness=0)
            scale.pack()
            tk.Label(column, text=frequency, fg=INK, bg=SURFACE, font=("Segoe UI", 10, "bold")).pack()
        actions = tk.Frame(card, bg=SURFACE)
        actions.pack(fill="x", pady=(10, 0))
        self._button(actions, "Apply curve", lambda: self.apply_eq([v.get() for v in gains])).pack(side="left")
        self._button(actions, "Flat", lambda: self.apply_eq([0, 0, 0, 0, 0]), filled=False).pack(side="left", padx=6)
        self._button(actions, "Save custom preset", lambda: self.save_custom([v.get() for v in gains]), filled=False).pack(side="right")

        presets = self._card(body, "Presets")
        names = [name for name, _curve in EQ_PRESETS] + [item["name"] for item in self._custom_presets()]
        choice = tk.StringVar(value=self._preset_name())
        combo = ttk.Combobox(presets, textvariable=choice, values=names, state="readonly", width=28)
        combo.pack(side="left", pady=5)
        self._button(presets, "Apply preset", lambda: self.apply_named_preset(choice.get())).pack(side="left", padx=8)
        toggles = tk.Frame(body, bg=BG)
        toggles.pack(fill="x")
        left = self._card(toggles, "Bass boost")
        left.pack(side="left", fill="both", expand=True, padx=(0, 4))
        self._switch(left, "Bass boost", "bass", lambda on: self.set_bool(0x1008, on, "bass"))
        right = self._card(toggles, "Speech clarity", "Turn off Hi-Res first.")
        right.pack(side="left", fill="both", expand=True, padx=(4, 0))
        self._switch(right, "Speech clarity", "speech", self.set_speech)

    # ---- Listening -----------------------------------------------------

    def _listening_page(self) -> None:
        body = self._scroll_page("Listening", "Playback, calls and headphone behaviour.")
        playback = self._card(body, "Playback")
        self._switch(playback, "Smart pause", "smart_pause", lambda on: self.set_bool(0x080C, on, "smart_pause"), "Pause when you take them off")
        self._switch(playback, "Low latency", "low_latency", lambda on: self.set_bool(0x0817, on, "low_latency"))
        self._switch(playback, "Hi-Res 24/96", "hi_res", self.set_hires, "Takes effect after a restart")
        calls = self._card(body, "Calls")
        self._switch(calls, "Auto-answer calls", "auto_answer", lambda on: self.set_bool(0x080A, on, "auto_answer"))
        self._switch(calls, "Comfort call", "comfort_call", lambda on: self.set_bool(0x0814, on, "comfort_call"))
        call_level = tk.IntVar(value=int(self.state["call_transparency"]))
        row = tk.Frame(calls, bg=SURFACE)
        row.pack(fill="x", pady=(10, 0))
        tk.Label(row, text="Transparency during calls", fg=INK, bg=SURFACE).pack(anchor="w")
        tk.Scale(row, from_=0, to=4, orient="horizontal", variable=call_level, showvalue=True, resolution=1,
                 troughcolor=SUNK, bg=SURFACE, fg=INK, activebackground=COPPER, highlightthickness=0).pack(side="left", fill="x", expand=True)
        self._button(row, "Apply", lambda: self.set_call_transparency(call_level.get()), filled=False).pack(side="right", padx=(10, 0))
        phones = self._card(body, "Headphones")
        self._switch(phones, "On-head detection", "on_head", lambda on: self.set_bool(0x0400, on, "on_head"))
        self._switch(phones, "Touch controls", "touch", self.set_touch)

    # ---- Guard ---------------------------------------------------------

    def _guard_page(self) -> None:
        body = self._scroll_page("Auto power-off", "Switches the headphones off after the chosen time without audible Windows audio.")
        card = self._card(body, "Idle guard", "It does not keep the control channel open while waiting. Any detected output audio resets the timer.")
        value_row = tk.Frame(card, bg=SURFACE)
        value_row.pack(fill="x", pady=(4, 12))
        tk.Label(value_row, text="Idle time", fg=INK, bg=SURFACE).pack(side="left")
        minutes = tk.IntVar(value=int(self.settings.get("guard_minutes", 15)))
        tk.Spinbox(value_row, from_=1, to=240, textvariable=minutes, width=6, bg=FIELD, fg=INK,
                   buttonbackground=SUNK, relief="flat", font=("Consolas", 11)).pack(side="left", padx=8)
        tk.Label(value_row, text="minutes", fg=INK2, bg=SURFACE).pack(side="left")
        auto = tk.BooleanVar(value=bool(self.settings.get("guard_autostart", False)))
        tk.Checkbutton(card, text="Start guard when the app opens", variable=auto, bg=SURFACE, fg=INK,
                       selectcolor=COPPER, activebackground=SURFACE, activeforeground=INK, highlightthickness=0,
                       command=lambda: self.save_settings(guard_autostart=auto.get())).pack(anchor="w", pady=(2, 10))
        tk.Label(card, text=self.guard_status, fg=COPPER if self.guard_running else INK2, bg=SURFACE,
                 font=("Segoe UI", 10)).pack(anchor="w", pady=(0, 10))
        action = "Stop guard" if self.guard_running else "Start guard"
        self._button(card, action, lambda: self.toggle_guard(minutes.get())).pack(anchor="w")
        details = self._card(body, "How it works", "The guard samples the Windows Core Audio output meter. It resets the countdown while audio is audible. If Core Audio cannot be read, the guard fails closed and never powers the headphones off.")
        details.pack(fill="x")

    # ---- Device / logs -------------------------------------------------

    def _device_page(self) -> None:
        body = self._scroll_page("Device", "Connect to read your headphone state. Windows Bluetooth pairing remains managed by Windows.")
        address = self._card(body, "Headphone connection")
        row = tk.Frame(address, bg=SURFACE)
        row.pack(fill="x", pady=(6, 7))
        entry = tk.Entry(row, textvariable=self.address, bg=FIELD, fg=INK, insertbackground=INK,
                         relief="flat", highlightthickness=1, highlightbackground=RULE, font=("Consolas", 11))
        entry.pack(side="left", fill="x", expand=True, ipady=7)
        self._button(row, "Save", self.save_address, filled=False).pack(side="left", padx=8)
        self._button(row, "Connect and refresh", self.refresh_all).pack(side="left")
        tk.Label(address, text="Find the address in Windows Settings → Bluetooth & devices → your headphones → Properties.",
                 fg=INK2, bg=SURFACE, font=("Segoe UI", 8)).pack(anchor="w")
        info = self._card(body, "Info")
        for label, key in (("Battery", "battery"), ("Model", "model"), ("Firmware", "firmware"), ("Hardware", "hardware")):
            self._info_row(info, label, self.state[key])
        advanced = self._card(body, "Advanced")
        timer_row = tk.Frame(advanced, bg=SURFACE)
        timer_row.pack(fill="x", pady=(6, 12))
        tk.Label(timer_row, text="Firmware timer (seconds)", fg=INK, bg=SURFACE).pack(side="left")
        seconds = tk.IntVar(value=int(self.state["timer"]))
        tk.Spinbox(timer_row, from_=0, to=3600, textvariable=seconds, width=8, bg=FIELD, fg=INK,
                   buttonbackground=SUNK, relief="flat").pack(side="left", padx=8)
        self._button(timer_row, "Set", lambda: self.set_timer(seconds.get()), filled=False).pack(side="left")
        raw = tk.Frame(advanced, bg=SURFACE)
        raw.pack(fill="x", pady=(0, 10))
        raw_value = tk.StringVar(value="")
        tk.Entry(raw, textvariable=raw_value, bg=FIELD, fg=INK, insertbackground=INK, relief="flat",
                 highlightthickness=1, highlightbackground=RULE, font=("Consolas", 10)).pack(side="left", fill="x", expand=True, ipady=7)
        self._button(raw, "Send documented frame", lambda: self.send_raw(raw_value.get()), filled=False).pack(side="left", padx=(8, 0))
        tools = tk.Frame(advanced, bg=SURFACE)
        tools.pack(fill="x")
        self._button(tools, "Read all config", self.refresh_all, filled=False).pack(side="left")
        self._button(tools, "Copy report", self.copy_report, filled=False).pack(side="left", padx=7)

    def _logs_page(self) -> None:
        body = self._scroll_page("Logs", "Connection logs are stored locally under %LOCALAPPDATA%\\Momentum4Companion\\logs.")
        card = self._card(body, "This session")
        text = tk.Text(card, height=24, bg=FIELD, fg=INK2, insertbackground=INK, relief="flat", wrap="word",
                       font=("Consolas", 9))
        text.pack(fill="both", expand=True, pady=(8, 0))
        text.insert("1.0", "\n".join(self.app_log[-250:]) or "No activity yet.")
        text.configure(state="disabled")
        self._button(card, "Copy visible log", lambda: self.copy_text("\n".join(self.app_log[-250:])), filled=False).pack(anchor="w", pady=(9, 0))

    def _info_row(self, parent: tk.Widget, name: str, value: str) -> None:
        row = tk.Frame(parent, bg=SURFACE)
        row.pack(fill="x", pady=5)
        tk.Label(row, text=name, fg=INK2, bg=SURFACE).pack(side="left")
        tk.Label(row, text=value, fg=INK, bg=SURFACE, font=("Consolas", 10)).pack(side="right")

    # ---- Connection and transport -------------------------------------

    def save_settings(self, **extra) -> None:
        values = dict(self.settings)
        values.update({"address": self.address.get().strip().upper(), "noise": self.state["noise"],
                       "eq_index": self.state["eq_index"]})
        values.update(extra)
        self.settings = values
        APP_DATA.mkdir(parents=True, exist_ok=True)
        temporary = SETTINGS.with_suffix(".tmp")
        temporary.write_text(json.dumps(values, indent=2), encoding="utf-8")
        temporary.replace(SETTINGS)

    def save_address(self) -> None:
        address = self.address.get().strip().upper()
        if not ADDRESS_RE.fullmatch(address):
            messagebox.showerror(APP_NAME, "Enter the Bluetooth address as AA:BB:CC:DD:EE:FF.")
            return
        self.address.set(address)
        self.save_settings()
        self.set_status("Address saved. Connect to read the headphones.")

    def _address_or_error(self) -> str | None:
        address = self.address.get().strip().upper()
        if not ADDRESS_RE.fullmatch(address):
            self.root.after(0, lambda: messagebox.showerror(APP_NAME, "Enter and save a valid Bluetooth address first."))
            return None
        return address

    def _worker(self, label: str, task, allow_parallel: bool = False) -> None:
        if self.busy and not allow_parallel:
            self.set_status("Another headset action is still running.")
            return
        address = self._address_or_error()
        if not address:
            return
        self.busy = True
        self.set_status(label + "…")

        def run() -> None:
            logger = None
            link = None
            try:
                logger = Gaia.Logger(str(LOGS))
                link = Gaia.Link(address, logger)
                if not link.connect(timeout=8.0):
                    raise RuntimeError("Could not open the GAIA control channel. Make sure the headphones are on and no phone control app holds it.")
                task(link)
                self._ui(lambda: self.set_status(label + " complete."))
                self._ui(self._refresh_current_page)
            except Exception as exc:
                self._log(f"{label}: {exc}")
                self._ui(lambda: self.set_status(label + " failed: " + str(exc)))
            finally:
                if link:
                    link.close()
                if logger:
                    logger.close()
                self.busy = False
        threading.Thread(target=run, name="m4-action", daemon=True).start()

    def _ui(self, fn) -> None:
        try:
            self.root.after(0, fn)
        except tk.TclError:
            pass

    def _log(self, message: str) -> None:
        stamp = time.strftime("%H:%M:%S")
        self.app_log.append(f"{stamp}  {message}")

    def set_status(self, text: str) -> None:
        self.status.set(text)
        connected = text.lower().startswith(("connected", "reading", "applying", "setting", "noise", "cycling")) and "failed" not in text.lower()
        self.connection_dot.configure(text="●  Connected" if connected else "●  Disconnected", fg=COPPER if connected else INK2)

    def _request(self, link: Gaia.Link, cmd: int, payload: bytes = b"", timeout: float = 1.4):
        reply, frames = Gaia.transact(link, Gaia.guarded_frame(cmd, payload), cmd, timeout=timeout, settle=0.08)
        if reply is not None and reply.type == 3:
            code = reply.payload[0] if reply.payload else 0
            raise RuntimeError(f"{cmd:04X} refused by headphones ({code:02X})")
        return reply, frames

    def _send(self, link: Gaia.Link, cmd: int, payload: bytes = b"") -> None:
        self._request(link, cmd, payload, timeout=1.2)

    def _send_batch(self, link: Gaia.Link, frames: list[tuple[int, bytes]]) -> None:
        """Apply related writes with the Android app's 60 ms framing gap.

        The headset does not reliably acknowledge every SET request. Waiting a
        full request timeout after each EQ band made a five-band preset feel
        broken. The Android app writes the complete curve, pauses briefly, and
        only then reads any replies/notifications that arrived.
        """
        for command, payload in frames:
            frame = Gaia.guarded_frame(command, payload)
            if not link.send(frame):
                raise RuntimeError(f"Transport refused {command:04X}")
            time.sleep(0.06)
        replies = link.read_frames(0.4)
        if replies is None:
            raise Gaia.LinkDropped("headphones closed the control link")
        for reply in replies:
            self._apply_frame(reply)

    def refresh_all(self) -> None:
        self.save_address()
        gets = ((0x1A05, b""), (0x1A01, b""), (0x1A03, b""), (0x080D, b""), (0x080B, b""),
                (0x0818, b""), (0x0815, b""), (0x1607, b""), (0x0806, b""), (0x0401, b""),
                (0x0406, b""), (0x1009, b""), (0x0804, b""), (0x0603, b""), (0x1206, b""),
                (0x1201, b""), (0x1200, b""), (0x0601, b"\x00"), (0x1003, b"\x00"))
        def read(link: Gaia.Link) -> None:
            for cmd, payload in gets:
                reply, frames = self._request(link, cmd, payload)
                if reply:
                    self._apply_frame(reply)
                for frame in frames:
                    self._apply_frame(frame)
            self._log("Read full headset configuration")
        self._worker("Reading headphone settings", read)

    def disconnect(self) -> None:
        self.set_status("Disconnected. The headset remains paired in Windows.")

    def _apply_frame(self, frame) -> None:
        if frame.vendor != Gaia.SENNHEISER or frame.type == 3:
            return
        p = frame.payload
        b0 = p[0] if p else None
        if frame.base == 0x0603 and p: self.state["battery"] = f"{p[-1]}%"
        elif frame.base == 0x1206: self.state["model"] = p.split(b"\0")[0].decode("ascii", "replace") or "MOMENTUM 4"
        elif frame.base == 0x1201 and len(p) >= 6: self.state["firmware"] = f"{(p[0]<<8)|p[1]}.{(p[2]<<8)|p[3]}.{(p[4]<<8)|p[5]}"
        elif frame.base == 0x1200 and len(p) >= 2: self.state["hardware"] = str((p[0] << 8) | p[1])
        elif frame.base == 0x0601 and len(p) >= 3: self.state["timer"] = (p[1] << 8) | p[2]
        elif frame.base == 0x1A05 and b0 is not None: self.state["noise"] = 1 if b0 else 2
        elif frame.base == 0x1A01:
            for i in range(0, len(p) - 1, 2):
                if p[i] == 3 and p[i + 1] == 1: self.state["noise"] = 0
                if p[i] == 1 and p[i + 1] <= 2: self.state["antiwind"] = {0: 0, 2: 1, 1: 2}[p[i + 1]]
        elif frame.base == 0x1A03 and b0 is not None: self.state["transparency"] = b0
        elif frame.base == 0x080D and b0 is not None: self.state["smart_pause"] = b0 == 1
        elif frame.base == 0x080B and b0 is not None: self.state["auto_answer"] = b0 == 1
        elif frame.base == 0x0818 and b0 is not None: self.state["low_latency"] = b0 == 1
        elif frame.base == 0x0815 and b0 is not None: self.state["comfort_call"] = b0 == 1
        elif frame.base == 0x0401 and b0 is not None: self.state["on_head"] = b0 == 1
        elif frame.base == 0x0406 and b0 is not None: self.state["hi_res"] = b0 == 0
        elif frame.base == 0x1607 and b0 is not None: self.state["touch"] = b0 == 0
        elif frame.base == 0x0806 and b0 is not None: self.state["call_transparency"] = b0
        elif frame.base == 0x1009 and b0 is not None: self.state["bass"] = b0 == 1
        elif frame.base == 0x0804 and len(p) >= 2: self.state["speech"] = ((p[0] << 8) | p[1]) == 2
        elif frame.base in (0x1003, 0x1002) and len(p) >= 5:
            self.state["eq"] = [x - 256 if x > 127 else x for x in p[:5]]

    # ---- Headset actions ----------------------------------------------

    def set_noise(self, index: int) -> None:
        level = int(self.state["transparency"])
        def change(link: Gaia.Link) -> None:
            if index == 0:
                self._send_batch(link, [(0x1A04, b"\x01"), (0x1A00, b"\x03\x01")])
            elif index == 1:
                self._send_batch(link, [(0x1A00, b"\x03\x00"), (0x1A04, b"\x01"), (0x1A02, bytes([level]))])
            else:
                self._send_batch(link, [(0x1A00, b"\x03\x00"), (0x1A04, b"\x00")])
            self.state["noise"] = index; self.save_settings(); self._log("Noise control set to " + NOISE_MODES[index])
        self._worker("Setting noise control", change)

    def set_transparency(self, value: int) -> None:
        def change(link: Gaia.Link) -> None:
            self._send(link, 0x1A02, bytes([value])); self.state["transparency"] = value
        self._worker("Applying transparency level", change)

    def set_antiwind(self, index: int) -> None:
        wire = (0, 2, 1)[index]
        def change(link: Gaia.Link) -> None:
            self._send(link, 0x1A00, bytes([1, wire])); self.state["antiwind"] = index
        self._worker("Setting anti-wind", change)

    def cycle_noise(self) -> None:
        self.set_noise((int(self.state["noise"]) + 1) % len(NOISE_MODES))

    def apply_eq(self, gains: list[int]) -> None:
        gains = [max(-60, min(60, int(gain))) for gain in gains]
        def change(link: Gaia.Link) -> None:
            self._send_batch(link, [(0x1001, bytes([band, gain & 0xFF])) for band, gain in enumerate(gains)])
            self.state["eq"] = gains
            self.state["eq_index"] = next((i for i, (_n, curve) in enumerate(EQ_PRESETS) if list(curve) == gains), -1)
            self.save_settings(); self._log("Applied EQ curve: " + ", ".join(self._db(v) for v in gains))
        self._worker("Applying equaliser", change)

    def cycle_eq(self) -> None:
        index = (int(self.state["eq_index"]) + 1) % len(EQ_PRESETS)
        self.state["eq_index"] = index
        self.apply_eq(list(EQ_PRESETS[index][1]))

    def apply_named_preset(self, name: str) -> None:
        for preset_name, gains in EQ_PRESETS:
            if name == preset_name:
                self.apply_eq(list(gains)); return
        for item in self._custom_presets():
            if item["name"] == name:
                self.apply_eq(item["gains"]); return

    def set_bool(self, command: int, on: bool, key: str) -> None:
        def change(link: Gaia.Link) -> None:
            self._send(link, command, bytes([1 if on else 0])); self.state[key] = on
        self._worker("Applying " + key.replace("_", " "), change)

    def set_hires(self, on: bool) -> None:
        def change(link: Gaia.Link) -> None:
            self._send(link, 0x0405, bytes([0 if on else 1])); self.state["hi_res"] = on
        self._worker("Applying Hi-Res", change)

    def set_touch(self, on: bool) -> None:
        def change(link: Gaia.Link) -> None:
            self._send(link, 0x1606, bytes([0 if on else 1])); self.state["touch"] = on
        self._worker("Applying touch controls", change)

    def set_speech(self, on: bool) -> None:
        if on and self.state["hi_res"]:
            messagebox.showwarning(APP_NAME, "Turn off Hi-Res 24/96 before enabling speech clarity.")
            self.show_page("Equaliser")
            return
        def change(link: Gaia.Link) -> None:
            self._send(link, 0x0803, bytes([0, 2 if on else 1])); self.state["speech"] = on
        self._worker("Applying speech clarity", change)

    def set_call_transparency(self, value: int) -> None:
        def change(link: Gaia.Link) -> None:
            self._send(link, 0x0805, bytes([value])); self.state["call_transparency"] = value
        self._worker("Applying call transparency", change)

    def set_timer(self, seconds: int) -> None:
        seconds = max(0, min(3600, int(seconds)))
        def change(link: Gaia.Link) -> None:
            self._send(link, 0x0600, bytes([0, seconds >> 8, seconds & 0xFF])); self.state["timer"] = seconds
        self._worker("Setting firmware timer", change)

    def send_raw(self, text: str) -> None:
        try:
            frame = Gaia.parse_hex(text)
            if Gaia.single_frame_problem(frame): raise ValueError(Gaia.single_frame_problem(frame))
            vendor = (frame[4] << 8) | frame[5]
            cmd = (frame[6] << 8) | frame[7]
            reason = Gaia.M4.documented_reason(vendor, cmd, frame[8:]) or Gaia.blocked_reason(frame)
            if reason: raise ValueError(reason)
        except ValueError as exc:
            messagebox.showerror(APP_NAME, "Frame refused: " + str(exc)); return
        def change(link: Gaia.Link) -> None:
            if not link.send(frame): raise RuntimeError("Transport refused the frame")
            link.read_frames(1.0); self._log("Sent documented raw frame " + Gaia.hexstr(frame))
        self._worker("Sending documented frame", change)

    # ---- Custom presets / report --------------------------------------

    def _custom_presets(self) -> list[dict]:
        raw = self.settings.get("custom_presets", [])
        return [item for item in raw if isinstance(item, dict) and isinstance(item.get("name"), str) and isinstance(item.get("gains"), list) and len(item["gains"]) == 5]

    def save_custom(self, gains: list[int]) -> None:
        name = simpledialog.askstring(APP_NAME, "Name this custom preset:", parent=self.root)
        if not name: return
        presets = self._custom_presets()
        if len(presets) >= 5: presets.pop(0)
        presets.append({"name": name.strip()[:40] or "Custom", "gains": [int(v) for v in gains]})
        self.save_settings(custom_presets=presets)
        self.set_status("Saved custom preset " + presets[-1]["name"] + ".")
        self.show_page("Equaliser")

    def _preset_name(self) -> str:
        index = int(self.state["eq_index"])
        if 0 <= index < len(EQ_PRESETS): return EQ_PRESETS[index][0]
        for item in self._custom_presets():
            if item["gains"] == self.state["eq"]: return item["name"]
        return "Custom curve"

    def _db(self, gain: int) -> str:
        return f"{gain / 10:+.1f}"

    def _listening_summary(self) -> str:
        keys = ("smart_pause", "auto_answer", "low_latency", "hi_res", "comfort_call", "on_head", "touch")
        return f"{sum(bool(self.state[k]) for k in keys)} of {len(keys)} on"

    def _guard_summary(self) -> str:
        return "Armed" if self.guard_running else "Off"

    def _device_summary(self) -> str:
        return f"{self.state['model']} · hw {self.state['hardware']}" if self.state["hardware"] != "—" else "Connect to read info"

    def copy_text(self, value: str) -> None:
        self.root.clipboard_clear(); self.root.clipboard_append(value); self.set_status("Copied to clipboard.")

    def copy_report(self) -> None:
        report = (f"MOMENTUM 4 report\nAddress: {self.address.get()}\nBattery: {self.state['battery']}\n"
                  f"Model: {self.state['model']}\nFirmware: {self.state['firmware']}\nHardware: {self.state['hardware']}\n"
                  f"Noise: {NOISE_MODES[self.state['noise']]}\nEQ: {self._preset_name()}\n"
                  f"Guard: {self._guard_summary()}\n")
        self.copy_text(report)

    # ---- Guard implementation -----------------------------------------

    @staticmethod
    def _windows_audio_active() -> bool | None:
        """True for audible Core Audio output, False for silence, None if unavailable.

        None deliberately fails closed in the guard: a missing audio meter must
        never result in a surprise power-off.
        """
        try:
            from pycaw.pycaw import AudioUtilities, IAudioMeterInformation
            for session in AudioUtilities.GetAllSessions():
                meter = session._ctl.QueryInterface(IAudioMeterInformation)
                if meter.GetPeakValue() > 0.002:
                    return True
            return False
        except Exception:
            return None

    def _autoload_guard(self) -> None:
        if self.settings.get("guard_autostart") and ADDRESS_RE.fullmatch(self.address.get().strip()):
            self.start_guard(int(self.settings.get("guard_minutes", 15)))

    def toggle_guard(self, minutes: int) -> None:
        if self.guard_running: self.stop_guard()
        else: self.start_guard(minutes)

    def start_guard(self, minutes: int) -> None:
        if not self._address_or_error(): return
        minutes = max(1, min(240, int(minutes)))
        self.save_settings(guard_minutes=minutes)
        self.guard_stop.clear(); self.guard_running = True
        self.guard_status = f"Armed · waiting for {minutes} minutes of silence"
        self._log("Idle guard started for " + str(minutes) + " minute(s)")

        def monitor() -> None:
            idle_from = time.monotonic()
            timeout = minutes * 60
            while not self.guard_stop.wait(5):
                active = self._windows_audio_active()
                if active is None:
                    self.guard_status = "Guard paused: Windows Core Audio meter is unavailable"
                    self._ui(self._refresh_current_page)
                    continue
                if active:
                    idle_from = time.monotonic()
                    self.guard_status = "Audio detected · idle countdown reset"
                    self._ui(self._refresh_current_page)
                    continue
                left = max(0, int(timeout - (time.monotonic() - idle_from)))
                self.guard_status = f"Silent · switches off in {(left + 59) // 60} min"
                self._ui(self._refresh_current_page)
                if time.monotonic() - idle_from >= timeout:
                    self._guard_power_off(); break
            self.guard_running = False
            self._ui(self._refresh_current_page)
        threading.Thread(target=monitor, name="m4-idle-guard", daemon=True).start()
        self._refresh_current_page()

    def stop_guard(self) -> None:
        self.guard_stop.set(); self.guard_running = False; self.guard_status = "Guard stopped"; self._log("Idle guard stopped")
        self._refresh_current_page()

    def _guard_power_off(self) -> None:
        address = self._address_or_error()
        if not address: return
        logger = link = None
        try:
            logger = Gaia.Logger(str(LOGS)); link = Gaia.Link(address, logger)
            if not link.connect(timeout=8.0): raise RuntimeError("headphones were unreachable")
            # 0605 is intentionally available only to this explicit guard and the confirmed button.
            if not link.send(Gaia.build(Gaia.SENNHEISER, 0x0605)): raise RuntimeError("transport refused power-off")
            self.guard_status = "Power-off sent. Wake the headphones with their button."
            self._log("Idle guard sent power-off (0605)")
        except Exception as exc:
            self.guard_status = "Guard could not reach headphones: " + str(exc)
            self._log(self.guard_status)
        finally:
            if link: link.close()
            if logger: logger.close()

    def confirm_power_off(self) -> None:
        if not messagebox.askyesno(APP_NAME, "Power off the headphones?\n\nThey switch off fully. Use the physical headphone button to wake them.", icon="warning"):
            return
        def power(link: Gaia.Link) -> None:
            if not link.send(Gaia.build(Gaia.SENNHEISER, 0x0605)):
                raise RuntimeError("transport refused the power-off command")
            self._log("User confirmed power-off (0605)")
        self._worker("Powering off headphones", power)

    def _refresh_current_page(self) -> None:
        if self.root.winfo_exists(): self.show_page(self.page)

    def run(self) -> None:
        self.root.mainloop()


if __name__ == "__main__":
    CompanionApp().run()

