#!/usr/bin/env bash
# Compila WiwyTransfer y lo empaqueta como WiwyTransfer.app (sin Xcode).
# Uso: ./build-app.sh [debug|release]
set -euo pipefail

CONFIG="${1:-release}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ICON_SRC="$HERE/../assets/icon/icon.png"
APP="$HERE/WiwyTransfer.app"

echo "==> Compilando ($CONFIG)…"
swift build -c "$CONFIG" --package-path "$HERE"
BIN="$(swift build -c "$CONFIG" --package-path "$HERE" --show-bin-path)/WiwyTransfer"

echo "==> Construyendo el bundle…"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/WiwyTransfer"
cp "$HERE/Info.plist" "$APP/Contents/Info.plist"

# Icono .icns desde el PNG
if [[ -f "$ICON_SRC" ]]; then
    ICONSET="$(mktemp -d)/AppIcon.iconset"
    mkdir -p "$ICONSET"
    for s in 16 32 64 128 256 512; do
        sips -z $s $s        "$ICON_SRC" --out "$ICONSET/icon_${s}x${s}.png"      >/dev/null
        sips -z $((s*2)) $((s*2)) "$ICON_SRC" --out "$ICONSET/icon_${s}x${s}@2x.png" >/dev/null
    done
    iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/AppIcon.icns"
    rm -rf "$(dirname "$ICONSET")"
fi

# Firma ad-hoc (sin avisos en este Mac; necesaria para que la red local funcione bien)
codesign --force --deep --sign - "$APP" 2>/dev/null || true

echo "==> Listo: $APP"
echo "    Ábrelo con:  open \"$APP\""
