---
name: visage-penpot
description: Use when the user mentions Penpot, the "VIsage UI" design file, a Penpot frame/element/layer, or asks to "apply this design/change from Penpot in the app". Maps the Penpot design 1:1 onto VIsage's Compose Desktop UI (naming contract, glass/blur rule, tokens, breakpoints, workflow, pitfalls, how to add screens).
---

# VIsage ⇄ Penpot

Penpot file **"VIsage UI"** (design.penpot.app) is the shared visual language for the Compose Desktop app in this repo.
Contract: `docs/penpot-mapping.md`. Coverage: `docs/penpot-coverage.md`. Design rules: `docs/design-system.md`, `docs/ui-kit.md`,
`docs/responsive-layout.md`. Never write the Penpot token/credentials into the repo.

The design was built **from the running app** (Xvfb screenshots of the real Compose UI) plus the Kotlin sources, not from imagination.
When code and Penpot disagree on a number, the **code is the baseline**; the Penpot frame is the *requested change*.

## 0. Iron rule: 1:1 or nothing

Never draw any component or screen from memory. For every element: read the Kotlin (dp, icons, text roles, colours) **and** zoom into the real Xvfb screenshot, build it, export the Penpot node, compare side by side, redo until identical. Use the exact icon variant the code uses (`Icons.Filled.SkipNext` ≠ outlined). Never regenerate frames the user hand-edited in Penpot; diff dark vs light and mirror their edits instead. Example failure: the mini-player is `MiniPlayerBar.kt` (circle cover 44, 48dp IconButtons prev/play(30dp, no circle)/next/Speaker/favorite, a thin 4dp `MiniSeek` row) — not `PlayerSeekBar`, no cast icon.

## 1. Workflow: "apply this Penpot frame/element in the app"

1. **Read the node.** `mcp__penpot__execute_code` (load tools with ToolSearch if deferred):
   `penpotUtils.findShape(s => s.name.startsWith('HomeScreen / Desktop 1920'))`, or ask the user to select it and use `penpot.selection`.
   Use `mcp__penpot__export_shape` to *look* at it. Read children with `penpotUtils.shapeStructure(shape, 3)`; read dp values from
   `x/y/width/height/borderRadius`, `fills[].fillColor`, text `fontSize`, flex-layout `rowGap/padding`.
2. **Resolve the symbol from the layer name** (see §2) and the `Legend` page (name → Kotlin symbol → file → params). Layer names look like
   `Symbol · param=value · param=value`.
3. **Diff against the code** (open the file named on the Legend page). Change only what the design changed. Express it with tokens:
   `VisageType.<role>`, `MaterialTheme.colorScheme.<role>`, `VisageMotion`, `PageInset`/`PanelInset`/`PanelHeaderGap` (see `ui/component/Dimensions.kt`,
   `docs/design-system.md`). Never paste a raw hex/sp/dp that has a token. Panel padding is `PanelInset` on all four sides.
4. **Compile:** `./gradlew :composeApp:compileKotlinDesktop --offline`
5. **Look at it under Xvfb (never on the live Hyprland session)**, same size as the Penpot frame, and compare with `export_shape`:
   ```bash
   Xvfb :77 -screen 0 3600x1500x24 &
   env -u WAYLAND_DISPLAY -u HYPRLAND_INSTANCE_SIGNATURE DISPLAY=:77 GDK_BACKEND=x11 VISAGE_WINDOW_SIZE=1920x1080 \
     VISAGE_DEV_ROUTE=home [VISAGE_DEV_QUEUE=1] [VISAGE_DEV_LYRICS=1] ./run-dev.sh      # or dist/VIsage-x86_64.AppImage
   xdotool search --onlyvisible --name . windowmove 0 0; import -display :77 -window root -crop 1920x1080+0+0 out.png
   ```
   Wait ≥60–80 s after start (software rendering + network covers) and ≥30 s after each `VISAGE_DEV_NAVFILE` route change; otherwise you
   screenshot the previous page. Move the window to 0,0 or the bottom is cropped. Use isolated `XDG_CONFIG_HOME` with a copy of
   `~/.config/visage` and force `dynamicTheme=false`, `matugenTheme=false`, remove font keys to get the default look. Kill only by PID /
   process group (`setsid` + `kill -- -PID`); never `pkill -f` with a pattern that appears in your own command.
6. **Rebuild the AppImage** (standing rule: always rebuild locally after app changes):
   `VISAGE_VERSION=1.3.0 ./build-appimage.sh` → `dist/VIsage-x86_64.AppImage`.
7. Update the Penpot frame/Legend if the code now differs from the design (the two must stay in sync). Do **not** push.

## 2. Naming contract

