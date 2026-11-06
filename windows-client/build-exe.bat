@echo off
setlocal
cd /d "%~dp0\.."
py -3 -m PyInstaller --noconfirm --clean --onefile --windowed --name Momentum4Companion --paths windows-client --collect-all pystray --collect-all PIL --collect-all pycaw --collect-all comtypes --add-data "%CD%\tools\m4_commands.py;tools" --distpath "windows-client\dist" --workpath "%TEMP%\m4-companion-build" --specpath "%TEMP%\m4-companion-spec" windows-client\tray_app.py
if errorlevel 1 exit /b %errorlevel%
echo Built windows-client\dist\Momentum4Companion.exe


