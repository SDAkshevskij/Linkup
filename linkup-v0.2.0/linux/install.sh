#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! /usr/bin/python3 -c 'from PyQt5 import QtWebEngineWidgets, QtWebChannel' || ! command -v xprintidle >/dev/null || ! command -v xprop >/dev/null; then
  sudo apt-get install -y python3-pyqt5 python3-pyqt5.qtwebengine python3-pyqt5.qtwebchannel x11-utils xprintidle
fi
/usr/bin/python3 linux/updater.py install
BASE="$HOME/.local/opt/linkup"; mkdir -p "$BASE" "$HOME/.local/share/applications" "$HOME/.config/autostart" "$HOME/.local/bin"
cat > "$HOME/.local/bin/linkup" <<RUN
#!/bin/sh
exec /usr/bin/python3 "$BASE/current/linux/app.py" "\$@"
RUN
chmod +x "$HOME/.local/bin/linkup"
cat > "$HOME/.local/share/applications/linkup.desktop" <<DESK
[Desktop Entry]
Type=Application
Name=LinkUp
Exec="$HOME/.local/bin/linkup"
Icon=$BASE/current/ui/assets/icon-192.png
Terminal=false
Categories=Utility;
DESK
cat > "$HOME/.config/autostart/linkup-agent.desktop" <<DESK
[Desktop Entry]
Type=Application
Name=LinkUp Agent
Exec=/usr/bin/python3 "$BASE/current/linux/agent.py"
Terminal=false
X-GNOME-Autostart-enabled=true
DESK
printf 'LinkUp installed. Run %s/.local/bin/linkup\n' "$HOME"
