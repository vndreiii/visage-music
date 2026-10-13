# VIVI Desktop

**VIVI Desktop** is a Linux music player for [YouTube Music](https://music.youtube.com). It is the desktop port of [VIVI Music](https://github.com/vivizzz007/vivi-music) (Android): same idea — stream YT Music with your own account — built as a native Compose Multiplatform app for the desktop.

Sign in once with Google (system Chromium + your Linux keyring). After that you get Home, Search, Library, a full Now Playing experience, lyrics, queue, MPRIS desktop integration, and optional offline audio saves.

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
- Chromium/Chrome (for login)
- Optional: **yt-dlp** (offline saves + stream fallback), **pactl** (audio output picker)

## Run

```bash
# Main app (dev)
./run-dev.sh
# or
./gradlew :composeApp:run

# First-time / re-login (Chromium → keyring)
./gradlew :desktop:run
```

Prefs live at `~/.config/vivi-desktop/settings.properties`.

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

Deeper architecture notes: [`docs/architecture.md`](docs/architecture.md). Session changelog for agents: [`SUMMARY.md`](SUMMARY.md).

## Build / test

```bash
./gradlew :composeApp:compileKotlinDesktop
./gradlew :innertube:jvmTest :lyrics:jvmTest
```

## License / upstream

Desktop port of the VIVI Music Android project. Upstream Android tree may exist locally as gitignored `vivi-music/` for reference only.
