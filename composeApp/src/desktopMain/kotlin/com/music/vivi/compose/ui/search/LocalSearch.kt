package com.music.vivi.compose.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.database.Local_track
import com.music.vivi.database.NativeHelpers
import com.music.vivi.database.NativeSearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Search over the user's indexed local files: the Rust fuzzy matcher when available (typo tolerant),
 * otherwise the SQLite FTS5 index (prefix match, bm25 ranked).
 */
@Composable
fun rememberLocalHits(repo: LocalMusicRepository, query: String, limit: Int = 6): List<Local_track> {
    var tracks by remember { mutableStateOf<Map<String, Local_track>>(emptyMap()) }
    var index by remember { mutableStateOf<NativeSearchIndex?>(null) }
    var hits by remember { mutableStateOf<List<Local_track>>(emptyList()) }

    LaunchedEffect(repo) {
        val all = withContext(Dispatchers.IO) { repo.allTracks() }
        tracks = all.associateBy { it.id }
        index = withContext(Dispatchers.IO) {
            NativeHelpers.searchIndex(all.map { it.id to "${it.title} ${it.artist_name} ${it.album_title}" })
        }
    }
    DisposableEffect(index) { onDispose { index?.close() } }

    LaunchedEffect(query, tracks, index) {
        val q = query.trim()
        hits = if (q.isEmpty() || tracks.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            val idx = index
            if (idx != null) {
                idx.query(q, limit).mapNotNull(tracks::get)
            } else {
                repo.search(q, limit)
            }
        }
    }
    return hits
}

fun LazyListScope.renderLocalHits(hits: List<Local_track>, onPlay: (Int) -> Unit) {
    if (hits.isEmpty()) return
    item(key = "local-hits-header") {
        Text(
            "On this device",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
    items(hits.size, key = { "local-${hits[it].id}" }) { i ->
        val t = hits[i]
        Surface(
            onClick = { onPlay(i) },
            color = Color.Transparent,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    "${t.artist_name} · ${t.album_title}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
