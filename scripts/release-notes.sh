#!/usr/bin/env bash
# Writes the text of a GitHub release: a "which file do I need" table, then the generated changelog.
# Usage: release-notes.sh <tag> [changelog-file]   (prints Markdown on stdout)
set -euo pipefail
tag="$1"; changelog="${2:-}"
v="${tag#v}"
repo="${GITHUB_REPOSITORY:-vndreiii/visage-music}"
d() { printf '[%s](https://github.com/%s/releases/download/%s/%s)' "$1" "$repo" "$tag" "$2"; }

cat <<MD
## Which file do I need?

| System | Easiest (recommended) | Other formats |
| --- | --- | --- |
| **Windows** (64-bit) | $(d "Installer (.exe)" "VIsage-$v-windows-x64-setup.exe") | $(d "MSI" "VIsage-x64.msi") · $(d "Portable (.zip)" "VIsage-$v-windows-x64-portable.zip") |
| **Linux** x86-64 (most PCs) | $(d "AppImage" "VIsage-x86_64.AppImage") | $(d ".deb" "visage_${v}_amd64.deb") (Debian, Ubuntu, Mint) · $(d ".rpm" "visage-$v-1.x86_64.rpm") (Fedora, openSUSE) · $(d "Arch" "visage-$v-1-x86_64.pkg.tar.zst") · $(d ".tar.gz" "visage-$v-linux-x86_64.tar.gz") |
| **Linux** ARM64 (Raspberry Pi 4/5, ARM laptops) | $(d "AppImage" "VIsage-aarch64.AppImage") | $(d ".deb" "visage_${v}_arm64.deb") · $(d ".rpm" "visage-$v-1.aarch64.rpm") · $(d ".tar.gz" "visage-$v-linux-aarch64.tar.gz") |

- **AppImage:** make it executable (\`chmod +x\`) and run it. It updates itself. The Windows installer and MSI update themselves too.
- **Not sure?** Windows: take the Installer. Linux: take the AppImage.
- **Linux without libmpv:** VIsage asks once and downloads it. On NixOS this is the normal path.
- Files named \`latest.json\`, \`SHA256SUMS\`, \`PKGBUILD\` and \`visage-libmpv-*\` are for the updater and for packagers. You do not need them.

MD
highlights="$(dirname "$0")/../packaging/release-highlights/$tag.md"
if [[ -s "$highlights" ]]; then
  echo "## Highlights"; echo; cat "$highlights"; echo
fi
if [[ -n "$changelog" && -s "$changelog" ]]; then
  echo "## What changed"; echo; cat "$changelog"
fi
