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

./gradlew :composeApp:createDistributable "$@"
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
cp -a "$ROOT/composeApp/build/compose/binaries/main/app/visage" "$APPDIR/opt/visage"
cp "$ROOT/composeApp/src/desktopMain/resources/visage_icon.png" "$APPDIR/visage.png"
cat > "$APPDIR/visage.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=VIsage
Comment=YouTube Music desktop player
Exec=visage
Icon=visage
Categories=AudioVideo;Audio;Player;
Terminal=false
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