* Layers = real Compose symbols: `GlassSurface`, `GlassBackdrop`, `HomeCategoryFrame`, `YouTubeGridItem`, `YouTubeListItem`,
  `SettingsGroupCard`, `SettingsRowFrame`, `NavTray`, `VisageTopBar`, `MiniPlayerBar`, `QueuePanel`, `LibrarySidebar`, `PopupPlayerLayout`,
  `PlayerSeekBar`, `ExpressiveButton(Filled|Tonal|Outlined|Elevated|Text)`, `ConnectedButtonGroup`, `LibraryDialogShell`, `ItemContextMenu`,
  `VisageToast`, `PageLoadingState|PageEmptyState|PageErrorState`, `DetailPageHeader`, `NavigationTitle`, `ChipsRow`.
* State/variant in brackets: `ExpressiveButtonFilled [size=Small state=pressed]`.
* Screen frames: `<Composable> / <Breakpoint WxH> / <state>` e.g. `HomeScreen / Desktop 1920x1080 / default`. States:
  default, loading, empty, error, queue-open, lyrics, video, light, blur-off, tray-open, dialog.
* `~name` = decoration, not a symbol. `icon:<name>` = Material icon (`Icons.Outlined.<Name>`, 24-grid path from `@material-design-icons/svg`).
* Cover art: album/playlist = rounded square (r16–24), artist = circle, video = 16:9 r12. Photos are CC-licensed picsum placeholders; real
  covers come from the network at runtime.

## 3. Blur / glass rule

Elements that are blurred/frosted in the app are Penpot boards named `GlassSurface · <label> · shape=RoundedCornerShape(N) · tint=panelGlassTint(α) ·
fallback=surfaceContainerHigh` containing a `GlassBackdrop` rect with a Penpot layer blur. **Never copy Penpot blur numbers.** Implement
with `ui/glass/Glass.kt` (`GlassSurface(shape, fallbackColor, tint)`, `GlassBackdrop`, `glassSource`, `ProvideGlass`, `panelGlassTint`);
pages blur `LocalPageHazeState`, sidebars/mini-player blur `LocalHazeState`; never nest glass in glass. Blur-off frames are named
`Surface · … · blur-off` (solid fallback). Same idea for other special classes: `LocalPlayerInk`, `playerControlsSurface`, `visageMarquee`,
`PlayerSeekBar` (wavy while playing), `interactiveScale`, `pageScrollEdge`.

## 4. Token mapping (Penpot → Kotlin)

* Library colours `dark/colorScheme.<role>` / `light/colorScheme.<role>` → `MaterialTheme.colorScheme.<role>` (real values dumped from
  `visageColorScheme(seed 0xFF8BA3B8, TonalSpot)`; the app is dynamic at runtime, so use the *role*, never the hex).
* Typographies `VisageType.<role>` → `VisageType.<role>` (`theme/VisageType.kt`): pageTitle 28/36, subPageTitle 24/32, sectionTitle 22/28,
  groupLabel 14/20, cardTitle 16/24, rowTitle 16/24, rowSubtitle 14/20, caption 12/16, control 14/20, readout 12/16, badge 11/16.
* dp 1:1: `PageInset` 20, `PanelInset` 20 (16 compact), `PanelHeaderGap` 12, `MiniPlayerClearance` 140, `ListItemHeight` 64,
  `ListThumbnailSize` 48, `GridThumbnailHeight` 128, queue panel 360, library sidebar 340, settings column ≤760.
* Radii: 6 thumbnail · 12 row/menu · 16 input · 20 settings card/shelf · 24 tile · 28 dialog/search/controls card · 999 pill.
* Motion is not in Penpot; use `VisageMotion` (docs/ui-kit.md).

## 5. Breakpoints

`ui/responsive/WindowLayout.kt`: Mobile <600 (390x844, min 360x560) · Tablet 600–900 (800x1280) · Desktop 900–2200 (1366x768, 1920x1080) ·
Large ≥2200 (2560x1440) · UltraWide aspect≥2.1 & w≥1800 (3440x1440). Compact = Mobile|Tablet (sidebars become trays/overlays).
Frame names carry `WxH`; implement differences with `windowLayout()` / `layout.isCompactWidth`, never with raw pixel checks.

## 6. Pitfalls

* Penpot MCP plugin storage resets if a call times out (≈30 s): keep each `execute_code` call small, rebuild the helper library from the
  `Legend` page plugin data (`visageLib`, `visagePatch*`, `visageIcons`, `visageImgs`) — see `tools/penpot/` for the icon/palette JSON.
* `penpot.openPage(page)` is async: `await` it, and shapes are created on the *current* page. Text `width/height` read 1 until
  `await penpot.waitForLayoutUpdate()`. `findByName('Roboto')` returns Roboto Mono — filter `findAllByName` by exact name.
* `fetch` is blocked in the plugin; images via `penpot.uploadMediaUrl`, icons by SVG path (`createShapeFromSvg`, take the path child).
* Penpot has no backdrop blur: a glass panel is a clipped board with a blurred copy of the page background underneath a tint. It is a
  picture of Haze, not an instruction.
