#!/usr/bin/env bash
# Build a Linux test AppImage with a bundled Java runtime.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64|aarch64) ;;
  *) echo "Unsupported AppImage architecture: $ARCH" >&2; exit 1 ;;
esac

if [[ -z "${APP_DIR:-}" ]]; then
  "$ROOT/scripts/fetch-ytdlp-linux.sh"
  ./gradlew :composeApp:createDistributable "$@"
  APP_DIR="$ROOT/composeApp/build/compose/binaries/main/app/visage"
fi
mkdir -p "$ROOT/build/appimage/tools" "$ROOT/dist"
TOOL="${APPIMAGETOOL:-$ROOT/build/appimage/tools/appimagetool-$ARCH.AppImage}"
if [[ ! -x "$TOOL" ]]; then
  if [[ -n "${APPIMAGETOOL:-}" ]]; then
    echo "APPIMAGETOOL must point to an executable packaging tool" >&2
    exit 1
  fi
  curl -fL --retry 2 -o "$TOOL.download" \
    "https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-$ARCH.AppImage"
  chmod +x "$TOOL.download"
  mv "$TOOL.download" "$TOOL"
fi

STAGE="$(mktemp -d "$ROOT/build/appimage/stage.XXXXXX")"
APPDIR="$STAGE/visage.AppDir"
mkdir -p "$APPDIR/opt"
cp -a "$APP_DIR" "$APPDIR/opt/visage"
cp "$ROOT/packaging/icons/visage-256.png" "$APPDIR/visage.png"
cp "$APPDIR/visage.png" "$APPDIR/.DirIcon"
for size in 16 24 32 48 64 128 256 512; do
  install -Dm644 "$ROOT/packaging/icons/visage-$size.png" \
    "$APPDIR/usr/share/icons/hicolor/${size}x${size}/apps/visage.png"
done
install -Dm644 "$ROOT/packaging/icons/visage.svg" "$APPDIR/usr/share/icons/hicolor/scalable/apps/visage.svg"
cat > "$APPDIR/visage.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=VIsage
GenericName=Music Player
Comment=YouTube Music player for Linux
Exec=visage
Icon=visage
Categories=AudioVideo;Audio;Player;
Terminal=false
StartupWMClass=visage
EOF
cat > "$APPDIR/AppRun" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
APPDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export LC_NUMERIC=C
# Keep the game frame-generation layer out of libmpv/Chromium Vulkan probing.
export DISABLE_LSFGVK=1
if [[ -f "$APPDIR/opt/visage/lib/libvisage_native.so" ]]; then
  export VISAGE_NATIVE_LIB="$APPDIR/opt/visage/lib/libvisage_native.so"
fi
exec "$APPDIR/opt/visage/bin/visage" "$@"
EOF
chmod +x "$APPDIR/AppRun"
if [[ -f "$ROOT/native/target/release/libvisage_native.so" ]]; then
  cp "$ROOT/native/target/release/libvisage_native.so" "$APPDIR/opt/visage/lib/"
fi
ARCH="$ARCH" APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" \
  "$APPDIR" "$STAGE/VIsage-$ARCH.AppImage"
# Atomic replacement also works while an older AppImage is running.
mv -f "$STAGE/VIsage-$ARCH.AppImage" "$ROOT/dist/VIsage-$ARCH.AppImage"
echo "Built: $ROOT/dist/VIsage-$ARCH.AppImage"
