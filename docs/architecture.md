# VIsage — Architecture

VIsage is a Compose Multiplatform desktop app (Kotlin, JVM 21) for Linux and Windows.
It plays YouTube Music and local files. libmpv decodes the audio. A small Rust library does the work that must be fast.

## Overview

```
                  ┌────────────────────────────┐
                  │ :composeApp  (Compose UI)  │
                  │ screens, prefs, navigation │
                  └─────────────┬──────────────┘
                                │ Koin
                  ┌─────────────▼──────────────┐
                  │ :core                      │
                  │ SessionManager             │
                  │ YouTubeMusicService        │
                  │ PlaybackController         │
                  └──────┬───────────────┬─────┘
                         │               │
          ┌──────────────▼───┐     ┌─────▼──────────┐
          │ :player          │     │ :innertube     │
          │ libmpv (JNA)     │     │ YouTube Music  │
          │ queue, radio     │     │ API client     │
          │ stream resolve   │     └────────────────┘
          │ MPRIS (Linux)    │     ┌────────────────┐
          │ session storage  │     │ :jiosaavn      │
          └───────┬──────────┘     │ :lyrics        │
                  │                │ :canvas  (+2)  │
          ┌───────▼──────────┐     │ :lastfm :kizzy │
          │ :database        │     └────────────────┘
          │ SQLDelight       │
          └──────────────────┘
          ┌──────────────────┐
          │ native/ (Rust)   │  loaded with JNA through :nativelib
          └──────────────────┘
```

| Module | Role |
|--------|------|
| `:composeApp` | The app: UI, navigation, preferences, update service. Main class `com.music.vivi.alex`. The window class is `visage`. |
| `:core` | Koin modules and the facades that the UI uses. |
| `:player` | Playback, stream resolution, queue and radio, session storage, MPRIS, music video. |
| `:innertube` | YouTube Music API client. |
| `:jiosaavn` | JioSaavn search and stream URLs. |
| `:lyrics` | Lyrics providers (LRCLIB, KuGou, Musixmatch, BetterLyrics, YouLy+, Unison, Paxsenix) and the lyrics timing model. |
| `:canvas`, `:applecanvas`, `:vivimusiccanvas`, `:artistvideo` | Canvas (looping video cover) providers. |
| `:lastfm`, `:spotify`, `:shazamkit`, `:kizzy` | Scrobbling, Spotify, song recognition, Discord Rich Presence. |
| `:database` | SQLDelight database: local library, playlists, play history, downloads, library folders. |
| `:nativelib` | App identity, environment variables, data folders (`AppPaths`), loader for the Rust library. |
| `:player-cli` | Headless playback smoke test. |
| `native/` | Rust library `visage_native`. See [`native/README.md`](../native/README.md). |

Versions: Kotlin 2.3, Compose Multiplatform 1.10, Koin, SQLDelight. See `gradle/libs.versions.toml`.

## Platforms

| | Linux | Windows |
|---|---|---|
| Data folders | XDG (`~/.config/visage`, `~/.local/share/visage`, `~/.cache/visage`) | `%APPDATA%\visage` (settings), `%LOCALAPPDATA%\visage` (data, cache) |
| libmpv | System package (`libmpv.so.2`) | `libmpv-2.dll`, bundled in the app |
| yt-dlp | `PATH` | `yt-dlp.exe`, bundled in the app |
| Rust library | `libvisage_native.so` | `visage_native.dll` |
| Media keys | MPRIS (Rust `zbus`) | Not supported yet. The Rust MPRIS functions are stubs. |
| Session storage | Secret Service or KWallet, else an encrypted file | Windows Credential Manager, else an encrypted file |
| Folder picker | XDG portal, then zenity or kdialog | Swing chooser |
| Packages | AppImage, deb, rpm, tar.gz, Flatpak, Arch `PKGBUILD` | MSI, setup EXE, portable zip |

Platform checks go through `HostOs` in `:nativelib`.

## Sign-in and session

1. Settings → Account embeds a JCEF (Chromium) browser. The user signs in to Google.
2. `YtLoginFlow` reads the cookies, `visitorData` and `dataSyncId` through the Chrome DevTools Protocol.
3. `InnerTube` validates the session. `SessionSecretStore` saves it. `SecretStoreRouter` picks the system keyring first. If the keyring is not available, it uses `EncryptedFileBackend`.
4. `YtMusicSession.applyToYouTube()` gives the session to the global `YouTube` object.
5. Sign-out deletes the keyring entries and the encrypted file.

## Playback

`PlaybackController` is the contract for the UI. It exposes state flows and the actions play, pause, seek, queue and like.
`DefaultPlaybackController` implements it.

1. `StreamResolver` finds a stream. The order is JioSaavn (when on), then Innertube and NewPipe, then yt-dlp.
2. `MpvPlayer` plays it through libmpv (JNA). Crossfade uses two mpv instances.
3. Radio expands the queue. Shuffle keeps the original order, so turning it off restores the order.
4. Each track change has a generation number. Late results from an older change are ignored.
5. Play calls run in a detached scope, so leaving a screen cannot stop the song.
6. Music video: `MusicVideoResolver` and `VideoStreamResolver` find a muted video stream. `MpvVideoRenderer` draws frames in software for the player.
7. On Linux, `MprisService` publishes the player on the session bus as `org.mpris.MediaPlayer2.visage`.
8. Artwork is cached on disk (`ArtworkCache`).

