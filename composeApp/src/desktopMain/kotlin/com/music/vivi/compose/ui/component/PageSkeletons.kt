package com.music.vivi.compose.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.responsive.windowLayout

/**
 * Loading wireframes. Each [PageSkeleton] kind mirrors one page layout with the same insets, sizes and gaps as the real
 * page (the numbers are the real composables': [PageInset], [PageHeaderTopPadding], `MediaPageHeader`, `YouTubeListItem`
 * 64 dp rows with a 48 dp cover, `YouTubeGridItem` cells), so a page does not jump when its content arrives.
 * Shapes are named after what they stand for; `docs/penpot-loading-skeletons.md` lists the same set for the Penpot file.
 */
enum class PageSkeleton {
    /** HomeScreen: greeting, mood chips, Quick picks card, shelves. */
    Home,

    /** Search results and tag/library lists: list of 64 dp rows (header optional). */
    List,

    /** NewReleases, ArtistItems, Browse: header then an adaptive grid of cover cards. */
    Grid,

    /** OnlineAlbum / OnlinePlaylist: back bar, glass header card with a square cover, track rows. */
    MediaSquare,

    /** Artist: same card with a round 140 dp portrait. */
    MediaRound,

    /** Charts: back bar, podium of three cards, ranked rows. */
    Charts,
}

/** Reserve the page's structure while loading, with one gentle shared pulse. */
@Composable
fun PageLoadingState(
    title: String = "Loading music",
    kind: PageSkeleton = PageSkeleton.List,
    /** False when the page draws its own header above the loading body (Browse, Moods, Tag Manager, Library). */
    header: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "pageLoading")
    val alpha by transition.animateFloat(
        0.35f, 0.75f,
        infiniteRepeatable(com.music.vivi.compose.ui.motion.VisageMotion.pulse(), RepeatMode.Reverse),
        label = "placeholderPulse",
    )
    val color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Box(Modifier.fillMaxSize().semantics { contentDescription = title }) {
        when (kind) {
            PageSkeleton.Home -> HomeSkeleton(color)
            PageSkeleton.List -> Column(Modifier.fillMaxSize()) {
                if (header) SkeletonDetailHeader(color)
                SkeletonRows(color, count = 10, horizontal = PageInset)
            }
            PageSkeleton.Grid -> Column(Modifier.fillMaxSize()) {
                if (header) SkeletonDetailHeader(color)
                SkeletonGrid(color)
            }
            PageSkeleton.MediaSquare -> MediaSkeleton(color, round = false)
            PageSkeleton.MediaRound -> MediaSkeleton(color, round = true)
            PageSkeleton.Charts -> ChartsSkeleton(color, title)
        }
    }
}

@Composable
private fun Bar(color: Color, modifier: Modifier, height: Dp, radius: Dp = height / 2) {
    Box(modifier.height(height).background(color, RoundedCornerShape(radius)))
}

@Composable
private fun Block(color: Color, modifier: Modifier, shape: Shape) {
    Box(modifier.background(color, shape))
}

/** `VisageBackButton` (44 dp circle) at the page-header position. */
@Composable
private fun SkeletonBackBar(color: Color) {
    Box(Modifier.padding(start = PageInset, top = PageHeaderTopPadding, bottom = PageHeaderBottomPadding)) {
        Block(color, Modifier.size(44.dp), CircleShape)
    }
}

/** `DetailPageHeader`: back circle, 14 dp gap, headlineSmall title. */
@Composable
private fun SkeletonDetailHeader(color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(start = PageInset, end = PageInset, top = PageHeaderTopPadding, bottom = PageHeaderBottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Block(color, Modifier.size(44.dp), CircleShape)
        Spacer(Modifier.width(14.dp))
        Bar(color, Modifier.width(180.dp), 28.dp)
    }
}

