# VIsage Flatpak

Manifest: `io.github.vndreiii.Visage.yml` (app id `io.github.vndreiii.Visage`, runtime `org.freedesktop.Platform` / `org.freedesktop.Sdk` 24.08).

## Layout

| File | Purpose |
| --- | --- |
| `io.github.vndreiii.Visage.yml` | Manifest: builds FFmpeg, libmpv, and installs the Compose distributable |
| `visage.sh` | `/app/bin/visage` launcher (`LC_NUMERIC=C`, `DISABLE_LSFGVK=1`, `LD_LIBRARY_PATH`, `VISAGE_NATIVE_LIB`) |
| `io.github.vndreiii.Visage.desktop` | Desktop entry (`StartupWMClass=visage`, `Icon=io.github.vndreiii.Visage`) |
| `io.github.vndreiii.Visage.metainfo.xml` | AppStream metadata |
| `../icons/` | Icon sources (PNG 16–512 and SVG), shared with the other packaging |
| `dist/` | Staged Compose distributable (`dist/visage/{bin,lib}`), not committed |

The app payload is installed to `/app/lib/visage`. The manifest's `dir` source
`dist/visage` is relative to this directory, so the staged path is
`packaging/flatpak/dist/visage`.

## Building in CI

`.github/workflows/release.yml` (job `flatpak`, matrix x86_64 and aarch64) does the following:

1. Extracts `visage-<version>-linux-<arch>.tar.gz` into `packaging/flatpak/dist/`.
2. Runs `flatpak/flatpak-github-actions/flatpak-builder@v6` with the manifest. Under the hood this is
   `flatpak-builder --user --install-deps-from=flathub` followed by export to a local repo.
3. Runs `flatpak build-bundle` to produce `VIsage-<arch>.flatpak`, which is uploaded as an artifact
   and attached to the release.

## Building locally

Requirements: `flatpak`, `flatpak-builder`, and the Flathub remote.

```sh
# 1. Runtime and SDK (once)
flatpak remote-add --if-not-exists --user flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install --user -y flathub org.freedesktop.Platform//24.08 org.freedesktop.Sdk//24.08

# 2. Stage the Compose distributable (from the repo root, after the Gradle build)
#    Use the packaged tar.gz, or copy the jpackage/createDistributable output:
mkdir -p packaging/flatpak/dist
cp -a composeApp/build/compose/binaries/main/app/visage packaging/flatpak/dist/

# 3. Build, install, and run
cd packaging/flatpak
flatpak-builder --user --install --force-clean --install-deps-from=flathub \
  build-dir io.github.vndreiii.Visage.yml
flatpak run io.github.vndreiii.Visage

# 4. Optional: single-file bundle (x86_64 example)
flatpak-builder --user --force-clean --repo=repo --install-deps-from=flathub \
  build-dir io.github.vndreiii.Visage.yml
flatpak build-bundle repo VIsage-x86_64.flatpak io.github.vndreiii.Visage
```

To produce the optional native helper, copy `libvisage_native.so` into
`packaging/flatpak/dist/visage/lib/app/resources/` before building. `visage.sh` sets
`VISAGE_NATIVE_LIB` only when that file exists.

## Notes

- The MPRIS integration and the desktop-entry writer in `MprisDesktopIntegration` skip writing into
  `~/.local/share` when `FLATPAK_ID` is set, so the Flatpak's own desktop file and icons are used.
- Permissions are declared in `finish-args` of the manifest. Credentials use Secret Service, and
  music folders are reachable via `xdg-music` and `xdg-download`.
- The `ffmpeg` and `mpv` tags (`n7.1`, `v0.39.0`) are not pinned to a commit. For reproducible builds,
  add `commit:` with the full hash.
- `metainfo.xml` has a placeholder `project_license` (`LicenseRef-proprietary`) because the repo has no
  LICENSE file. The release date and version (`0.1.0`) should be updated per release. Screenshots are
  recommended by Flathub but not included.