## Local library and tags

- Folders are in the `LibraryFolder` table. `visage_scan` (Rust) walks them. It reads tags and covers with lofty. Unchanged files are skipped.
- Fuzzy search uses `visage_search_*` (nucleo).
- The Tag Manager (`ui/tags/`) finds files with missing or bad tags. It suggests fixes from MusicBrainz, Deezer, iTunes and TheAudioDB. It writes through `visage_tags_write`. That call writes to a temporary file, checks it, and renames it over the original. Optional backups allow Undo.
- Artist pages use `visage_artist_resolve` (Deezer, TheAudioDB, Wikipedia).

## User interface

`App` shows a rounded window. `AppScaffold` holds the top bar, the `NavHost`, the sidebars and the mini player.

```
┌─────────────────────────────────────────────────────────────┐
│ Top bar   Home · Search · Library · Settings · sidebar keys │
├───────────┬─────────────────────────────────┬───────────────┤
│ Queue     │ NavHost (MainPane)              │ Library       │
│ sidebar   │                                 │ sidebar       │
│           │      ┌───────────────┐          │               │
│           │      │ Mini player   │          │               │
└───────────┴──────┴───────────────┴──────────┴───────────────┘
```

| Area | Package | Notes |
|------|---------|-------|
| Navigation | `navigation/` | `Routes`, `AppScaffold`, transitions, touchpad and mouse back (`PredictiveBackSwipe`). |
| Home, Search, Browse | `ui/home`, `ui/search`, `ui/browse` | Region and language setting for search. |
| Library | `ui/library` | Library home, mixes, genre cards. `sidebar/` is the library sidebar (filters, sort, folders). |
| Artists and albums | `ui/artist`, `ui/album`, `ui/playlist` | Artist info page with portrait and bio. |
| Charts, Moods, History | `ui/charts`, `ui/moods`, `ui/history`, `ui/newrelease` | |
| Now Playing | `ui/player` | Player, fullscreen player, queue panel, track menu, audio output. |
| Lyrics | `ui/player/lyrics` | Word-by-word renderer, gap indicator, styles. `fetchLyrics` asks the chosen provider first, then the others. |
| Song swap | `ui/player/swap` | Animations for song change: Camera pan, Slide, Crossfade, Zoom through, Flip, Stack, Vinyl, Wipe. |
| Backgrounds | `ui/player/background` | SkSL shaders and Canvas video. |
| Keyboard | `keys/` | `ShortcutAction`, rebindable `Keymap`, `ShortcutEngine`, overlay. |
| Settings | `ui/settings` | Hub: Interface (Theme, Top bar and mini-player, Now Playing, Song swappings, Sidebars, Keyboard shortcuts), Playback, Library and storage, Support, Updates, About. |
| Theme | `theme/` | Dynamic colour from artwork, matugen colours, contrast. |
| Preferences | `prefs/` | `DesktopPrefs` (`settings.properties`) with `PrefKeys`, plus `SidebarPrefs` and `FullscreenPlayerPrefs`. |
| Errors | `crash/` | Log, crash dialog, problem report. |

Mini player styles (`MiniPlayerStyle`): `FLOATING_PILL` (default, a notch attached to the bottom edge with its own seek row), `FULL_WIDTH_BAR`, `COMPACT_EDGE`.

Rules for the UI:
- Every change animates. Use the springs in `VisageMotion`. See [`ui-kit.md`](ui-kit.md).
- Back buttons use `VisageBackButton`.
- Space plays and pauses everywhere, except in a text field.
- Generated covers are flat and have no gradients. Text does not sit on the art, except on Moods and genres cards.
- Do not call `YouTube.*` from the UI. Use `YouTubeMusicService`.

## Updates

`UpdateService` checks the latest GitHub release every six hours. The release has `latest.json` and `latest.json.sig`. The app checks the Ed25519 signature with the key in `update-public-key.b64`. Then it checks the version, the asset URL and the SHA-256.

| Install | Update |
|---------|--------|
| AppImage | Replaces its own file, then restarts. |
| Windows installer | Downloads `VIsage-x64.msi`, runs it for the current user, then restarts. |
| Windows portable zip (has a `portable` file) | Opens the release page. |
| Flatpak | Off. Flatpak updates it. |
| deb, rpm, tar.gz, Arch | Opens the release page. |

## Build and release

- `.github/workflows/build.yml` builds all packages. `ci.yml` (each push) and `release.yml` (each `v*` tag) call it.
- `scripts/package-linux.sh` builds deb, rpm, tar.gz and AppImage. `scripts/package-windows.ps1` builds the zip, MSI and EXE.
- `release.yml` also builds Flatpak, writes a `PKGBUILD` for the release, signs `latest.json`, and publishes the release.
- The secret `VISAGE_UPDATE_SIGNING_KEY` holds the signing key.
