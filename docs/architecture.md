# VIsage — Architecture

Canonical architecture for agents and humans. Session changelog lives in [`summary.md`](summary.md). UX audit / M3E backlog: [`UI-UX-enbetterment.md`](UI-UX-enbetterment.md).

Linux desktop port of [VIVI Music](vivi-music/) (Android). Upstream Android tree is gitignored under `vivi-music/` as a reference clone.

---

## Big picture

```
┌─────────────┐    cookies/CDP     ┌──────────────────┐
│ :player     │ ─────────────────► │ Linux keyring    │
│  (Compose   │    save session    │ (Secret Service) │
│   login)    │ ◄───────────────── │ vivi-desktop /   │
└─────────────┘    load session    │ yt-music-session │
                                   └────────┬─────────┘
                                            │ applyToYouTube()
┌─────────────┐    Koin            ┌────────▼─────────┐
│ :composeApp │ ◄───────────────── │     :core        │
│  Compose    │  SessionManager    │  DI + facades    │
│  Desktop    │  PlaybackController│  YouTubeMusicSvc │
│  (main UI)  │  DesktopPrefs      └────────┬─────────┘
└──────┬──────┘                             │
       │ LocalPlaybackController            │
       ▼                                    ▼
┌─────────────────────────────────────────────────────┐
│                      :player                          │
│  PlaybackController → StreamResolver → libmpv (JNA)  │
│  queue + radio expand → MPRIS (dbus) → likeVideo     │
└───────────┬─────────────────────┬───────────────────┘
            │                     │
            ▼                     ▼
     ┌────────────┐        ┌────────────┐
     │ :innertube │        │ :jiosaavn  │
     │ YouTube.*  │        │ Saavn CDN  │
     └────────────┘        └────────────┘
            │
            ▼
     ┌────────────┐
     │ :database  │  SQLDelight playlist/song cache
     └────────────┘

Also available (partially wired): :lyrics :lastfm :spotify
:shazamkit :kizzy :canvas :applecanvas :vivimusiccanvas :artistvideo
```

| Entry | Role |
|-------|------|
| `./gradlew :desktop:run` | Google / YT Music **login** (system Chromium + CDP). Writes session to keyring. |
| `./gradlew :composeApp:run` | **Main app** — Compose Desktop shell. Entry / WM_CLASS: `com.music.vivi.alex`. |
| `./gradlew :player-cli:run` | Headless smoke (play / like) for CI-style checks. |

Hyprland / lab scripts use class `com.music.vivi.alex` (may appear dashed as `com-music-vivi-alex`). Prefer killing by that PID/class, not broad `pkill -f`.

Gradle: Kotlin **2.3.10**, JVM **21**, Compose Multiplatform **1.10.2**, Koin (not Hilt). Version catalog: `gradle/libs.versions.toml`.

---

## Auth & session

1. `:desktop` opens Chromium (`--app=…`) against Google → music.youtube.com.
2. CDP scrapes cookies, `visitorData`, `dataSyncId`.
3. Session validated via Innertube `accountInfo`, stored with **java-keyring** (Secret Service): service `visage`, accounts `yt-music-session` / `yt-music-account`.
4. `YtMusicSession.applyToYouTube()` sets the global `YouTube` singleton used by Innertube.
5. `:core`’s `KeyringSessionManager` reloads that session for Compose / player.

Login runs in `:player` (`YtLoginFlow`: real Chromium window + CDP → keyring); Compose hosts it in Settings → Account.

---

## Playback path

`PlaybackController` (`:player`) is the UI-stable contract (StateFlows + play/pause/seek/favourite/queue) — desktop analogue of Android’s `PlayerConnection`, without Media3 / a bound service.

On `playVideo` / `playQueue`:

