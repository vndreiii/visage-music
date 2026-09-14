# Vivi Desktop: Exhaustive Material 3 Expressive (M3E) UI/UX Audit

This document provides a comprehensive, file-by-file UI/UX analysis of the Vivi Desktop app. It identifies where the current implementation deviates from modern **Material 3 Expressive (M3E)** guidelines and provides detailed architectural and component-level solutions.

---

## 1. Global Architectural Anti-Patterns & M3E Fixes

Before touching specific screens, the foundational Compose modifiers and layout logic must be fixed across the entire app.

### A. The "Divider Abuse"
The codebase currently relies heavily on `HorizontalDivider` to separate continuous list items. 
*   **Violating Files:** `TrackActionsSheet.kt`, `AudioOutputSheet.kt`, `SearchScreen.kt`, `SettingsScreens.kt`, `LibraryScreen.kt`, `ArtistScreen.kt`.
*   **M3E Rule:** Dividers are for separating distinct semantic groupings (e.g., "Pinned" vs "Unpinned", or a Header from a scrollable body). Continuous lists (tracks, search results, settings, action menus) should **never** have dividers between every item.
*   **The Fix:** Remove all `HorizontalDivider` calls in list iteration loops. Rely purely on vertical whitespace (`padding` / `Arrangement.spacedBy`) and the natural alignment of text/images to group elements.

### B. Missing "Pill" Shapes & Bleeding Ripples
Currently, rows use `.clickable { }` directly on a full-width `Row`. This results in a harsh, flat rectangular ripple that bleeds to the edges of the screen or sheet.
*   **M3E Rule:** Interactive list items, menu choices, and search results should use "Pill" shapes or fully rounded rectangles (e.g., `RoundedCornerShape(16.dp)` or `50%` pill).
*   **The Fix:** Wrap the `Row` content in a `Surface(shape = RoundedCornerShape(12.dp), onClick = { ... })`. When hovered or clicked, the highlight will form a soft, floating pill rather than a flat rectangle. Apply this to `ActionRow`, `SuggestionRow`, `RecommendedRow`, and `YouTubeListItem`.

### C. Missing Desktop Tactility (Hover States)
*   **The Issue:** Compose Desktop doesn't automatically add hover scaling or elevation shifts like native desktop apps do.
*   **The Fix:** Create a custom modifier `.m3Hover()` that uses `PointerEventType.Enter` to animate a slight scale up (e.g., `scale(1.02f)`) and applies a subtle brightness overlay or elevation shadow when hovered.

### D. Missing List Animations
*   **The Issue:** Navigating and fetching data results in items snapping instantly onto the screen.
*   **The Fix:** Add `modifier = Modifier.animateItemPlacement()` inside all `LazyColumn` and `LazyRow` items. When grids resize or lists load, items should fluidly slide into place.

### E. Dynamic Color crossfading
*   **The Issue:** Color changes (if any) snap instantly.
*   **The Fix:** Extract colors from the current `NowPlaying` artwork. Pass them through `animateColorAsState(tween(600))` so the entire app (TopBar, active pills, text highlights) smoothly breathes into the new palette when a song changes.

---

## 2. Exhaustive Page-by-Page Audit

### Global Scaffolding (`AppScaffold.kt`, `ViviTopBar.kt`, `MiniPlayerBar.kt`)
*   **Navigation Rail/Bar:** The active tab needs an M3 pill-shaped indicator background (e.g., a lavender pill behind the "Home" icon). When switching tabs, this pill should use an `Animatable` to fluidly morph/slide to the new tab, not snap.
*   **Top Bar:** Remove the `HorizontalDivider` at the bottom of the Top Bar (found in code). Instead, animate the `TopAppBar`'s surface color and elevation (`shadowElevation`) to appear only when the user scrolls down, leaving it perfectly transparent when at the top.
*   **Mini Player:** The mini-player should have a slight floating elevation on hover. Clicking it should trigger a `SharedTransitionLayout` where the tiny thumbnail expands seamlessly into the fullscreen `NowPlayingScreen` artwork.

### Action Sheets (`TrackActionsSheet.kt`, `AudioOutputSheet.kt`)
*   **Complete Overhaul Needed:** These sheets are currently flat lists of rectangular ripples and dividers.
*   **Fix:** Remove every single `HorizontalDivider`. Change `ActionRow` to use a rounded `Surface`. Increase vertical padding slightly. Ensure the sheet itself uses a large corner radius (e.g., `32.dp`) top corners.

