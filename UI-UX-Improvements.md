# UI & UX Analysis and Implementation Guide

## 1. Home Screen: Horizontal Scrolling
The home screen currently displays horizontal lists (e.g., playlists, albums), but the scrolling interactions can be significantly improved to feel more natural and fluid on a desktop environment.

### UX Recommendations
*   **Smooth Scrolling:** The scrolling should have inertia and smooth deceleration.
*   **Multiple Input Methods:** Ensure users can scroll horizontally using:
    *   Horizontal scroll wheel (if available on the mouse).
    *   Touchpad two-finger swipe (left/right).
    *   Click-and-drag panning (grabbing the row and pulling it).

### Compose Desktop Implementation
In Jetpack Compose Desktop, a `LazyRow` handles basic mouse wheel scrolling, but horizontal trackpad swiping and click-and-drag require explicit support.

1.  **Touchpad Swiping & Scroll Wheel:**
    Compose Desktop generally handles trackpad horizontal scrolling for `LazyRow` automatically in newer versions. If it feels rigid, ensure you aren't intercepting scroll events manually. 
2.  **Click and Drag (Mouse Panning):**
    To support grabbing and dragging the list (like on touch devices, but with a mouse), you need a custom `pointerInput` modifier.

```kotlin
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun Modifier.dragToScroll(
    state: LazyListState,
    coroutineScope: CoroutineScope
): Modifier = this.pointerInput(Unit) {
    detectDragGestures { change, dragAmount ->
        change.consume()
        coroutineScope.launch {
            // Dragging left (negative dragAmount.x) means scrolling right (positive scroll)
            state.scrollBy(-dragAmount.x)
        }
    }
}

// Usage:
// val state = rememberLazyListState()
// val scope = rememberCoroutineScope()
// LazyRow(state = state, modifier = Modifier.dragToScroll(state, scope)) { ... }
```

---

## 2. Library Page: UX-UI Betterment
The current Library page features a large "Liked" banner and a grid of square buttons ("Offline", "Albums", "Artists", "Playlists", "Recent"). Instead of just being a simple folder menu, the Library should act as a **"Second Home" page**, specifically tailored to the user's local collection.

### UX Recommendations
*   **The "Second Home" Concept:** Keep the "Offline" functionality distinct, but the main body of the Library page should be populated with local data. Treat it like a dynamic feed similar to the main Home page, but using only local files.
*   **Dynamic Local Rows:** Fill the massive empty space below with horizontally scrolling rows (using the improved smooth scrolling mentioned above) generated entirely from the local music database:
    *   *Recently Added:* Show the newest local albums/songs scanned into the library.
    *   *Heavy Rotation (Quick Picks):* Local tracks the user plays the most.
    *   *Rediscover:* Older local albums they haven't listened to in a while.
    *   *Local Playlists & Mixes:* Auto-generated mixes based on local artist data.
*   **Redesign Navigation:** Transform the current bulky square buttons into a sleek row of chips or tabs at the top (e.g., "Playlists", "Albums", "Artists", "Offline"). When no specific tab is selected, show this rich "Second Home" feed.
*   **Visual Hierarchy:** The "Liked Songs" banner could be integrated as a prominent, beautifully styled tile in the "Second Home" feed rather than a disjointed full-width bar, making the page feel more cohesive.

---

## 3. Local & Offline Library Support
It is important to draw a hard distinction between **"Offline"** and **"Local"**:
*   **Offline:** This section is strictly a download page for content *downloaded via the app itself*. It should not randomly pull in files from the user's OS "Downloads" folder. When the app downloads a track, it should register it in an internal `DownloadedTracks` database table, explicitly saving the high-quality cover art, artist metadata, and audio file into an app-specific managed directory. The "Offline" page queries this specific database table.
*   **Local:** This refers to the user's existing personal music collection (folders they explicitly tell the app to scan).

### Implementation Guide

