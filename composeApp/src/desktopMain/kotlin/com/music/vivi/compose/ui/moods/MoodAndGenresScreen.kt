package com.music.vivi.compose.ui.moods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.music.innertube.YouTube
import com.music.innertube.pages.MoodAndGenres
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.ui.component.DetailPageHeader
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.component.NavigationTitle
import com.music.vivi.compose.ui.component.PageErrorState
import com.music.vivi.compose.ui.component.PageLoadingState
import com.music.vivi.compose.ui.component.interactiveScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MoodAndGenresScreen(
    navController: NavHostController,
) {
    val scope = rememberCoroutineScope()
    var moods by remember { mutableStateOf<List<MoodAndGenres>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                moods = withContext(Dispatchers.IO) { YouTube.moodAndGenres().getOrThrow() }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load moods"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        DetailPageHeader(title = "Moods & genres", onBack = { navController.navigateUp() })
        val loaded = moods
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        when {
            loading && loaded == null -> PageLoadingState("Loading moods", detail = true)
            error != null && loaded == null -> PageErrorState(error.orEmpty()) { load() }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 190.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().smoothWheelScroll(gridState),
            ) {
                loaded.orEmpty().forEachIndexed { sectionIndex, section ->
                    item(key = "title_$sectionIndex", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        NavigationTitle(title = section.title, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(section.items, key = { "${sectionIndex}_${it.endpoint.browseId}_${it.endpoint.params}" }, contentType = { "mood" }) { item ->
                        MoodTile(
                            title = item.title,
                            stripe = Color(item.stripeColor.toInt()),
                            onClick = { navController.navigate(Routes.forBrowse(item.endpoint.browseId, item.endpoint.params)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodTile(title: String, stripe: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(56.dp).interactiveScale(hoveredScale = 1.015f),
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            Box(Modifier.width(6.dp).fillMaxHeight().padding(vertical = 10.dp).align(Alignment.CenterStart)) {
                Surface(color = stripe, shape = RoundedCornerShape(3.dp), modifier = Modifier.fillMaxSize()) {}
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 12.dp),
            )
        }
    }
}
