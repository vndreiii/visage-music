# VIsage

**VIsage** is a Linux music player for [YouTube Music](https://music.youtube.com). It is the desktop port of [VIVI Music](https://github.com/vivizzz007/vivi-music) (Android): same idea — stream YT Music with your own account — built as a native Compose Multiplatform app for the desktop.

Sign in once with Google (bundled browser + your Linux keyring). After that you get Home, Search, Library, a full Now Playing experience, lyrics, queue, MPRIS desktop integration, and optional offline audio saves.

## What you can do

- Browse Home shelves, Search, artists, albums, and playlists
- Play with **libmpv**, queue / radio expand, shuffle & repeat
- Control playback from the shell via **MPRIS** (media keys / applets)
- Read synced **lyrics** (LRCLIB and other providers)
- Pin a **queue** rail, use under-playbar or fullscreen queue
- **Make tracks available offline** (audio files via yt-dlp into your Downloads folder)
- Theme the UI (Material You / dynamic color, mini-player styles, player backgrounds)

## Requirements

- Linux (Secret Service keyring: GNOME Keyring, KWallet, etc.)
- JDK **21**
- **libmpv** (`libmpv.so`)
- No browser installation needed: Chromium sign-in is embedded in the app
- Optional: **yt-dlp** (offline saves + stream fallback), **pactl** (audio output picker)

## Run

```bash
# Main app (dev)
./run-dev.sh
# or
./gradlew :composeApp:run

# First-time / re-login: Settings → Account → Sign in with Google
```

Prefs live at `~/.config/visage/settings.properties`.

## Project layout (short)

| Module | Role |
|--------|------|
| `:composeApp` | Main Compose Desktop UI |
| `:player` | PlaybackController, libmpv, MPRIS, stream resolve |
| `:innertube` | YouTube Music Innertube API |
| `:core` | DI / session / service facades |
| `:database` | Local SQLDelight cache |
| `native/` | Optional Rust helpers (parallel scan + tag cache, fuzzy search) via JNA — see [`native/README.md`](native/README.md) |
| `:lyrics`, `:jiosaavn`, … | Lyrics backends, Saavn stream assist, other integrations |

Deeper architecture notes: [`docs/architecture.md`](docs/architecture.md). Reusable UI pieces (motion tokens, Material 3 Expressive components, shader backgrounds): [`docs/ui-kit.md`](docs/ui-kit.md). Session changelog for agents: [`SUMMARY.md`](SUMMARY.md).

## Build / test

```bash
./gradlew :composeApp:compileKotlinDesktop
./gradlew :innertube:jvmTest :lyrics:jvmTest
```

Gradle automatically downloads JDK 21 if it is missing (the Gradle launcher
itself needs Java 17 or newer). The first build needs internet access.

To build a Linux AppImage for testing:

```bash
./build-appimage.sh
./dist/VIsage-x86_64.AppImage
# Without FUSE:
APPIMAGE_EXTRACT_AND_RUN=1 ./dist/VIsage-x86_64.AppImage
```

The AppImage bundles Java 21 and JCEF/Chromium 152 for embedded sign-in;
system **libmpv** and a Secret Service keyring are still needed. The script downloads
appimagetool 1.9.1, or uses an executable supplied via `APPIMAGETOOL`.
Gradle bundles the CEF native runtime for the build architecture. On first sign-in
it is extracted into `~/.cache/visage/jcef-152.0.6/`; no browser download is needed.
The browser profile lives in `~/.cache/visage/embedded-login-profile/`.
Existing keyring sessions are restored into the embedded browser automatically.

Canvas controls live under **Settings → Now Playing → Background**. GPU decoding
defaults off; optional **Ping-pong canvas** plays forward and backward without
repeating endpoint frames. Normal loops use a crossfade, with frame interpolation
after the first pass has filled the decoded-frame cache.

Canvas identities distinguish Apple/Tidal album artwork from VIVI song artwork,
and distinguish square and tall assets. The native helper persists lookup metadata
for 24 hours. Decoded pixels are cached in RAM (512 MiB total, 256 MiB per clip),
shared across tracks and reused when signed URL query parameters change. Oversized
clips retain the streaming forward loop. Video files are not permanently cached
on disk.

## License / upstream

Desktop port of the VIVI Music Android project. Upstream Android tree may exist locally as gitignored `vivi-music/` for reference only.

## Migrating from VIVI Desktop
VIsage is the new name of VIVI Desktop. On first launch it moves `~/.local/share`, `~/.config`, `~/.cache` and
`~/.local/state` `vivi-desktop` directories to `visage` (leaving a symlink at the old path) and renames `vivi.db` to
`visage.db`; the saved YouTube Music session is copied to the new keyring service. Old `VIVI_*` environment variables
still work. The window class is now `visage` — update any compositor window rules that matched `com.music.vivi.alex`.
