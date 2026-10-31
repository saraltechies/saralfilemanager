#!/bin/sh
# Renders store graphics from store.html with headless Chrome. Run from store.
CHROME=${CHROME:-"/c/Program Files/Google/Chrome/Application/chrome.exe"}
SRC="file:///$(cd graphics-src && pwd -W)/store.html"
r() { "$CHROME" --headless=new --hide-scrollbars --force-device-scale-factor=1 --virtual-time-budget=10000 \
        --window-size="$2" --screenshot="$(pwd -W)/graphics/$3" "$SRC#$1" 2>/dev/null; }
r icon 512,512 icon-512.png
r feature 1024,500 feature-graphic-1024x500.png
for i in 1 2 3 4 5; do r s$i 1080,1920 phone-screenshot-$i.png; done