#### A. Scanning & Indexing
To support local albums, songs, and artists, you need to build a local media scanner.
1.  **Tag Extraction:** Use a library like [Jaudiotagger](http://www.jthink.net/jaudiotagger/) (Java/Kotlin compatible) to parse metadata from local files.
2.  **Database:** Use SQLDelight or Room to store the scanned metadata locally.
    *   `Artists` table (id, name)
    *   `Albums` table (id, title, artist_id, release_year, cover_path)
    *   `Tracks` table (id, title, album_id, artist_id, duration, file_path, track_number)
3.  **The Scanner Flow:**
    *   Run a Kotlin Coroutine (on `Dispatchers.IO`).
    *   Walk the user-defined directories (`File.walkTopDown()`).
    *   Filter by audio extensions (e.g., `.mp3`, `.flac`, `.wav`, `.m4a`, `.ogg`).
    *   Extract ID3/Vorbis tags for each file.
    *   Batch insert into the local database to ensure UI performance.

#### B. Cover Art Reading
*   **Embedded Art:** Jaudiotagger can extract the byte array of embedded album art. Save this byte array to a temporary local cache folder as an image file (e.g., `cache/covers/{album_id}.jpg`), or serve it directly to the UI using Compose's `ImageBitmap`.
*   **Folder Art:** If embedded art is missing, the scanner should look for `cover.jpg`, `folder.jpg`, or `front.jpg` in the same directory as the audio file.

#### C. Lyrics Support (.lrc and Embedded)
*   **Embedded Lyrics:** Read the `USLT` (Unsynchronized lyrics) or `SYLT` (Synchronized lyrics) frames from ID3 tags.
*   **Local .lrc Files:** When playing a file (e.g., `/Music/Artist/Album/Song.flac`), check if a file named `/Music/Artist/Album/Song.lrc` exists. Read it, parse the timestamps, and feed it into the app's lyrics UI.

#### D. Lossless + Lossy Support
Ensure the audio engine (e.g., if using a VLC wrapper, GStreamer, or ExoPlayer ported to desktop) supports both:
*   **Lossy:** MP3, AAC (.m4a), OGG, Opus.
*   **Lossless:** FLAC, WAV, ALAC.
When scanning, read the bit depth, sample rate, and bitrate to optionally display a "Hi-Res" or "Lossless" badge in the UI.

---

## 4. Settings: "Local / Offline" Category
Add a new dedicated section in the app's settings to manage local files.

### Settings Options to Implement
*   **Local Music Folders:** A list of directories the app should monitor. Provide an "Add Folder" button that opens a native desktop directory picker (using `JFileChooser` or LWJGL's native dialogs).
*   **Scan Now Button:** A manual trigger to re-scan the directories. Show a progress bar indicating how many files have been processed.
*   **Auto-scan on Startup:** A toggle (`Checkbox` or `Switch`) to quietly look for new files in the background when the app launches.
*   **Prefer Local Metadata:** Toggle to choose whether local ID3 tags override metadata fetched from the internet (if your app matches local files with online services like Last.fm).
*   **Clear Local Cache:** A button to wipe the local database and cached covers, useful for debugging or fresh starts.

---

## 5. Playback Transitions: Crossfade & Fade Play/Pause
The current audio transition logic suffers from implementation flaws that break the expected user experience both audibly and visually.

### A. True Crossfade vs. Sequential Fading
*   **The Issue:** The current "crossfade" implementation acts as a sequential fade. It fades the volume of Track A down to zero, loads Track B, and then fades Track B's volume up.
*   **The Fix:** A true crossfade requires **concurrent playback**. Track A and Track B must overlap and play simultaneously for the specified crossfade duration. As Track A's volume ramps down over $X$ seconds at the end of the track, Track B starts playing and its volume ramps up over those same $X$ seconds. This requires the audio engine to maintain two separate player instances (or an audio mixer) so they can overlap.

### B. Fade to Play/Pause UI Sync
*   **The Issue:** When a user clicks the Play/Pause button, the visual icon doesn't update until the audio volume fade finishes. This creates immediate visual latency, making the app feel unresponsive, laggy, or broken.
*   **The Fix:** The UI state must be completely decoupled from the audio engine's fade completion. The Play/Pause icon (and the play state in the UI) must toggle **instantly** upon the user's click. The audio engine handles the graceful 500ms volume ramp in the background, but the user immediately sees that their click registered.
