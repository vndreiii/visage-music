# Responsive layout

One breakpoint system: `composeApp/.../ui/responsive/WindowLayout.kt`. It is provided once in `App.kt`
(`ProvideWindowLayoutFromWindow`) from the window's size in dp. Read it anywhere with `windowLayout()`.

## Classes (window width in dp, `px / density`)

| Class | Rule | Targets |
|-------|------|---------|
| `Mobile` | < 600 | 360x560, 390x844 |
| `Tablet` | 600..900 | 800x1280 portrait |
| `Desktop` | 900..2200 | 1024x768, 1280x800, 1366x768, 1920x1080 |
| `Large` | >= 2200 | 2560x1440 |
| `UltraWide` | aspect >= 2.1 and width >= 1800 | 3440x1440, 2560x1080 |

Minimum window size is 360x560 (`alex.kt`). Dev aids: `VISAGE_WINDOW_SIZE=WxH`, `VISAGE_DEV_ROUTE=now_playing`,
`VISAGE_DEV_QUEUE=1`, `VISAGE_DEV_LYRICS=1`.

## API

```kotlin
val layout = windowLayout()
layout.cls                      // LayoutClass
layout.isCompactWidth           // Mobile or Tablet: single column, sidebars become overlays/trays
layout.isMobile / isDesktopUp / isExpanded / isUltraWide / isPortrait / isShort
layout.pagePadding              // 12 / 20 / 28 dp
layout.contentMaxWidth          // Infinity on compact, 1600 / 1900 / 2200 on Desktop / Large / UltraWide
layout.gridColumns(minCellWidth, available = layout.width, gap = 12.dp)   // >= 1
ContentWidth { ... }            // centres and caps content at contentMaxWidth
gridColumnsFor(available, minCell, gap)   // pure; use with your pane's BoxWithConstraints width
ProvideWindowLayout(w, h) { }   // tests / previews
```

Rules of thumb:

* Use the **window class** for chrome decisions (nav, sidebars, trays, paddings).
* Use your pane's own `BoxWithConstraints` width for arranging its content: a screen next to a docked sidebar has less
  room than the window (Now Playing does this: `stacked = maxWidth < 760.dp || portrait-ish`).
* Pages: wrap in `ContentWidth`, use `LazyVerticalGrid(GridCells.Fixed(layout.gridColumns(160.dp, paneWidth)))` instead
  of fixed counts, replace fixed `.width(360.dp)` panels with `widthIn(max = ...)`/`fillMaxWidth()` on compact.
* Never let a row of fixed-size controls exceed 336dp (360 minus padding); use `Arrangement.SpaceBetween` / wrap.

## Already done

* Top bar: below Desktop the tabs collapse into a hamburger menu; logo hidden on Mobile; title ellipsizes.
* `AppScaffold`: on compact windows the pinned queue opens as the bottom tray and the library sidebar is a modal overlay
  (toggle button / Ctrl+Shift+L); docked beside the page on Desktop and up.
* Now Playing: stacked (media on top, controls, tools row) in narrow panes; side by side otherwise; cover | lyrics |
  controls when lyrics are open on ultra wide panes; controls column capped on Large. Fullscreen player: tighter paddings,
  lyrics replace the cover on narrow screens, the queue overlay fills the width.

## To do (per page)

Home, Search, Library, Settings, Album/Artist/Playlist, Charts, Moods, Tag manager: all still use fixed widths/columns.
