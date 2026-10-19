# UI kit

Everything reusable about how the app looks and moves. Change things **here**, not at call sites.

## Motion — `ui/motion/VisageMotion.kt`
One object holds every animation spec; nothing in the UI should call `spring(...)`/`tween(...)` directly.

| Token | Use |
|-------|-----|
| `spatialFast / spatialDefault / spatialSlow` | Material 3 Expressive spatial springs (movement, size, **shape morph**; they may overshoot). fast 0.6/800 · default 0.8/380 · slow 0.8/200 |
| `effectsFast / effectsDefault / effectsSlow` | Expressive effect springs (colour, alpha; never overshoot). 1.0 damping, 3800 / 1600 / 800 |
| `glide`, `settle`, `rail` | The app's signature layout glide (Now Playing lyrics shift, side queue). `settle` is the no-overshoot variant, `rail` the slow heavy one for big panels |
| `press`, `pressDown`, `bounce`, `morph` | Touch feedback, selected-icon bounce, loading-indicator shape morph |
| `fade`, `fadeInSpec`, `fadeOutSpec`, `ambient`, `colorShift` | Deliberate fades and slow ambient/colour changes (palette per track, theme) |
| `linear(ms)`, `pulse(delay)` | Constant-speed loops and breathing placeholders |
| `menuEnter/menuExit(origin)` | Anchored popups, same feel as Material's dropdown |

## Material 3 Expressive components — `ui/expressive/`
Compose Multiplatform doesn't expose the official Expressive components on desktop (checked on CMP 1.10.2 **and** 1.12.1: `MaterialExpressiveTheme`, `MotionScheme`, `LoadingIndicator`, `ButtonGroup`, `ToggleButton`… are internal/absent), so this is our own implementation of the spec. `vivi-music/` (Android, official components) is the visual reference; Google's M3 Expressive docs and the shadcn Material page are the spec references.

- `ExpressiveButton(...)` — 5 sizes (`ExpressiveSize.XSmall…XLarge`), 5 styles (`ExpressiveStyle.Filled/Tonal/Outlined/Elevated/Text`), round or `square`, `selected = true/false` makes it a toggle, shape morphs on press and when toggled.
- `ConnectedButtonGroup(options, selected, onSelect)` — replaces segmented buttons; outer corners round, inner tight, selected item is a full pill.
- `ExpressiveSplitButton(...)` — action + chevron menu trigger.
- `ExpressiveCard(onClick, restRadius, pressedRadius, ...)` — tappable container whose corners tighten while pressed (Library cards/tiles use it).
- `ChipsRow` (ui/component) — filter rows built from `ExpressiveButton` toggles; the top nav chips use the same press morph.
- Existing Expressive-style pieces: `VisageLoadingIndicator` (morphing shapes via graphics-shapes), `PlayerSeekBar` (wavy track and pill handle; the wave flattens when paused), `ShaderBackground`.

`ExpressiveGallery()` shows everything. **Adding or changing a component:** edit it, add it to the gallery, then render and look:

```bash
VIVI_RENDER_OUT=/tmp/ui ./gradlew :composeApp:desktopTest --tests "*ExpressiveGalleryTest*"   # -> /tmp/ui/expressive-gallery.png
```

## Shared components — `ui/component/`
| Component | Use |
|-----------|-----|
| `VisageBackButton` | The one round "<" back button. Use it on every page. |
| `MainPane` | The content area between the sidebars. Toasts and overlays centre on it. |
| `VisageToast` | Short messages, centred on the main pane. |
| `GeneratedCover`, `CoverTiles`, `CoverLayout` | Cover art for items with no image. Flat colours, no gradients, no overlap. Tiles show up to four covers. |
| `MoodCard`, `MoodArt` | Moods and genres cards. Shapes fill the card. The title sits on a solid plate at the bottom. |
| `DetailPageHeader` | Header of album, artist and playlist pages. |
| `HorizontalWheelScroll`, `SmoothWheelScroll`, `WheelInput` | Wheel and touchpad scrolling. `WheelClassifier` tells a wheel from a touchpad. Tilt wheels and side buttons work. |
| `interactiveScale` | Hover and press scale for clickable items. |
| `YouTubeItems`, `ChipsRow`, `PageStates` | Item rows and cards, filter chips, loading, empty and error states. |

Rules: every state change animates (use `VisageMotion`). Use generous padding. Do not draw borders on cover tiles.

## Lists and pages
- Continuous lists have no per-item divider.
- Clickable rows use `RoundedClickable` or a `Surface` with `RoundedCornerShape(12.dp)`.
- Scrolling lists leave 120 dp at the bottom for the mini player.
- The library sidebar and the queue sidebar are always available, except on Now Playing and Settings.

## Backgrounds — `ui/player/background/`
`ShaderBackground(kind, palette, artworkUrl)` draws one of `BackgroundShader` (Aurora, Glow, Mesh, Wave, XMB, Blur, Distort) from the cover palette; sources are in `Shaders.kt` (SkSL). Shared inputs: `iResolution`, `iTime`, `c0..c4` (palette), optionally `img`/`imgSize` (shrunk cover).

```bash
VIVI_SHADER_OUT=/tmp/shaders ./gradlew :composeApp:desktopTest --tests "*ShaderRenderTest*"      # -> PNG per shader at two times
```
The test also fails if a shader doesn't compile, renders blank, or doesn't animate. **New shader:** add the SkSL body + a `BackgroundShader` entry, wire it in `PlayerBackgroundLayer`, run the test.

## Native helpers — `native/`
See [`native/README.md`](../native/README.md) (scan, search, MPRIS, canvas, artist lookup, tags).

Related: [`architecture.md`](architecture.md).
