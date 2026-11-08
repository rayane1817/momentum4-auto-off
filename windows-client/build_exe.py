#!/usr/bin/env python3
"""Build M4Control.exe (the app, no console) and m4ctl.exe (command line) with PyInstaller.

    python -m pip install pyinstaller==6.11.1
    python build_exe.py            -> dist\\M4Control.exe, dist\\m4ctl.exe, dist\\SHA256SUMS

Both are single files with Python inside: nothing else to install. They are not signed, so
Windows SmartScreen asks once ("More info" > "Run anyway"). CI builds the same way
(.github/workflows/windows-app.yml).
"""

import hashlib
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import m4_logic as L  # noqa: E402
import m4_tray as T  # noqa: E402

BUILD, DIST = os.path.join(HERE, "build"), os.path.join(HERE, "dist")
TOOLS = os.path.normpath(os.path.join(HERE, "..", "tools"))


def pyinstaller(script, name, windowed, extra=()):
    cmd = [sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--onefile",
           "--name", name, "--icon", os.path.join(BUILD, "m4.ico"),
           "--paths", TOOLS, "--hidden-import", "m4_commands",
           "--distpath", DIST, "--workpath", os.path.join(BUILD, name), "--specpath", BUILD,
           "--windowed" if windowed else "--console"] + list(extra) + [os.path.join(HERE, script)]
    subprocess.run(cmd, check=True, cwd=HERE)


def main():
    os.makedirs(BUILD, exist_ok=True)
    with open(os.path.join(BUILD, "m4.ico"), "wb") as f:
        f.write(T.ico_file())
    ui = os.path.join(HERE, "ui", "index.html")
    pyinstaller("m4_app.py", "M4Control", True, ["--add-data", ui + os.pathsep + "ui"])
    pyinstaller("m4ctl.py", "m4ctl", False)
    lines = []
    for exe in ("M4Control.exe", "m4ctl.exe"):
        with open(os.path.join(DIST, exe), "rb") as f:
            lines.append("%s  %s" % (hashlib.sha256(f.read()).hexdigest(), exe))
    with open(os.path.join(DIST, "SHA256SUMS"), "w", encoding="ascii", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("M4 Control %s:" % L.APP_VERSION)
    print("\n".join(lines))


if __name__ == "__main__":
    main()
