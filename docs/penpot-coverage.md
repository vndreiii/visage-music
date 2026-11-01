# Penpot coverage ("VIsage UI")

Honest status. **Rule (from Alex): every component/screen must be 1:1 with the running app; verify against Xvfb screenshots and the Kotlin
source, never from memory.** Status below is split into *verified 1:1*, *built but not yet re-verified*, and *missing*.
Real captures of the app (default theme, Xvfb) were taken for many more screens than are rebuilt; they live outside the repo
(session scratchpad) and the recipe to re-take them is in `.claude/skills/visage-penpot/SKILL.md`.

## Foundations (page `Foundations`)
| Item | Status |
|------|--------|
| colorScheme dark + light swatches (real values dumped from `visageColorScheme`, seed 0xFF8BA3B8 TonalSpot; also library colours `dark/colorScheme.*`, `light/…`) | done, exact |
| VisageType roles (11) as library typographies + specimen frame | done (sizes from `docs/design-system.md`; fonts Manrope/Roboto) |
| Radii, spacing constants, breakpoints, glass key | done (from docs/Dimensions.kt; not screenshot-measured) |

## Components (page `Components`, dark + light each)
Alex hand-corrected several of these in Penpot; **his edits are the source of truth — do not regenerate the page.** Light frames were
re-synced to his dark edits (page-state layout, search-field default/hover opacity 0.4/0.8, `HOVER_STATE` search field, NewChip 41x21, loading
indicator = morph cycle illustration, `Recent ▾` icon).
| Group | Status |
|-------|--------|
| ExpressiveButton (5 styles x sizes x states) | built from `ExpressiveButtons.kt` values; **needs a pixel compare vs capture** |
| Chips / ConnectedButtonGroup / NewChip / Switch / text fields / icon buttons | built; Alex-corrected; not re-compared by me |
| YouTubeListItem / YouTubeGridItem / mood + hero tiles | built from memory-grade values; **not verified 1:1** |
| GlassSurface / HomeCategoryFrame / SettingsGroupCard | built; glass approximated; HomeCategoryFrame verified at 1920 Home |
| Overlays: ItemContextMenu, VisageToast, NavTray, LibraryDialogShell, page states | built; **not verified 1:1** |
| PlayerSeekBar (wavy/flat/compact) | from `PlayerSeekBar.kt` constants; real sine wave path |
| **MiniPlayerBar (3 styles + phone)** | **rebuilt 1:1 from `MiniPlayerBar.kt` + zoomed capture** (circle cover 44, prev/play/next/Speaker/favorite, 4dp MiniSeek row, no cast icon) |
| QueuePanel / LibrarySidebar components | built earlier from memory; superseded by the measured versions inside `HomeScreen / Desktop 1920x1080` |
| Icons | real Material outlined/filled SVG paths (`tools/penpot/material-icons-outlined.json`) |

## Screens
| Screen | Breakpoint / state | Status |
|--------|--------------------|--------|
| HomeScreen | Desktop 1920x1080 default, queue + library docked | **done, measured against capture (layout, panel rects, rows, shelves, mini player)** |
| HomeScreen | 1366, Mobile 390, Tablet 800, Large 2560, UltraWide 3440 (default, dark) | **done, measured from Xvfb captures** (responsive rules: quick-picks columns, top-bar variants, UltraWide width cap) |
| HomeScreen | light, blur-off, loading, error/login, tray/queue/library overlays | missing (captures taken, frames not built) |
| Settings hub | Desktop 1920x1080 default (queue docked) | **done, compared against capture** (rows, chevrons, account card, search, mini-player) |
| Settings sub-pages at Desktop 1920x1080 (first viewport, queue docked): Interface, Theme, Playback & audio, Account, Song swappings, Downloads, Local files, Diagnostics, Keyboard shortcuts, Sidebars, Lyrics, Fullscreen player, Now Playing | built from `q1920__settings_*` captures (row heights 75 switch-sub / 71 sub / 67, segmented control 40dp, switch 52x32, slider 16dp track; the first ones compared pixel-wise, the last five spot-checked) | **partial**: only the first screenful of each long page, live preview wells (VisageTopBar, MiniPlayerBar, Lyrics, Fullscreen, Sidebars, Song swap) are empty placeholders, machine-specific text replaced by neutral sample data; other breakpoints (390/800/1366/2560/3440) and light mode missing |
| Settings hub / sub-pages below the fold (Interface: Glass and backdrop, Typography, Home...; Lyrics: Look/Text/Lines...; Fullscreen: Track info/Controls...) | not built |
| NowPlayingScreen | idle (default) at Desktop 1920x1080 / 1366x768, Large 2560x1440, UltraWide 3440x1440, Tablet 800x1280, Mobile 390x844 | **done**: layout formulas (`nowPlayingIdleMetrics`, stacked column, compact tools row) checked against Xvfb captures at all six sizes |
| NowPlayingScreen | lyrics state (LyricsPanel loading + toolbar, controls card with 72dp cover thumb, triple layout at UltraWide) at the same six sizes | **done for the loading state** (no lyrics text rendered); 1920/1366/800/390 compared with captures, 2560 and 3440 (triple) built from `NowPlayingScreen.kt` and checked if a capture was taken (see below) |
| NowPlayingScreen | `playing` (wavy seek bar, pause icon) and `lyrics-synced + canvas` (synced lines with blur by distance, delay pill, tall canvas above the controls, no cover thumb) at Desktop 1920x1080 | **done, compared with a real capture of a song with lyrics** |
| NowPlayingScreen | synced lyrics / canvas at the other five sizes, music-video cover, queue tray / inline queue strip, light, blur-off, popup | missing |
| Fullscreen player (`NowPlayingFullscreenScreen`) | all | missing |
| Library (+ sidebar hidden items), Search, Album, Artist, Playlist, Charts, Moods, New releases, History, Browse, Tag Manager | all | missing |
| Popup mini player, What's New, onboarding, login gate, update prompt, crash dialog, splash | all | missing |

## Legend page
Table Penpot element → Kotlin symbol → file → parameters: done (24 rows).

## Penpot MCP limitations hit
* Plugin storage is wiped when an `execute_code` call times out (~30 s): keep calls small; the helper library is persisted in
  Legend-page plugin data (`visageLib`, `visagePatchN`, `visageIcons`, `visageImgs`, `visagePal`).
* No backdrop blur; `fetch` blocked; localStorage not permitted; `findByName('Roboto')` returns Roboto Mono; `openPage` async; text sizes read as 1
  until a layout update; images only via `uploadMediaUrl` (public URLs, so covers are CC picsum photos, not the user's real artwork).
