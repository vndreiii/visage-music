package com.music.vivi.compose.whatsnew

import androidx.compose.ui.graphics.Color

/** Where the walkthrough videos live. Blank skips the CDN step and goes straight to YouTube. */
object WhatsNewConfig {
    const val CDN_BASE = ""

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
                ),
                WhatsNewFeature(
                    slug = "lyrics-cuts",
                    title = "Cuts scene",
                    summary = "A vinyl-groove poster look: concentric rings slice through the lyric and ripple outward with the music.",
                    howTo = "Settings > Lyrics > Scene > \"Cuts\". The rings react to singing, tempo and every lyric change.",
                ),
                WhatsNewFeature(
                    slug = "lyrics-bounce",
                    title = "Springy lyric changes",
                    summary = "Each new line overshoots and settles like a real spring. Tune it to taste, or turn it down to nothing.",
                    howTo = "Settings > Lyrics > Motion: overshoot amplitude, bounce frequency and damping. The same sliders are in the lyrics panel.",
                ),
                WhatsNewFeature(
                    slug = "carry-tracks",
                    title = "Carry tracks anywhere",
                    summary = "Drag a track out of the queue, the now-playing card or a library list and drop it onto a playlist or folder.",
                    howTo = "Hold a row and pull it sideways. Targets that would refuse the track (a local file onto a YouTube playlist) glow red and a toast says why.",
                ),
                WhatsNewFeature(
                    slug = "tag-manager",
                    title = "Tag Manager upgrades",
                    summary = "Batch-fix your local library tags with auto-tagging, a review screen where you can type your own values, and a right-click menu.",
                    howTo = "Library > Tag Manager. Drag to select rows, right-click for Play, Add to queue or Open folder. Work continues in the background while you browse.",
                ),
                WhatsNewFeature(
                    slug = "local-library",
                    title = "Local library, your way",
                    summary = "Pick several music folders, see each one's scan status, and filter the current tab with the new search bar.",
                    howTo = "Settings > Local & offline > Add folder. In Library, type in the search bar to filter the tab you are on.",
                ),
            ),
            changes = listOf(
                ChangeEntry(ChangeKind.FEATURE, "3D row and Cuts lyric scenes, selectable in Lyrics settings."),
                ChangeEntry(ChangeKind.FEATURE, "Drag tracks from the queue or the now-playing card onto playlists and folders."),
                ChangeEntry(ChangeKind.FEATURE, "Tag Manager: right-click menu, drag-select, background work with a floating progress button."),
                ChangeEntry(ChangeKind.FEATURE, "Library search bar; multi-folder local library with per-folder status."),
                ChangeEntry(ChangeKind.IMPROVED, "Text that does not fit scrolls (marquee) instead of being cut off, app-wide."),
                ChangeEntry(ChangeKind.IMPROVED, "Smarter local .lrc lookup: case, track numbers, \"Artist - Title\", Lyrics folder, encodings."),
                ChangeEntry(ChangeKind.IMPROVED, "Lyrics delay range is now +-30 s; hold + or - to repeat and accelerate."),
                ChangeEntry(ChangeKind.IMPROVED, "Cover and image pickers use the system file chooser portal."),
                ChangeEntry(ChangeKind.IMPROVED, "Clipboard uses wl-copy on Wayland, then xclip or xsel."),
                ChangeEntry(ChangeKind.FIX, "LRCLIB no longer picks a namesake song just because its duration is closest."),
                ChangeEntry(ChangeKind.FIX, "Queue drag grabbed the wrong track after playback extended the queue."),
                ChangeEntry(ChangeKind.FIX, "Page background: no more seams, tiles or black when scrolling far."),
                ChangeEntry(ChangeKind.FIX, "A local or offline song queued by id is resolved to its file (fixes the 400 from \"Jump back in\")."),
                ChangeEntry(ChangeKind.HOTFIX, "Playback tries every remaining stream source before failing, with one clear message."),
                ChangeEntry(ChangeKind.HOTFIX, "Windows keyring: large secrets are stored in pieces (error 1783)."),
                ChangeEntry(ChangeKind.PATCH, "Windows updater stops leftover processes, logs msiexec and retries a failed install."),
                ChangeEntry(ChangeKind.PATCH, "Settings saves merge with the file so a second running copy cannot wipe them."),
                ChangeEntry(ChangeKind.PATCH, "Privacy: library debug dumps removed, home directory hidden in logs and reports."),
            ),
        ),
    )
}
