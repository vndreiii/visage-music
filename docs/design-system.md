# VIsage design system

How every piece of the UI is built and why. It describes what the app does today. When you add or change UI, follow this
file; when you must break a rule, change the rule here in the same commit. `docs/ui-kit.md` covers the Expressive
components and motion in more depth; this file is the rulebook that ties everything together.

Rule zero: **use a token, not a number.** Text sizes come from `VisageType`, colours from `MaterialTheme.colorScheme`,
animation from `VisageMotion`, page spacing from the constants in `ui/component`. A literal `fontSize = 13.sp` or
`spring(...)` at a call site is a bug.

---

## 1. Typography

Code: `theme/VisageTypography.kt` (fonts and weights), `theme/VisageType.kt` (named roles).

Fonts: headings use the *heading* font (default Manrope), everything calmer uses the *body* font (default Roboto). Both
can be changed in Settings → Interface → Typography. Never set `fontFamily` on a `Text`.

Always pick a **role** from `VisageType`. Each maps to one Material 3 slot, so the size is the same everywhere.

| Role | Slot | Size / line | Weight | Font | Use |
|------|------|-------------|--------|------|-----|
| `pageTitle` | headlineMedium | 28 / 36 | SemiBold | heading | Top-level page title ("Settings") |
| `subPageTitle` | headlineSmall | 24 / 32 | SemiBold | heading | Sub-page and detail headers next to the back button; empty/error state titles |
| `sectionTitle` | titleLarge | 22 / 28 | SemiBold | heading | Shelf and group headings (`NavigationTitle`) |
| `groupLabel` | titleSmall | 14 / 20 | Medium | body | Coloured label above a settings card |
| `cardTitle` | titleMedium | 16 / 24 | Medium | body | Card, tile and dialog-section titles |
| `rowTitle` | bodyLarge | 16 / 24 | Regular | body | Main line of a list row or setting |
| `rowSubtitle` | bodyMedium | 14 / 20 | Regular | body | Second line (artist, album, description). Colour `onSurfaceVariant` |
| `caption` | bodySmall | 12 / 16 | Regular | body | Hints, counts, footnotes |
| `control` | labelLarge | 14 / 20 | Medium | body | Buttons, chips, tabs, menu items |
| `readout` | labelMedium | 12 / 16 | Medium | body | Changing numbers: elapsed/total time, volume %, counters |
| `badge` | labelSmall | 11 / 16 | Medium | body | "New", explicit "E", other tiny badges |

Rules
- Same kind of text, same role. A time stamp and a volume percentage are both `readout`; they must match.
- Do not scale text by hand. If a role is wrong for a place, fix the place or add a role here.
- Exceptions that are allowed to have their own size: lyrics (user-controlled `fontSizeSp`), chart rank numerals,
  generated cover initials, mood-card titles (sized to the card), and the settings mock-ups. Everything else uses roles.
- Titles: one line with ellipsis unless the page is about reading it (then wrap, max 2 lines).
- Text on a photo or on glass uses `LocalPlayerInk` (section 3), never a hard-coded white or black.

## 2. Colour

Everything is `MaterialTheme.colorScheme` (Material 3 scheme generated from the theme, the cover palette, or Matugen).
No hard-coded colours except pure black/white scrims and the cover-palette colours.

| Need | Token |
|------|-------|
| Page | `background` / `surface` |
| Card on a page | `surfaceContainerHigh` (or `GlassSurface`, section 4) |
| Raised element on a card | `surfaceContainerHighest` |
| Accent, selected, primary action | `primary` / `primaryContainer` with `onPrimary` / `onPrimaryContainer` |
| Secondary action, selected chip | `secondaryContainer` |
| Search highlight, flash | `tertiaryContainer`, `primary` |
| Secondary text and icons | `onSurfaceVariant` |
| Dividers | `outlineVariant` at 30% |
| Destructive and refused-drop | `error` |

