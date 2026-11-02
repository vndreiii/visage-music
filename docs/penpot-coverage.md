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
| Fullscreen player (`NowPlayingFullscreenScreen`) | all | missing: navfile route capture shows a half-loaded transitional state; capture it by clicking the fullscreen tool button in a playing Now Playing session (`live.sh`) |
| History, Tag Manager, Charts ("Whole world"), New Releases at Desktop 1920x1080 (page `Screens · Library & browse`) | built from `q1920__*` captures; History/Tag Manager/Charts compared with the capture, covers are CC placeholder photos (the app shows generated pattern art for covers without artwork on New Releases/Moods) | **partial**: 1920 only, default state, first screenful; other breakpoints missing |
| TagManagerScreen (new design: inline title, 40dp search + grouping, filter chips, actions, column header, 64dp rows, 65dp cover tile) at Mobile 390x844, Tablet 800x1280, Desktop 1366x768, Large 2560x1440, UltraWide 3440x1440 (default, queue + library docked where wide) | built with `H.tmScreen` (Legend patch 31) from the `tm*__tag_manager` captures; exports compared with the captures (columns, chips, tile, mini player, UltraWide content cap); covers are placeholders, row text is sample data | **partial**: default state only; 1920 frame left untouched (Alex's edits); rows are 64dp in the capture, not the 60dp in older notes |
| HistoryScreen (Listening history: back, title, search 320 trailing on dock / under title on compact, mini player) at Mobile 390x844, Tablet 800x1280, Desktop 1366x768, Large 2560x1440, UltraWide 3440x1440 | built with `H.hsScreen` (Legend patch 32) from the `tm*__history` captures and `nw1920__history.png`; header and chrome compared; the list is empty in every capture, so no rows are built | **partial**: layout only, no list rows (captures are empty); light, loading and error states missing |
| Library home, Search (idle/results), Moods & genres, Album, Artist, Playlist, Browse | all | missing (captures of Library / Moods / Search results exist in the scratchpad but are not built; Moods and New Releases use procedural pattern art that would need its own builder) |
| Popup mini player, What's New, onboarding, login gate, update prompt, crash dialog, splash | all | missing |

## Legend page
Table Penpot element → Kotlin symbol → file → parameters: done (24 rows).

## Penpot MCP limitations hit
* Plugin storage is wiped when an `execute_code` call times out (~30 s): keep calls small; the helper library is persisted in
  Legend-page plugin data (`visageLib`, `visagePatchN`, `visageIcons`, `visageImgs`, `visagePal`).
* No backdrop blur; `fetch` blocked; localStorage not permitted; `findByName('Roboto')` returns Roboto Mono; `openPage` async; text sizes read as 1
  until a layout update; images only via `uploadMediaUrl` (public URLs, so covers are CC picsum photos, not the user's real artwork).

## Alex's Home Large 2560 edits (applied 2026-11-01)
App: pill row + mood chips gutter 12dp (pane x+12, aligned to the cards), Quick picks grid gap 12dp, framed row pitch 58.5dp (48dp content centred in the 64dp item, no layout trim). Penpot: same shift/spacing propagated to the 390, 800, 1366, 1920 and 3440 Home frames (pills/chips x = card x, rows y 319 + 58.5n, columns pitch (card w - 40 + 12) / n). Not re-captured from the new AppImage yet.

## Screens · Library, Search, Moods (page `Screens · Library, Search, Moods`), 2026-11-01
Captured from the real AppImage (Xvfb `:80`, `tools/penpot/capture/sess.sh`, routes `library`, `search_input`, `mood_and_genres`). Only the Desktop 1920x1080 column was built this round.
| Frame | Breakpoint / state | Status |
|-------|--------------------|--------|
| MoodAndGenresScreen | Desktop 1920x1080 default | **geometry matches capture** (hero 192-436 with 484/322/322 columns, rows 522/706/918, card edges exact by pixel scan). Covers are flat colour tiles named `~cover mood (procedural pattern)`; label plate widths are estimated from character count (about +-6px vs capture); plate colours sampled approximately. Not yet pixel-compared to 2px. |
| SearchScreen | Desktop 1920x1080 idle | structure matches (top bar Search tab, search field 392..1536 y84 h52, queue, library, mini player). Background is the homeA wallpaper, not the blurred haze of the capture; not pixel-compared. |
| LibraryScreen | Desktop 1920x1080 default | structure matches (tab chips, search, On this device card, Made for you / Recently added / Genres shelves clipped at the pane edge 1548, mini player). Covers are flat placeholders, all sample text. Not pixel-compared; New albums row below the fold is only a header. |
| all three | Mobile 390, Tablet 800, Desktop 1366, Large 2560, UltraWide 3440 | **missing** (not captured, not built) |
| all three | light, blur-off, loading, empty, queue-open variants | **missing** |
Frame positions: x=2826 (Desktop 1920 column), y=0 Library, y=1400 Search, y=2800 Moods.
Builder: `visagePatch31` (`H.moodCard(f,x,y,w,size,title,colours)`) on the Legend page.

## Home frames re-audited against fresh captures (2026-11-02)
Captured the current AppImage at 390/800/1366/1920/2560/3440 (`tools/penpot/capture/sess.sh`, route `home`) and compared with the six `Screens · Home` frames.
* App: Quick picks grid pulled up 17dp (first row content 68dp under the card top, card 315dp at Large); verified on a new 2560 capture (cover top y319, card about 316dp).
* Penpot, all six frames: real `visage_icon.png` logo (36dp r8) instead of a placeholder picture (Mobile 390 has no logo, as in the capture); greeting Bold; mood chips resized and SemiBold; Quick picks strapline/row title weights; mini player title; 1366 mini player prev/speaker/favourite icons; "Covers and remixes" shelf at 2560, 3440, 800.
* Queue panel (1920, 1366, 2560, 3440): divider under Shuffle/Repeat/Radio, 48dp header buttons and chips, brighter icon and label colours, 14dp subtitles, 14x2 drag handles.
* Library sidebar (same four frames): search field, white labels, bold chips and sort label, menu icon, Liked Songs gradient tile with heart, tile caption sizes.
* Still different: glass backgrounds (no backdrop blur in Penpot), wallpaper, cover art and track names (sample data), 800 "From your library" 3-4px left, 3440 Covers shelf about +-10px, Covers rows reuse sample text, 1920/1366 do not show the Covers shelf in their captures.
