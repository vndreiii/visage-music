# Penpot ⇄ Compose mapping contract

The Penpot file **"VIsage UI"** (design.penpot.app) mirrors the real UI 1:1. Every layer is named after the Kotlin symbol it
represents so a model can go *frame → layer name → composable → edit*. Source of truth for values is the Kotlin code
(`docs/design-system.md`, `docs/ui-kit.md`, `ui/component/Dimensions.kt`, `theme/*`). Penpot is the *visual language*; when
they disagree about a number, the code wins until the design is explicitly "applied".

Read `.claude/skills/visage-penpot/SKILL.md` for the workflow. Coverage status: `docs/penpot-coverage.md`.

## 1. Pages

| Page | Content |
|------|---------|
| `Foundations` | Colour (dark + light), `VisageType` roles, radii, spacing, breakpoints, blur/glass key |
| `Components` | One board per Compose component, variants/states side by side |
| `Legend` | Table: Penpot name → Kotlin symbol → file → parameters |
| `Screens · Home` … | One page per screen group, each frame = one breakpoint × state |

## 2. Naming

* **Component layers** use the exact Kotlin symbol: `GlassSurface`, `HomeCategoryFrame`, `YouTubeGridItem`, `YouTubeListItem`,
  `SettingsGroupCard`, `NavTray`, `VisageTopBar`, `PopupPlayerLayout`, `ExpressiveButton(Filled|Tonal|Outlined|Elevated|Text)`.
  Variant/state goes in brackets: `ExpressiveButtonFilled [size=Small state=hover]`.
* **Screen frames**: `<Composable> / <Breakpoint WxH> / <state>`, e.g. `HomeScreen / Desktop 1920x1080 / default`.
  Breakpoints: `Mobile 390x844`, `Mobile min 360x560`, `Tablet 800x1280`, `Desktop 1366x768`, `Desktop 1920x1080`,
  `Large 2560x1440`, `UltraWide 3440x1440`.
  States: `default loading empty error queue-open queue-closed lyrics lyrics-none video blur-on blur-off dark light tray-open dialog`.
* Inside a screen, direct children are named after the composable (or `Slot:<name>` for a plain layout box). Plain
  decoration (dividers, placeholder covers) is named `~cover`, `~divider`, `~glyph`; a `~` prefix means "not a symbol, ignore".
* Layout boards use Penpot flex layout where the Compose code uses Row/Column; gap/padding = the Kotlin number.
* Dimensions in Penpot are **dp 1:1** (1 Penpot px = 1 dp).

## 3. Blur / glass rule (important)

Any element that is frosted glass or blurred in the app is a Penpot element **with a blur effect** (layer blur 12) AND
a name carrying the real class and parameters:

```
GlassSurface · shape=RoundedCornerShape(24) · tint=panelGlassTint(0.5f) · fallback=surfaceContainerHigh
GlassBackdrop · tint=panelGlassTint(0.45f) · stateOverride=LocalPageHazeState
playerControlsSurface · frosted+dark scrim · ink=white
```

The Penpot blur is only an approximation of Haze. **Never copy Penpot blur values into code.** Implementers use the real
class (`ui/glass/Glass.kt`: `GlassSurface`, `GlassBackdrop`, `glassSource`, `ProvideGlass`, `panelGlassTint`) which falls back
to a solid `fallbackColor` card when the user's blur setting is off. Blur-off variants are separate frames with the `blur-off`
state and a solid fill. Glass-less panels are named `Surface · …`.

Other special classes are annotated the same way in the layer name: `LocalPlayerInk` (text over glass/photo),
`visageMarquee` (scrolling title), `PlayerSeekBar` (wavy while playing), `pageScrollEdge`, `interactiveScale`,
`ExpressiveCard` (corner morph on press), `PanelInset` padding.

## 4. Tokens

Penpot **colours** (library colours, name = Kotlin path, grouped `dark/…` and `light/…`):
`colorScheme.primary`, `.onPrimary`, `.primaryContainer`, `.onPrimaryContainer`, `.secondary*`, `.tertiary*`, `.error*`,
`.background`, `.surface`, `.onSurface`, `.surfaceVariant`, `.onSurfaceVariant`, `.outline`, `.outlineVariant`,
`.surfaceContainerLowest|Low|(plain)|High|Highest`, `.inverse*`, `.scrim`.
The real app generates the scheme from a seed (default `DefaultThemeColorArgb = 0xFF8BA3B8`, TonalSpot, SPEC_2025,
`theme/VisageTheme.kt: visageColorScheme`) or the artwork palette or Matugen. **The Penpot values are an approximation of
the default seed** (computed from CIELAB tones); the *role name* is what matters, never hard-code the hex.

Penpot **typographies** (name = `VisageType.<role>`): pageTitle 28/36 SemiBold, subPageTitle 24/32 SemiBold, sectionTitle 22/28
SemiBold (heading font Manrope); groupLabel 14/20 Medium, cardTitle 16/24 Medium, rowTitle 16/24 Regular, rowSubtitle 14/20
Regular, caption 12/16 Regular, control 14/20 Medium, readout 12/16 Medium, badge 11/16 Medium (body font Roboto).

**Spacing / dimension constants** (shown on `Foundations`): `PageInset` 20, `PageHeaderTopPadding` 8, `PageHeaderBottomPadding` 16,
`MiniPlayerClearance` 140, `ListItemHeight` 64, `ListThumbnailSize` 48, `GridThumbnailHeight` 128, `ThumbnailCornerRadius` 6,
`PanelInset` 20 (`PanelInsetCompact` 16 on Mobile/Tablet), `PanelHeaderGap` 12, queue sidebar 360, settings page max 760.

**Radii**: 6 thumbnail · 8–10 chip · 12 clickable row/menu · 14–16 input/list cover · 20 settings card/shelf · 24 cover tile/large card ·
28 sheet/dialog/search bar/controls card · 999 pill/circle.

**Breakpoints** (`ui/responsive/WindowLayout.kt`): Mobile <600 (pagePadding 12), Tablet 600–900 (20), Desktop 900–2200 (20),
Large ≥2200 (28), UltraWide aspect ≥2.1 & w ≥1800 (28). Compact = Mobile|Tablet (sidebars become trays/overlays).

## 4b. How the numbers were obtained

Colours: dumped from `visageColorScheme(...)` (default seed). Geometry/typography: Kotlin sources plus pixel measurements of Xvfb captures of the real app
(e.g. Desktop 1920x1080: queue panel x8 y72 w348, library sidebar x1572 w340, page pane x356..1572 with 20dp inset, Quick picks card 1176x315, rows pitch 54,
playlist covers 180 pitch 192). **Never invent a value**: measure or read the code.

## 5. Fidelity levels

Penpot frames are **structural wireframes with real tokens**, not pixel copies: covers are flat placeholder rectangles, text is
representative sample data. Geometry (dp sizes, paddings, radii, gaps), colours, type roles, glass flags and structure are exact
for the parts that were built; `docs/penpot-coverage.md` says which screens are `done / partial / missing`.

## 6. Reading a node as an implementer

1. Find the frame/element by name (`mcp__penpot__execute_code` + `penpotUtils.findShape`).
2. Parse the layer name (`Symbol · param=… · param=…`). Look it up on the `Legend` page / `docs/penpot-mapping.md`.
3. Compare geometry & colour role against the Kotlin; change only what the design changed, using tokens.