Contrast: text must reach **WCAG AAA 7:1** on whatever is behind it. `theme/AaaContrast.kt` (`withAaaContrast`) nudges a
colour until it does; light themes assume a #CCCCCC backdrop worst case behind glass. If you add a new surface with text
on it, route the text colour through it or through `PlayerInk`. Test: `AaaContrastTest`.

Never use opacity to dim text on solid backgrounds (it loses contrast); use `onSurfaceVariant`. Opacity is fine on
artwork backdrops via `ink.text(alpha)`.

## 3. Ink on the player

`theme/PlayerInk.kt`. `ProvidePlayerInk` decides whether text over the player is white or black:
- artwork backdrop: white (black when a light theme washes it pale),
- solid theme background: the colour that reaches 7:1,
- **framed controls card in a light theme: white**, because the card has a dark scrim.

Read it with `LocalPlayerInk.current`: `ink.text(alpha)` for text/icons, `ink.fill(alpha)` for translucent fills,
`ink.inverse` for content on a filled ink shape.

## 4. Surfaces, glass and blur

Code: `ui/glass/Glass.kt`.

- Three levels: page (`PageBackground`), card/panel (rounded 20–28 dp), floating (mini-player, toasts, menus).
- **Material 3 Expressive blur** (Settings → Interface) turns panels into frosted glass. Anything that is a panel must
  use `GlassSurface(shape, fallbackColor, tint)`; it is glass when blur is on and a solid `fallbackColor` card when off.
  A plain `Surface`/`OutlinedTextField` will look wrong with blur on.
- `tint = panelGlassTint(alpha)`: 0.45 default, 0.5 for home shelves and sidebars.
- Haze rule: a `hazeChild` cannot blur a source it is a descendant of. Pages blur `LocalPageHazeState` (fed only by
  `PageBackground`); sidebars and the mini-player blur `LocalHazeState`. Do not nest glass inside glass.
- No borders on cover art. A `0.dp` border still draws a hairline: omit the border modifier instead.
- Shadows: none on search bars or flat cards. Only floating elements (mini-player, menus, dialogs) cast one.

## 5. Shape

| Radius | Use |
|--------|-----|
| 6 dp | Tiny thumbnails (`ThumbnailCornerRadius`) |
| 8–10 dp | Small chips, tags |
| 12 dp | Clickable rows (`InteractionCornerRadius`), menus |
| 14–16 dp | Inputs, small cards, list-item cover art |
| 20 dp | Settings group cards, shelf cards |
| 24 dp | Cover tiles, large cards, dialog covers |
| 28 dp | Sheets, dialogs, the framed controls card, search bars |
| 999 dp / `CircleShape` | Pills, round buttons, chips, avatars |

Pressed Expressive elements tighten their corners (shape morph); see `ExpressiveCard`. Artist photos are circles; album,
playlist and folder covers are rounded squares; videos are 16:9 with 12–16 dp corners.

## 6. Spacing and layout

Grid is 4 dp; common steps 4 · 8 · 12 · 16 · 20 · 24.

Page constants (`ui/component/DetailPageHeader.kt`, `Dimensions.kt`):

| Constant | Value | Meaning |
|----------|-------|---------|
| `PageInset` | 20 dp | Left/right padding of every page |
| `PageHeaderTopPadding` | 8 dp | Above the header |
| `PageHeaderBottomPadding` | 16 dp | Header to first content |
| `MiniPlayerClearance` | 140 dp | Bottom padding so the last row clears the mini-player |
| `ListItemHeight` | 64 dp | A list row; thumbnail `ListThumbnailSize` 48 dp |
| `GridThumbnailHeight` | 128 dp | Grid cards (width = height × aspect + 16 dp) |

Page skeleton, for every page:
1. `DetailPageHeader(title, onBack, trailing)` (sub-pages) or the page title (top-level pages).
2. Scrolling content with `contentPadding(start = PageInset, end = PageInset, bottom = MiniPlayerClearance)`.
3. `Modifier.pageScrollEdge { state.edgeProgress() }` on the scrolling container: content fades and the top corners
   round as it scrolls under the header. No hard cut anywhere.
