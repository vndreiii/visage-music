#!/usr/bin/env bash
# Put the standalone yt-dlp for this CPU in composeApp/build/linux-deps/yt-dlp, checked against the release's SHA2-256SUMS.
# Gradle stages that folder into the app's resources. Skips the download when the file is already there.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/composeApp/build/linux-deps"
case "$(uname -m)" in
  x86_64) ASSET="yt-dlp_linux" ;;
  aarch64) ASSET="yt-dlp_linux_aarch64" ;;
  *) echo "No standalone yt-dlp for $(uname -m); packaging without it" >&2; exit 0 ;;
esac
[[ -x "$DEST/yt-dlp" ]] && exit 0
mkdir -p "$DEST"
BASE="https://github.com/yt-dlp/yt-dlp/releases/latest/download"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -fL --retry 2 -o "$TMP/$ASSET" "$BASE/$ASSET"
curl -fL --retry 2 -o "$TMP/SHA2-256SUMS" "$BASE/SHA2-256SUMS"
WANT="$(awk -v a="$ASSET" '$2 == a || $2 == "*" a { print $1; exit }' "$TMP/SHA2-256SUMS")"
HAVE="$(sha256sum "$TMP/$ASSET" | cut -d' ' -f1)"
if [[ -z "$WANT" || "$WANT" != "$HAVE" ]]; then
  echo "yt-dlp checksum mismatch: expected [$WANT], got [$HAVE]" >&2
  exit 1
fi
install -m755 "$TMP/$ASSET" "$DEST/yt-dlp"
echo "yt-dlp bundled from $BASE/$ASSET"