### Search Screen (`SearchScreen.kt`)
*   **Search Field:** The `BasicTextField` container should react to focus. When focused, animate the corner radius to be slightly sharper (e.g., `28.dp` down to `16.dp`) and dim the background of the rest of the screen to draw the eye.
*   **Suggestions & Results:** `SuggestionRow` and `RecommendedRow` need the M3 Pill treatment (rounded surfaces on hover/click). Strip out the dividers in the `suggestions.forEachIndexed` loop.
*   **Staggered Loading:** When search results return, they shouldn't just pop in. Apply a cascaded fade-and-slide-up entrance animation.

### Home Screen (`HomeScreen.kt` & Hero Cards)
*   **Carousels (`LazyRow`, `LazyHorizontalGrid`):** Ensure the `horizontalWheelScroll` modifier doesn't eat the overscroll effect. M3 requires an elastic stretch at the end of lists.
*   **Hero Cards (`YouTubeHeroCard.kt`):** These large cards need a subtle parallax effect. As the carousel scrolls horizontally, the background image inside the card should translate slightly in the opposite direction to create depth.
*   **Card Hover States:** On desktop, hovering a hero card should slightly increase its elevation (drop shadow) and morph its shape (e.g., `16.dp` corners tightening to `12.dp`).

### Library Screen (`LibraryScreen.kt`)
*   **Filter Chips:** The `ChipsRow` filter (Playlists, Songs, etc.) should use M3 pill shapes. When a filter is clicked, the transition of the list below should be animated (items sliding out and new items sliding in), rather than a harsh instant list replacement.
*   **Grid vs List Toggle:** If the user toggles between a grid view and a list view, this must be done using `AnimatedContent` so the elements morph into their new layout, rather than snapping.
*   **Dividers:** Remove `HorizontalDivider()` inside the `LazyColumn` iterations.

### Online Album / Playlist / Artist Screens
*   **Sliver App Bar (Collapsing Header):** The massive hero artwork at the top should collapse dynamically as the user scrolls. 
    *   **Animation:** As it shrinks, the artwork should apply a blur effect and fade out, seamlessly blending into the `ViviTopBar` color.
*   **Floating Action Button (FAB):** The main Play/Shuffle FAB should float prominently over the boundary of the artwork and the list. As the user scrolls past the header, the FAB should gracefully scale down to `0f` and disappear.

### Settings (`SettingsScreens.kt`)
*   **Divider Purge:** Remove dividers between individual toggles. Use dividers ONLY to separate major sections (e.g., separating the "Audio" block from the "Appearance" block).
*   **Accordion Animations:** Expanding a settings category (if implemented) must use `Modifier.animateContentSize()` so the list pushes down smoothly.
*   **M3 Switches:** Ensure custom toggle switches use the expressive thumb animation (thumb stretches slightly when dragged, and an icon appears inside the thumb when active).

### Now Playing & Lyrics Redesign (`NowPlayingScreen`, `LyricsPanel`)

The current implementation of the `NowPlayingScreen` when lyrics are active feels disjointed and unbalanced—the lyrics panel awkwardly floats on the left while the right side is completely barren, leaving the artwork and controls misaligned. 

**The Desktop 2-Pane Split (The Redesign):**
When the Lyrics toggle is engaged, the screen should immediately fluidly animate into a balanced, 2-pane layout (similar to Apple Music Desktop):
*   **The Left Pane (Lyrics):** This side is dedicated entirely to the lyrics engine. The lyrics `LazyColumn` takes up the full vertical height of this pane, allowing the text to breathe.
*   **The Right Pane (Artwork & Controls):** The currently playing UI collapses into a centered vertical stack on the right side. The large Album Artwork sits directly on top. Below it sits the Song Title and Artist (centered), and directly beneath that are the Playback/Seek controls. This creates a visually dense, perfectly balanced focal point on the right while the text animates on the left.

**The Lyrics Engine Porting (Mobile to Desktop):**
To power the Left Pane, the logic from the mobile app (`vivi-music/app/.../Lyrics.kt` and `LyricsUtils.kt`) must be ported properly:
1.  **Shared Parsing:** Move `LyricsUtils.kt` (which handles Regex parsing of `[MM:SS.mm]` and word-sync formats) into a `commonMain` module (e.g., `core` or `player`). 
2.  **Desktop Sync Loop:** In Compose Desktop, set up a `LaunchedEffect` that polls `LocalPlaybackController.current.playbackState.position` every ~50ms. Pass this position to `LyricsUtils.findCurrentLineIndex` to derive the `activeLineIndex`.
3.  **Desktop Scroll Actuation:** Desktop relies on mouse wheels, not touch dragging. Use `LazyListState.isScrollInProgress` to detect user intervention. If the user scrolls, set `isAutoScrollEnabled = false`. If auto-scroll is enabled, use `lazyListState.animateScrollToItem(activeLineIndex)` with a spring spec for buttery-smooth tracking.
4.  **M3E Text Highlighting:** Inside the `LazyColumn`, the active sung line must scale to `1.05x` (using `graphicsLayer { scaleX = ... }` to avoid layout thrashing) and adopt the dynamic Primary color. Inactive lines should dim to `0.4f` opacity.