4. `smoothWheelScroll(state)` for mouse-wheel and touchpad.
5. Loading, empty and error with `PageLoadingState` / `PageEmptyState` / `PageErrorState`, never ad-hoc text.

Settings pages are centred, max width 760 dp (600 dp with Adwaita-inspired), 24 dp side padding.

Alignment: titles, search bars and content on a page share the same left edge (`PageInset`). Icons in a column share one
x position; row icon is 22 dp with an 18 dp gap to text, 16 dp from the card edge.

## 7. Icons

- Material Symbols *Outlined* (`Icons.Outlined.*`), `AutoMirrored` where a direction exists.
- Sizes: 16 dp inline in text, 20 dp in buttons and chips (`ExpressiveSize` icon sizes), 22 dp in settings rows, 24 dp
  in toolbars and menus, 36 dp as a hover "choose photo" glyph.
- Menu items: icon in a fixed 24 dp slot, glyph 20 dp, so labels align.
- Icon colour follows its text (`onSurfaceVariant` for secondary).
- Icon-only buttons are at least 40 dp with a tooltip or content description.

## 8. Components

### Buttons — `ui/expressive/ExpressiveButtons.kt`
`ExpressiveButton(label, onClick, style, size, square, selected, enabled)`.
Styles: `Filled` (the one primary action), `Tonal` (secondary), `Outlined`, `Elevated`, `Text` (Cancel, tertiary).
Sizes: XSmall 32 · Small 40 · Medium 56 · Large 96 · XLarge 136 dp tall. Dialog actions are Text + Filled, Small/Medium.
One Filled button per view. Destructive confirmation uses the `error` colour, never Filled primary.
Related: `ConnectedButtonGroup` (segmented choice, 2–4 options), `ExpressiveSplitButton`.

### Chips — `ChipsRow`, `NewChip`
Filter chips are `ExpressiveButton` toggles in a horizontal `ChipsRow`; selected = `secondaryContainer`. Badges
(`NewChip`) are `primary` pills with `badge` text. A "New" chip sits on a settings category until it is opened, and
next to the new setting inside it.

### Cards and tiles
- Item cards (`YouTubeGridItem`): cover `GridThumbnailHeight`, `cardTitle`/`rowTitle` one line, `rowSubtitle` one line.
  Artist names in subtitles are individually clickable (underlined) and go to the artist page.
- `ExpressiveCard` for tappable containers with pressed corner tightening.
- Shelves (`HomeCategoryFrame`): a `GlassSurface` card with `NavigationTitle` and a trailing action row (refresh, shuffle,
  play all).
- Cover fallback: `GeneratedCover` (flat colour + initials, no gradient). Never leave a grey box.
- Hover: `interactiveScale`; pressed: shape morph. Both on every clickable card.

### List rows — `YouTubeListItem`, `RoundedClickable`
64 dp row, 48 dp cover, `rowTitle` + `rowSubtitle`, trailing duration as `readout`. No dividers between rows. Active
track shows the animated playing glyph over the cover. Clickable rows clip to 12 dp.

### Settings — `ui/settings/SettingsScreens.kt`
Build only from: `SettingsPage`, `SettingsSection(title)` (a `groupLabel` over a 20 dp `SettingsGroupCard`),
`SettingsNavItem` (chevron), `SwitchItem`, `ChoiceRow` (≤4 short options), `ChipChoiceRow` (many), `PrefSecondsSlider`,
`SettingsDivider`. All go through `SettingsRowFrame`, which handles search highlight, the scroll-into-view flash and the
"New" chip.
- Title is a noun phrase; description is one sentence, no trailing full stop needed.
- Put a setting on the page where a person would look for it, not where the code lives.
- **Search:** the settings list is generated into `ui/settings/SettingsIndex.kt` by
  `tools/gen-settings-index.py`. It is regenerated automatically before every desktop compile (Gradle task `generateSettingsIndex`); a new settings screen must be added to `fnroute` in the script, which fails loudly if one is missing.
- **New chip:** add the setting to `SettingsNew.items` with a higher `rev`.

