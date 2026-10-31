#!/usr/bin/env bash
# Unpack a pinned JetBrains Runtime SDK (JBR 25, no JCEF) into build/jbr/ and print its path on stdout.
# Stock OpenJDK has no Wayland toolkit; JBR ships WLToolkit (native Wayland) and the window-move API.
# The Gradle packaging picks build/jbr/jbrsdk-* up automatically (VISAGE_JBR_HOME overrides it), and
# packaging/launcher/visage uses WLToolkit when a Wayland session is present. Skips the download when already unpacked.
# Set VISAGE_NO_JBR=1 to skip entirely (package with the stock JDK 21 toolchain, X11/XWayland only).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/build/jbr"
[[ "${VISAGE_NO_JBR:-0}" == 1 ]] && exit 0
VER="25.0.4.1-linux"
BUILD="b635.75"
case "$(uname -m)" in
  x86_64)  ARCH=x64;     SHA512="eb9806ffc8226a53ece596730de2a1d5923bee85cd0eeb0e30fa3f21a2c561b0dfe8c29d1c17d6675ba91649e9aa010d85f4fb428bfc96a035d104a23e4e2f50" ;;
  aarch64) ARCH=aarch64; SHA512="f34ec0721110c2ee539854dd4f7ecb4c5f6700daa0f97bc996f18e0b44e359c75fd946799fbdda7c454978a1780f6f5f7348de51c18d44f5d2c482c60a3220a4" ;;
  *) echo "No JetBrains Runtime for $(uname -m); packaging with the stock JDK" >&2; exit 0 ;;
esac
NAME="jbrsdk-$VER-$ARCH-$BUILD"
if [[ -x "$DEST/$NAME/bin/java" ]]; then echo "$DEST/$NAME"; exit 0; fi
mkdir -p "$DEST"
TMP="$(mktemp -d "$DEST/dl.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
URL="${VISAGE_JBR_URL:-https://cache-redirector.jetbrains.com/intellij-jbr/$NAME.tar.gz}"
curl -fL --retry 2 -o "$TMP/jbr.tar.gz" "$URL" >&2
HAVE="$(sha512sum "$TMP/jbr.tar.gz" | cut -d' ' -f1)"
if [[ "$HAVE" != "$SHA512" ]]; then
  echo "JBR checksum mismatch: expected [$SHA512], got [$HAVE]" >&2
  exit 1
fi
tar -xzf "$TMP/jbr.tar.gz" -C "$TMP"
mv "$TMP/$NAME" "$DEST/$NAME"
echo "$DEST/$NAME"
