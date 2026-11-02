package com.music.vivi.compose.whatsnew

import androidx.compose.ui.graphics.Color

/** Where the walkthrough videos live. Blank skips the CDN step and goes straight to YouTube. */
object WhatsNewConfig {
    const val CDN_BASE = "https://vndreiii.com/programs/visage/media"

    /** `<CDN_BASE>/whatsnew/<version>/<slug>.mp4` */
    fun cdnUrl(version: String, slug: String): String? =
        CDN_BASE.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let { "$it/whatsnew/$version/$slug.mp4" }
}

/** One feature page in the carousel. [slug] names the video file; [youtubeId] is the fallback upload. */
class WhatsNewFeature(
    val slug: String,
    val title: String,
    val summary: String,
    /** How to use it, shown under the summary; the same text is the "no video" fallback. */
    val howTo: String,
    val youtubeId: String? = null,
)

enum class ChangeKind(val label: String, val color: Color) {
    FEATURE("FEATURE", Color(0xFF3DDC84)),
    IMPROVED("IMPROVED", Color(0xFFB794F6)),
    FIX("FIX", Color(0xFF4DA3FF)),
    HOTFIX("HOTFIX", Color(0xFFFF5C5C)),
    PATCH("PATCH", Color(0xFFFFB84D)),
}

class ChangeEntry(val kind: ChangeKind, val text: String)

class WhatsNewRelease(
    val version: String,
    val features: List<WhatsNewFeature>,
    val changes: List<ChangeEntry>,
)