### Dialogs — `LibraryDialogShell`
28 dp card, title (`subPageTitle`-sized heading), one-line subtitle in `onSurfaceVariant`, content in labelled sections
(`DialogSection`), actions bottom-right: Text "Cancel" then Filled confirm. Max width 520–760 dp. Cover pickers show a
live preview tile that is also a drop target and opens the photo chooser on click.
Confirmation dialogs (`AlertDialog`) are only for destructive or irreversible actions; say what is lost.

### Menus
Context menus (right click) and dropdowns use the Expressive menu enter/exit. Item = 24 dp icon slot + `control`
label. "Edit" (not "Rename") opens the edit dialog. Destructive items last, in `error`.

### Inputs
Text fields use `VisageTextField` (or the glass search box in settings). A field on a panel is a glass/tonal surface,
**not** an outlined default field. Search bars: 52 dp, 28 dp radius, icon aligned with the row icons beneath, no shadow.
Sliders: `PlayerSeekBar` for playback (wavy while playing, flat when paused), Material slider for settings.

### Toasts and states
`VisageToast` appears centred on the main pane, one line, auto-dismisses. Errors that need action use
`PageErrorState` with a Retry. A missing login shows a "Log in" state, never "request failed".

### Player
- Mini-player: floating, glass, `PlayerInk` text, 140 dp clearance contract for pages.
- Now Playing: cover, then the controls card (`playerControlsSurface`: frosted glass with a dark scrim, white ink).
  Times and volume are `readout`; Play/Pause is the Filled expressive button. (Track title/artist sizes on this screen
  are still set per layout and should be moved onto roles.)
- Seek and volume share one slider component; the % label is `readout` with a 40 dp minimum width.
- Lyrics: size comes from the user's setting, scenes (rows, 3D, cuts) share the line renderer.

### Home, search, library, artist
- Home: quick picks (12 songs in 3 columns × 4, refreshable, persisted, auto-refreshed on an interval), shelves in glass
  frames, "Jump back in" cards (right-click → Remove from history).
- Artist page: portrait header in its own glass container, stat chips (subscribers, monthly audience) with icons, then
  Top songs, Albums, Singles, Videos (16:9 grid) with "Show more".
- Library sidebar: folders and playlists; drag a track onto an entry to add it, refused targets tint red.

## 9. Motion

All motion is `VisageMotion` (see `docs/ui-kit.md`). Rules: movement and size use the *spatial* springs (may overshoot),
colour and alpha use the *effects* springs (never overshoot). Hover/press ≤ 150 ms perceived. One-shot attention pulses
(search result flash) ease in ~400 ms and out ~700 ms. Nothing pops: every appear/disappear fades or scales.

## 10. Interaction and input

- Everything clickable has hover, pressed and focus states and a pointer cursor.
- Right-click opens the context menu; long-press is not used on desktop.
- Wheel/touchpad scrolling goes through `smoothWheelScroll`; horizontal shelves accept the wheel too.
- Keyboard shortcuts are configurable (Settings → Keyboard shortcuts); do not hard-code new ones.
- Drag and drop: tracks can be carried onto sidebar targets; images can be dropped on cover tiles.

## 11. Accessibility checklist

- 7:1 text contrast on every surface, both themes (section 2).
- Every icon-only control has a `contentDescription`.
- Minimum hit target 40 dp.
- Never convey state by colour alone (active track also has the playing glyph; errors also have text).
- Text is real text (selectable where it is content), not an image.

## 12. Adding something new — checklist

1. Is there already a component in `ui/component`, `ui/expressive` or the settings kit? Use it.
2. Text: pick a `VisageType` role. Colour: a scheme token. Spacing: the constants and the 4 dp grid.
3. Panels: `GlassSurface`. Check it with blur **on** and **off**, in dark and light themes.
4. Animate every state change with `VisageMotion`.
5. Page? Follow the skeleton in section 6, including `MiniPlayerClearance` and `pageScrollEdge`.
6. Setting? Add it to the right page, regenerate the search index, add a `SettingsNew` entry.
7. Update this file if a rule changed. Run `./gradlew :composeApp:desktopTest` and look at the UI.