1. **Stream resolve** (`StreamResolver`): prefer **JioSaavn** when enabled (`VIVI_SAAVN=0` to disable) → else Innertube / NewPipe → **yt-dlp** on 403 → last resorts.
2. **libmpv** via JNA (`libmpv.so`; `LC_NUMERIC=C` / setlocale for mpv).
3. **Queue + radio:** `playQueue` / in-queue jumps keep history, then `expandQueueFromRadio(..., replaceUpcoming = true)`. EOF advances on a coroutine — never `requestIndex` / `halt` on the mpv-events thread.
4. **Artwork** under `~/.cache/visage/art/` as `file://` URLs.
5. **MPRIS** bus `org.mpris.MediaPlayer2.vivi`.
6. **Favourite** → Innertube `YouTube.likeVideo` / unlike.

---

## Module map

| Module | Kind | Responsibility |
|--------|------|----------------|
| `:innertube` | KMP JVM | YouTube Music InnerTube client, auth session types, NewPipe helpers |
| `:lyrics` | KMP JVM | Lyrics providers (LrcLib, etc.) |
| `:jiosaavn` | KMP JVM | Saavn search + stream URL decrypt |
| `:lastfm` / `:spotify` / `:shazamkit` / `:kizzy` | KMP JVM | Scrobble / GQL / recognize / Discord RPC |
| `:canvas` family / `:artistvideo` | KMP JVM | Canvas / background artwork providers |
| `:player` | JVM | libmpv, MPRIS, stream resolve, queue/radio, like |
| `:player-cli` | JVM app | CLI smoke |
| `:database` | JVM | SQLDelight playlist/song cache |
| `:core` | JVM | Koin: `SessionManager`, `PlaybackController`, `YouTubeMusicService`, chart suggestions |
| `:composeApp` | CMP desktop | Full shell UI (`com.music.vivi.alex`) |

### Compose connection

- Entry: `composeApp/.../com/music/vivi/alex.kt` (`@file:JvmName("alex")`, `awt.appClassName = com.music.vivi.alex`)
- `startKoin { modules(coreModule, desktopModule) }`
- `CompositionLocalProvider(LocalPlaybackController provides controller)`
- Screens: `LocalPlaybackController.current` / `koinInject<…>()`
- Prefs: `DesktopPrefs` (`~/.config/visage/settings.properties`) + listeners / `rememberPrefsVersion` for immediate Settings recomposition

### Not in the stack yet

- PoToken / full Android player-utils parity
- Full local library DB UX parity
- Packaging (AppImage / Flatpak); runtime needs system **libmpv**
- Compose-native login (still `:desktop` Chromium+CDP)
- Shared-element mini→NP transition; NP V2 glassmorph; full LyricsUtils commonMain port

---

## Compose UI shell

Undecorated window (`alex.kt`). Root `App` wraps content in `Surface` + `clip(RoundedCornerShape(16.dp))` so player glow/blur cannot bleed past compositor-rounded corners.

```
┌──────────────────────────────────────────────────────────┐
│  ViviTopBar   Home · Search · Library · Settings · …     │
├──────────────────────────────────────────────┬───────────┤
│  NavHost                                     │ Queue?    │
│    content + soft bottom fade                │ (pinned)  │
│    MiniPlayerBar overlays content            │           │
│    (FLOATING_PILL default — not a dock band) │           │
└──────────────────────────────────────────────┴───────────┘
```

| Piece | Role |
|-------|------|
| `ViviTopBar` | Destinations + window controls |
| `ViviNavHost` / `AppScaffold` | Routes + optional pinned queue |
| `MiniPlayerBar` | Overlay on content (hidden on now-playing). Styles below. |
| `NowPlayingScreen` | Full player; lyrics 2-pane; queue modes |

Route names stay aligned with upstream (`Routes.kt`).

| Route | Screen |
|-------|--------|
| `home` | `HomeScreen` |
| `search_input` | `SearchScreen` |
| `library` | `LibraryScreen` |
| `settings`… | `SettingsScreens` (hub → Account / Appearance / Theme / Player) |
| `now_playing` / fullscreen | `NowPlayingScreen` / `NowPlayingFullscreenScreen` |
| `album/{id}` / `artist/{id}` / `online_playlist/{id}` / `browse/{id}` | Online browse |
| `smoke` | `SmokePlaybackScreen` |

### Mini player

Canonical pref: `miniPlayerStyle` (`PrefKeys.MiniPlayerStyle`).

