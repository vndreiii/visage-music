#!/usr/bin/env bash
# Build every Linux artifact except the Flatpak: .deb, .rpm, .tar.gz and .AppImage -> dist/
# Usage: VISAGE_VERSION=1.2.3 scripts/package-linux.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
ARCH="$(uname -m)"
VERSION="${VISAGE_VERSION:-1.0.2}"
VERSION="${VERSION#v}"
export VISAGE_VERSION="$VERSION"
# Compose/jpackage need a numeric MAJOR.MINOR.PATCH (strip -rc1 / -ci suffixes for the deb/rpm metadata).
PKG_VERSION="${VERSION%%-*}"
[[ "$PKG_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || PKG_VERSION="1.0.2"
export VISAGE_VERSION="$PKG_VERSION"

mkdir -p dist && rm -f dist/*.deb dist/*.rpm dist/*.tar.gz dist/*.AppImage

if command -v cargo >/dev/null 2>&1; then
  cargo build --release --manifest-path native/Cargo.toml
else
  echo "cargo not found: packaging without the native helpers" >&2
fi

scripts/fetch-ytdlp-linux.sh
./gradlew --no-daemon :composeApp:packageDeb :composeApp:packageRpm :composeApp:createDistributable

shopt -s nullglob
for f in composeApp/build/compose/binaries/main/deb/*.deb; do cp -v "$f" "dist/visage_${VERSION}_$( [[ $ARCH == x86_64 ]] && echo amd64 || echo arm64 ).deb"; done
for f in composeApp/build/compose/binaries/main/rpm/*.rpm; do cp -v "$f" "dist/visage-${VERSION}-1.${ARCH}.rpm"; done

APP="composeApp/build/compose/binaries/main/app/visage"
tar -C "$(dirname "$APP")" -czf "dist/visage-${VERSION}-linux-${ARCH}.tar.gz" visage
echo "Built dist/visage-${VERSION}-linux-${ARCH}.tar.gz"

# The AppImage reuses the same distributable.
APP_DIR="$ROOT/$APP" ./build-appimage.sh
cp -v "dist/VIsage-${ARCH}.AppImage" "dist/VIsage-${VERSION}-${ARCH}.AppImage"
ls -lh dist