* Layer text is sample data. Do not copy copy ("Aurora Nights") into code. Colours of the screenshot-derived frames are the default seed;
  real users have dynamic/Matugen themes.
* Do not trust a screenshot taken right after navigation; wait and re-shoot.

## 7. Adding a new screen/component to Penpot

1. Capture the real thing under Xvfb at the needed sizes (§1 step 5). 2. Reuse existing component builders/instances; add a new component
page entry only if it is a new Kotlin symbol. 3. Name layers per §2, annotate glass per §3, place the frame on the matching
`Screens · …` page named `<Composable> / <Breakpoint WxH> / <state>`. 4. Add a Legend row (element → symbol → file → params) and a
row in `docs/penpot-coverage.md`. 5. Compare `export_shape` of the frame to the screenshot.

## 8. Capture harness and Now Playing builders

* `tools/penpot/capture/` (`sess.sh`, `live.sh`, `edges.py`, README) is the Xvfb harness. The scratchpad copies get wiped between days, so keep
  using the repo copy (`SC=<dir>` env). To play a real song: `live.sh start 1920x1080`, wait 100 s, click the Search tab, wait 60 s,
  click the field, `xdotool type`, Enter, click a Songs row, then `live.sh nav now_playing`; click the lyrics tool button for lyrics.
* Now Playing is built by plugin-data builders `H.npIdle(W,H,{playing,x0})` and `H.npLyrics(W,H,{synced,canvas,playing,x0,track})`
  (patches 21/22 on the Legend page; they depend on `H.homeA`, `H.seekBar`, `H.loadingIndicator`, `H.icon`). Formulas copied from
  `NowPlayingScreen.kt`: `nowPlayingIdleMetrics`, stacked when pane < 760dp or taller than 1.25x width, lyrics stack = min(0.44*maxW, 440),
  triple layout at maxW >= 1700 and ratio >= 1.85, thumb 72dp in the card unless a canvas shows on the right, controls card height =
  20 + title(lines x 36|28) + 6 + 24 (+20 album) + 28 + 56 + 28 + 56 + 20.
* Lyrics text in frames is placeholder copy (never paste real song lyrics).

## 9. Keep this skill current (standing rule from Alex)

After every batch of frames/builders: update this file (new builders, formulas, harness rules, pitfalls), `docs/penpot-coverage.md`
(honest done / partial / missing) and the Legend page, then commit locally (`git add -f`, never push). A future session must be able to
continue from these files alone, without this conversation.

## 10. Settings page builders (patches 23-26 on the Legend page)

`H.sFrame(W,H,x0,name,title)` = `homeA` frame (queue docked, no library) + back button + `subPageTitle` (24/32, not pageTitle). Content column x=`356+round((W-356-711)/2)`, width 711.
Rows (`H.sRowGen`): switch-with-subtitle 75dp, other with subtitle 71, plain 67, slider 91-99, shortcut 81; card padding 4 top / 3 bottom, section gap 24, label->card 28.
Controls: `sSeg` (segmented 380x40, selected pill white on `#20272c`), `sSwitch` (on = white track + dark thumb, off = outlined 2dp), `sSlider` (16dp track, 4x44 thumb, stop dots),
`sPalette` (9 palette tiles, real swatch colours measured from the capture), `sPreview` (black outlined card with a placeholder well), `sFolderCard`, `sSearchField`, `sShortcutRow`.
Page builders: `sInterface, sTheme, sPlayback, sAccount, sSongSwap, sDownloads, sLocal, sDiag, sKeybinds, sSidebars, sLyrics, sFsPlayer, sNowPlayingSettings`.
Heavy calls (>~25 s) time out and still half-run: build 2 frames per call, then list `penpot.root.children` and delete duplicates by x position.
Never put machine-specific values (paths, kernel, user names) in frames.

## 11. Page builders for list/browse screens (patches 27-30)

`H.pgFrame(x0,name,title)` = Desktop 1920x1080 frame with queue + library docked and only the library toggle lit (Library/Charts/History/Tag Manager captures), `H.pgHeader` (back circle 44 + subPageTitle + optional search pill 320x44), `H.pgMini`.
Built: `pgHistory`, `pgTagManager` (table: rows pitch 68, h60, columns Title 492 / Artist 749 / Album 935 / Album artist 1121 / Year 1261 / Genre 1325 / # 1441 / Cover 1488), `pgCharts` (top-3 cards 409/355/356 wide + 2-column ranked list), `pgNewReleases` (6x grid, cell pitch 195x241, cover 173).
TODO next (in this order): Library home, Search idle + results (play a real song with `live.sh`), Moods & genres (needs a procedural pattern-art builder), Fullscreen player (capture via the UI, not the navfile), popup player, dialogs/onboarding, then the other breakpoints and light/blur-off for every screen.
Real songs with synced lyrics for captures: search "pinkpantheress" in the harness (Paxsenix lyrics worked within ~2 minutes).