| `MiniPlayerStyle` | Look |
|-------------------|------|
| `FLOATING_PILL` (**default**) | Centered pill (`RoundedCornerShape(50)`), inset + shadow, thin progress |
| `FULL_WIDTH_BAR` | Edge-docked classic bar |
| `COMPACT_EDGE` | Small card bottom-trailing (legacy “Apple mini”) |

Resolution: `resolveMiniPlayerStyle(prefs)` — prefers `miniPlayerStyle` string; falls back to `UseAppleMiniPlayer` / `UseNewMiniPlayerDesign`. `MiniPlayerBar` listens to prefs so Appearance changes apply live.

Scaffold: soft gradient fade only — **no** solid full-width band under the bar (that made FLOATING_PILL look glued).

Thumb: `Surface(shape = CircleShape, onClick)` so hover/ripple stay circular (hard-square bug).

### Now playing & lyrics

- **Non-lyrics:** large art column (`BoxWithConstraints`, ~96% of column) + centered title / seek / transport; bottom chrome is a **sibling** row (never overlaps Pause).
- **Lyrics on:** 2-pane — `LyricsPanel` left, compact thumb+title + controls right; compact circular play in lyrics mode.
- **LyricsPanel** (`ui/player/lyrics/`): floating text on backdrop (no Surface panel). Pref `lyricsTextAlign` (`LEFT` / `CENTER` / `RIGHT`, default **CENTER**) + in-pane align chips. Hierarchy = scale + opacity (+ light text shadow on active). **No** distance blur, **no** top/bottom black edge fades.
- **Track ···:** `TrackActionsMenu` = bounded `Popup` + `heightIn` + `verticalScroll` (Material `DropdownMenu` crashes on Desktop with infinite max height).
- **Backgrounds:** `PlayerBackgroundLayer` uses `clipToBounds()`; styles from prefs (`GRADIENT`, `GLOW_ANIMATED`, …).

### Settings (Appearance)

- Accordion `SettingsSection` (expand/collapse).
- Mini-player + mini-player background: one large `LiveMiniPlayerHeroPreview` card per section (real `MiniPlayerBar`, scaled); radios only — selection swaps via `AnimatedContent`. No tiny unreadable row thumbnails.
- Lyrics alignment radios; theme / top-nav / slider / buttons mockups as elsewhere.

### Lists / M3E patterns in force

- Continuous lists: **no** per-item `HorizontalDivider` (semantic section dividers only).
- Interactive rows: pill `Surface(shape = RoundedCornerShape(12.dp), onClick)`.
- Playlist / album `LazyColumn` `contentPadding(bottom = 120.dp)` so last tracks clear the floating mini.
- Loaders: `ViviLoadingIndicator` — Morph port of M3 Expressive indeterminate indicator (`graphics-shapes`; official CMP `LoadingIndicator` still **internal**).

### Mapping Android → desktop

| Upstream (Android) | Desktop |
|--------------------|---------|
| Hilt / `@HiltViewModel` | Koin in `:core` + `koinInject` |
| `MusicService` + bind | Process-local `:player` singleton |
| `PlayerConnection` | `PlaybackController` |
| `LocalPlayerConnection` | `LocalPlaybackController` |
| `YouTube.*` from VMs | `YouTubeMusicService` (`ensureSession` then `YouTube.*`) |
| Room + DataStore | SQLDelight + `DesktopPrefs` |
| Media3 / ExoPlayer | libmpv |
| Google login WebView | `:desktop` Chromium+CDP |

---

## Hard rules (porting)

1. Do **not** add Android Gradle plugin, Hilt, Timber, or Media3 to desktop modules.
3. Do **not** call `YouTube.*` from UI without session applied.
4. Prefer extending `PlaybackController` / `:core` facades over leaking `:player` into screens.
5. Port one screen/route cluster per change; `./gradlew :composeApp:compileKotlinDesktop` after each.
6. Update `TODO.txt` + `summary.md` when a slice lands; keep **this file** in sync when shell/player contracts change.
7. Do not commit / force-push unless the human asks.