*   **The "Flower" Loader:** When skipping tracks, the new album art on the Right Pane should load with the M3 segmented/flower indeterminate loader centered on a blurred placeholder.
*   **Track Change Transition:** The old cover should translate out (X-axis slide) while scaling down, and the new cover translates in.
*   **Dynamic Backgrounds (`PlayerBackgroundLayer.kt`):** Whether rendering a Canvas video or an algorithmic color gradient, track changes MUST crossfade over `600ms-800ms`.

---

## 3. Implementation Roadmap
1.  **Phase 1: The Purge.** Global find-and-replace to eliminate `HorizontalDivider` in all continuous lists.
2.  **Phase 2: Pill Interactions.** Update base components (`YouTubeListItem`, `ActionRow`) to use rounded `Surface` boundaries for correct M3 ripple mapping.
3.  **Phase 3: Color Engine.** Implement the Monet/Material You color extraction and wire `animateColorAsState` into the global `MaterialTheme`.
4.  **Phase 4: Now Playing Redesign.** Architect the 2-Pane split for the `NowPlayingScreen`, port `LyricsUtils.kt` to `commonMain`, and wire up the desktop-specific `LazyListState` auto-scroll logic.
5.  **Phase 5: Fluidity.** Introduce `animateItemPlacement()`, `AnimatedContent` for navigation, and `animateContentSize()` for expanding elements.

### Album Artwork Overflow Fix (`NowPlayingFullscreenScreen.kt`)
**The Issue:** When the application window is extremely wide (like maximized on a 16:9 or 21:9 monitor), the album artwork scales up so massively that it overflows its vertical bounds, bleeding under the top bar. This is caused by `Modifier.fillMaxWidth(0.72f).aspectRatio(1f)` inside a `Row` weight. It forces the image height to match its massive calculated width without respecting the parent container's maximum height.
**The Fix:** You must constrain the square image to the *smallest* available dimension of its container using `BoxWithConstraints`.

**Code Replacement:**
In `NowPlayingFullscreenScreen.kt`, replace the `Box` containing the `AsyncImage` with this:

```kotlin
if (!hideThumbnail) {
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(32.dp), // Safe breathing room from edges
        contentAlignment = Alignment.Center,
    ) {
        // Calculate the maximum square size that will safely fit
        val size = minOf(maxWidth, maxHeight).coerceAtMost(600.dp) // Optional max cap
        
        AsyncImage(
            model = track?.artworkUrl,
            contentDescription = track?.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .shadow(28.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp)),
        )
    }
}
```
### Playlist UI Overhaul
**The Issue:** The current Playlist view is a flat, infinite list of rectangular items separated by harsh `HorizontalDivider`s. It lacks a hero header, and the list items bleed to the absolute edges of the screen. Furthermore, the Mini Player floats awkwardly over the bottom items.
**The Fix:**
1.  **The Hero Header (Sliver App Bar):** Add a large header at the top of the playlist containing the Playlist Artwork (blurred background with sharp foreground cover), Playlist Title, and a prominent FAB (Floating Action Button) for Play/Shuffle. As the user scrolls down, this header should collapse/parallax.
2.  **The M3 List Makeover:** Remove every `HorizontalDivider`. Wrap each track row in a `Surface(shape = RoundedCornerShape(12.dp), color = Color.Transparent)` so that hover states and ripples are constrained to a floating pill shape rather than a bleeding rectangle. Add horizontal padding to the `LazyColumn` so the pills don't touch the window edges.
3.  **Mini Player Insets:** Ensure the `LazyColumn` has a `contentPadding = PaddingValues(bottom = 120.dp)` (or whatever height the Mini Player is) so the last track isn't permanently obscured by the floating player.