/** `YouTubeListItem`: fixed 64 dp row, 48 dp cover, title + subtitle. */
@Composable
private fun SkeletonRow(color: Color, titleFraction: Float = .55f) {
    Row(
        Modifier.fillMaxWidth().height(ListItemHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Block(color, Modifier.size(ListThumbnailSize), RoundedCornerShape(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Bar(color, Modifier.fillMaxWidth(titleFraction), 16.dp)
            Bar(color, Modifier.fillMaxWidth(titleFraction * .55f), 12.dp)
        }
    }
}

@Composable
private fun SkeletonRows(color: Color, count: Int, horizontal: Dp, vertical: Dp = 0.dp) {
    Column(Modifier.fillMaxWidth().padding(horizontal = horizontal), verticalArrangement = Arrangement.spacedBy(vertical)) {
        repeat(count) { SkeletonRow(color, if (it % 3 == 1) .45f else .6f) }
    }
}

/** `YouTubeGridItem` cell: 6/4 dp inset, square cover, two text lines. */
@Composable
private fun SkeletonGridCard(color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        Block(color, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(12.dp))
        Spacer(Modifier.height(6.dp))
        Bar(color, Modifier.fillMaxWidth(.7f), 16.dp)
        Spacer(Modifier.height(4.dp))
        Bar(color, Modifier.fillMaxWidth(.45f), 12.dp)
    }
}

/** Adaptive grid as in NewReleases / ArtistItems: min cell 180 dp (150 compact), 10 dp gaps, PageInset sides. */
@Composable
private fun SkeletonGrid(color: Color, rows: Int = 3) {
    val minCell = if (windowLayout().isCompactWidth) 150.dp else 180.dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = PageInset)) {
        val gap = 10.dp
        val columns = ((maxWidth + gap) / (minCell + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(columns) { SkeletonGridCard(color, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** `MediaPageHeader` glass card then the track list, laid out with the same formulas as the real header. */
@Composable
private fun MediaSkeleton(color: Color, round: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
        SkeletonBackBar(color)
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f), RoundedCornerShape(28.dp)),
        ) {
            val cardWidth = maxWidth
            val size = if (round) 140.dp else (cardWidth * 0.6f).coerceIn(180.dp, 340.dp)
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(maxOf(minOf(cardWidth / 3f, 420.dp), size))) {
                    // The real banner fades to transparent at its bottom edge, so this does too.
                    Box(
                        Modifier.fillMaxSize().background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color, color, Color.Transparent)),
                            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        ),
                    )
                    Block(
                        color,
                        Modifier.align(Alignment.BottomCenter).offset(y = size / 2).size(size),
                        if (round) CircleShape else RoundedCornerShape(24.dp),
                    )
                }
                Spacer(Modifier.height(size / 2 + 12.dp))
                Bar(color, Modifier.width(220.dp), 36.dp)
                Bar(color, Modifier.padding(top = 6.dp).width(140.dp), 20.dp)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bar(color, Modifier.width(72.dp), 32.dp)
                    Bar(color, Modifier.width(88.dp), 32.dp)
                    Bar(color, Modifier.width(64.dp), 32.dp)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Block(color, Modifier.height(48.dp).weight(1.5f), RoundedCornerShape(24.dp))
                    Block(color, Modifier.size(48.dp), CircleShape)
                    if (!round) Block(color, Modifier.size(48.dp), CircleShape)
                }
            }
        }
        SkeletonRows(color, count = 8, horizontal = 12.dp, vertical = 4.dp)
    }
}

/** Charts: back bar, podium (weights 1.15 / 1 / 1, 14 dp gaps, bottom aligned), ranked rows. */
@Composable
private fun ChartsSkeleton(color: Color, status: String) {
    Column(Modifier.fillMaxSize()) {
        // The world chart takes a while to build; its country progress is the one loading text worth showing.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBackBar(color)
            Spacer(Modifier.width(14.dp))
            androidx.compose.material3.Text(
                status, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = PageHeaderTopPadding, bottom = PageHeaderBottomPadding),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Panes under 600 dp have no podium: every entry is a compact row, with 12 dp side padding.
            val narrow = maxWidth < 600.dp
            Column {
                if (!narrow) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        listOf(1.15f, 1f, 1f).forEach { weight ->
                            Block(color, Modifier.weight(weight).height(if (weight > 1f) 260.dp else 232.dp), RoundedCornerShape(24.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                SkeletonRows(color, count = 8, horizontal = if (narrow) 12.dp else 24.dp)
            }
        }
    }
}

/** HomeScreen: greeting row, mood chips, Quick picks card (rows 48 dp at a 58.5 dp pitch), then a shelf of covers. */
@Composable
private fun HomeSkeleton(color: Color) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Block(color, Modifier.size(48.dp), CircleShape)
            Bar(color, Modifier.width(220.dp), 28.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(76, 96, 84, 110, 90, 80).forEach { Bar(color, Modifier.width(it.dp), 40.dp) }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f), RoundedCornerShape(24.dp)).padding(20.dp),
        ) {
            val columns = if (maxWidth < 600.dp) 1 else if (maxWidth < 1000.dp) 2 else 3
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(columns) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.5.dp)) {
                        repeat(4) {
                            Row(Modifier.height(48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Block(color, Modifier.size(48.dp), RoundedCornerShape(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Bar(color, Modifier.fillMaxWidth(.6f), 14.dp)
                                    Bar(color, Modifier.fillMaxWidth(.35f), 10.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { Block(color, Modifier.size(GridThumbnailHeight + 40.dp), RoundedCornerShape(16.dp)) }
        }
    }
}