object WhatsNewCatalog {
    val releases: List<WhatsNewRelease> = listOf(
        WhatsNewRelease(
            version = "1.3.0",
            features = listOf(
                WhatsNewFeature(
                    slug = "lyrics-3d-corridor",
                    title = "3D lyric corridor",
                    summary = "Lyrics now fly toward you through a depth-blurred corridor. The line being sung is sharp, the rest fall away.",
                    howTo = "Open the lyrics panel, then Settings > Lyrics > Scene and pick \"3D row\". Close-up fade distance is adjustable there.",
                    youtubeId = "XGrBMOOgrOA",
                ),
                WhatsNewFeature(
                    slug = "lyrics-cuts",
                    title = "Cuts scene",
                    summary = "A vinyl-groove poster look: concentric rings slice through the lyric and ripple outward with the music.",
                    howTo = "Settings > Lyrics > Scene > \"Cuts\". The rings react to singing, tempo and every lyric change.",
                    youtubeId = "Wj2C7aD7zTo",
                ),
                WhatsNewFeature(
                    slug = "carry-tracks",
                    title = "Carry tracks anywhere",
                    summary = "Drag a track out of the queue, the now-playing card or a library list and drop it onto a playlist or folder.",
                    howTo = "Hold a row and pull it sideways. Targets that would refuse the track (a local file onto a YouTube playlist) glow red and a toast says why.",
                    youtubeId = "8M89koPkj7w",
                ),
                WhatsNewFeature(
                    slug = "tag-manager",
                    title = "Tag Manager upgrades",
                    summary = "Batch-fix your local library tags with auto-tagging, a review screen where you can type your own values, and a right-click menu.",
                    howTo = "Library > Tag Manager. Drag to select rows, right-click for Play, Add to queue or Open folder. Work continues in the background while you browse.",
                    youtubeId = "iiTa1FdeBGc",
                ),
                WhatsNewFeature(
                    slug = "local-library",
                    title = "Local library, your way",
                    summary = "Pick several music folders, see each one's scan status, and filter the current tab with the new search bar.",
                    howTo = "Settings > Local & offline > Add folder. In Library, type in the search bar to filter the tab you are on.",
                    youtubeId = "x9T52JRWG24",
                ),
                WhatsNewFeature(
                    slug = "responsive-layouts",
                    title = "Fits any window",
                    summary = "VIsage now adapts from a phone-sized window to an ultrawide monitor. On small windows the tabs fold into a menu tray, the queue and lyrics open as trays and Now Playing stacks into one column.",
                    howTo = "Resize the window. Under 600 px wide tap the menu button in the top bar for Home, Search, Library and Settings. On big screens pages stay centred at a readable width.",
                    youtubeId = "dM2CNzR_HOM",
                ),
                WhatsNewFeature(
                    slug = "settings-redesign",
                    title = "Settings redesign",
                    summary = "Every settings page was rebuilt. Long rows of chips became dropdowns, options that depend on another switch only show up when it is on, and pages like Interface, Lyrics, Fullscreen player and Sidebars show a live preview of what you are changing as you change it.",
                    howTo = "Open Settings and pick a page. Change a setting and watch the preview on the same page update at once. Use the search box at the top of Settings to jump straight to any option.",
                    youtubeId = "SSrzzMXmT_Y",
                ),
                WhatsNewFeature(
                    slug = "popout-player",
                    title = "Pop-out mini player",
                    summary = "Pull Now Playing into its own small window. Make it wide enough and the lyrics show up next to the cover.",
                    howTo = "Press Ctrl+Shift+M or use the pop-out button in the Now Playing tool row (rebindable in Settings > Keyboard shortcuts). It can stay on top; on Hyprland and other Wayland compositors pin it with a window rule, see docs/popup-player.md.",
                    youtubeId = "RqUxEAXzYR0",
                ),
            ),
            changes = listOf(
                ChangeEntry(ChangeKind.FEATURE, "3D row and Cuts lyric scenes, selectable in Lyrics settings."),
                ChangeEntry(ChangeKind.FEATURE, "Springy lyric changes: each new line overshoots and settles like a real spring. Tune the overshoot amplitude, bounce frequency and damping in Settings > Lyrics > Motion (the same controls are in the lyrics panel), or turn it down to nothing."),
                ChangeEntry(ChangeKind.FEATURE, "Drag tracks from the queue or the now-playing card onto playlists and folders."),
                ChangeEntry(ChangeKind.FEATURE, "Tag Manager: right-click menu, drag-select, background work with a floating progress button."),
                ChangeEntry(ChangeKind.FEATURE, "Library search bar; multi-folder local library with per-folder status."),
                ChangeEntry(ChangeKind.FEATURE, "Responsive layouts for phone, tablet, 1080p, 2K and ultrawide windows: menu tray in the top bar, queue and lyrics trays, stacked Now Playing, library as an overlay."),
                ChangeEntry(ChangeKind.FEATURE, "Pop-out mini player window (Ctrl+Shift+M) with lyrics when it is wide enough."),
                ChangeEntry(ChangeKind.IMPROVED, "Text that does not fit scrolls (marquee) instead of being cut off, app-wide."),
                ChangeEntry(ChangeKind.IMPROVED, "Smarter local .lrc lookup: case, track numbers, \"Artist - Title\", Lyrics folder, encodings."),
                ChangeEntry(ChangeKind.IMPROVED, "Lyrics delay: the + and - buttons in the player have no limit now (the Settings slider stays at +-1.5 s); hold to repeat and accelerate."),
                ChangeEntry(ChangeKind.IMPROVED, "Home: shelf headers show the avatar with a 12 dp gap to the title, Quick picks are multi-row cards, and panels have equal padding on all sides."),
                ChangeEntry(ChangeKind.IMPROVED, "Tag Manager: the search field and grouping stack under a narrow window width, with a one-line search placeholder."),
                ChangeEntry(ChangeKind.IMPROVED, "Pop-out player: pin button on the left, close on the right, even padding; lyrics keep your size and only shrink when the longest line would not fit."),
                ChangeEntry(ChangeKind.IMPROVED, "Volume shortcuts: Up/Down change the volume by 5%, Shift+Up/Down by 15%, with an on-screen readout."),
                ChangeEntry(ChangeKind.IMPROVED, "Music video: read-ahead, chunk retries and quality step-down; seeks no longer count as stalls."),
                ChangeEntry(ChangeKind.IMPROVED, "Right-click menu on all YouTube items; songs dropped on the queue land where you release them."),
                ChangeEntry(ChangeKind.IMPROVED, "Fullscreen player: lyrics open by default beside a big square cover; mouse wheel over the cover changes the volume."),
                ChangeEntry(ChangeKind.IMPROVED, "Settings pages rebuilt with live previews, dropdowns and a search."),
                ChangeEntry(ChangeKind.IMPROVED, "Music video: the official video is found by search when YouTube Music does not link it."),
                ChangeEntry(ChangeKind.IMPROVED, "Cover and image pickers use the system file chooser portal."),
                ChangeEntry(ChangeKind.IMPROVED, "Clipboard uses wl-copy on Wayland, then xclip or xsel."),
                ChangeEntry(ChangeKind.FIX, "LRCLIB no longer picks a namesake song just because its duration is closest."),
                ChangeEntry(ChangeKind.FIX, "Queue drag grabbed the wrong track after playback extended the queue."),
                ChangeEntry(ChangeKind.FIX, "Page background: no more seams, tiles or black when scrolling far."),
                ChangeEntry(ChangeKind.FIX, "A local or offline song queued by id is resolved to its file (fixes the 400 from \"Jump back in\")."),
                ChangeEntry(ChangeKind.FIX, "Moving the window by its title bar on Linux no longer bounces: the window manager does the move."),
                ChangeEntry(ChangeKind.HOTFIX, "Playback tries every remaining stream source before failing, with one clear message."),
                ChangeEntry(ChangeKind.HOTFIX, "Windows keyring: large secrets are stored in pieces (error 1783)."),
                ChangeEntry(ChangeKind.PATCH, "Windows updater stops leftover processes, logs msiexec and retries a failed install."),
                ChangeEntry(ChangeKind.PATCH, "Settings saves merge with the file so a second running copy cannot wipe them."),
                ChangeEntry(ChangeKind.PATCH, "Privacy: library debug dumps removed, home directory hidden in logs and reports."),
            ),
        ),
    )
}