### The "Hard Square" Clipping Bug (Modifier Ordering)
**The Issue:** In the Mini Player and bottom corner album arts, hovering or clicking produces a harsh, right-angled square highlight that bleeds out of the rounded corners of the image.
**The Fix:** This is caused by an incorrect Compose Modifier execution order. In Compose, modifiers are applied sequentially. If you apply `.clickable { }` or an interaction indication *before* applying `.clip(RoundedCornerShape(...))`, the ripple and highlight will draw to the full unclipped bounding box.
*   **Wrong:** `Modifier.clickable { }.clip(RoundedCornerShape(12.dp))`
*   **Right:** `Modifier.clip(RoundedCornerShape(12.dp)).clickable { }`
Alternatively, the safest M3 way to fix this is to wrap the `AsyncImage` inside a `Surface(shape = RoundedCornerShape(12.dp), onClick = { ... })`, which perfectly bounds all ripples and hover highlights to the rounded shape automatically.

### Settings: Live Component Previews (Instead of Fake Mockups)
**The Issue:** In the Settings screen, the previews for "Mini-player background", "Player background", and "Slider style" look like static, fake graphical representations rather than authentic parts of the app. 
**The Fix:** Modern Material 3 configuration screens (like Android's lock screen customization) do not use fake image mockups. They render the **actual UI components** in real-time.

To achieve this in Compose:
1.  **Use the Real Composables:** Instead of drawing rectangles that "look like" a mini player or a slider, instantiate the *actual* `MiniPlayerBar` or `Slider` composable directly inside the settings row's trailing edge.
2.  **Scale Them Down (The "Live Widget" Trick):** If the real component is too big for the settings row, wrap it in a `Box` and apply `Modifier.graphicsLayer { scaleX = 0.5f; scaleY = 0.5f }` to shrink it perfectly without breaking its internal layout logic.
3.  **Wire Real-Time State:** Pass the *previewed* state into these components. For example, the row for "Wavy Slider" should render an actual `Slider` with the wavy modifier applied. When the user clicks the row, your underlying `Preference` state updates. Because both your settings preview and the actual app player are reading from the same state flow, they will both instantly crossfade into the new style simultaneously with proper Compose animations.
4.  **Isolate Interaction:** Since these are just previews, you usually want them to be read-only. You can wrap the real component in a `Box` with `Modifier.pointerInput(Unit) { awaitPointerEventScope { while(true) { awaitPointerEvent(); /* consume events so they can't actually interact with the preview slider */ } } }`.

### Player Variants: Material 3 Redesign Concepts
The settings menu mentions toggles for "Player V2", "Apple mini player", and "New player design", but the app currently lacks distinct M3 architecture for these. Here is how they should be redesigned and implemented as live toggles:

#### 1. The Miniplayer Redesigns
Instead of just one style, the `MiniPlayerBar` should accept a `MiniPlayerStyle` enum that fundamentally changes its layout:
*   **V1: Full-Width (Current):** Docks to the bottom edge stretching side-to-side.
*   **V2: The Floating M3 Pill (Requested):** Instead of docking to the edge, the miniplayer is a floating `Surface` with `RoundedCornerShape(50)` (a perfect pill). It sits ~24dp above the bottom of the screen with a heavy drop shadow. Its background color is heavily tinted by the current album's vibrant dynamic color. The play/pause buttons are housed inside, and the progress bar can be a thin `.height(2.dp)` line that traces the bottom edge of the pill, or a circular progress ring around the Play button.
*   **V3: The Compact Edge:** A smaller, non-pill box that floats in the bottom right corner (like the 3rd image), with a thick, flush-edge progress bar at the very bottom.

#### 2. The Full Player Redesigns
The `NowPlayingScreen` should also have distinct M3 architectures:
*   **V1 (Standard):** The 2-pane split view discussed earlier (Lyrics left, Stack right).
*   **V2 (M3 Expressive / Player V2):** The album art isn't just an image, it's a massive, floating hero card with an extreme corner radius (e.g., `48.dp`) centered on the screen. The playback controls are not floating loose on the background; instead, they are housed inside a translucent, blurred "Glassmorphism" pill that floats beneath the artwork. The background is a full-bleed, slowly breathing dynamic color mesh extracted from the artwork.

#### 3. Wiring the Variants to the Settings Previews
To show these new designs in the settings menu:
1.  Pass the `style` parameter into your live preview component. 
2.  When the user selects "Floating Pill" in settings, the live mockup component in the settings row is instantiated as `MiniPlayerBar(style = MiniPlayerStyle.FLOATING_PILL)`.
3.  Because it's a live component, the user will immediately see the full-width bar morph into a hovering pill in real-time right inside the settings menu. You can even wrap the transition in `AnimatedContent` so it crossfades smoothly between the different architectural shapes.
