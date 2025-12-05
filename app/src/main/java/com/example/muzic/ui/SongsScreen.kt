package com.example.muzic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import java.util.LinkedHashMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muzic.data.MusicLibrary
import com.example.muzic.data.Song

enum class SortMode {
    RECENTLY_ADDED_DESC, // default: newest first
    RECENTLY_ADDED_ASC,  // oldest first
    ALPHABETICAL_ASC
}

private fun getTitleInitial(title: String): String {
    val trimmed = title.trim()
    val first = trimmed.firstOrNull() ?: return "#"
    val upper = first.uppercaseChar()
    return if (upper in 'A'..'Z') upper.toString() else "#"
}

@Composable
fun SongsScreen(
    library: MusicLibrary,
    musicPlayer: com.example.muzic.player.MusicPlayer,
    onSongSelected: (Song) -> Unit,
    enableChipPressAnimation: Boolean = true
) {
    var sortMode by remember { mutableStateOf(SortMode.RECENTLY_ADDED_DESC) }
    val listState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // Unified Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                text = "ALL SONGS",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Sorting controls in a dedicated row below the header, aligned to the right,
            // styled as compact chips so they don't look clumsy.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recently chip (toggles asc/desc), black/white theme + subtle press scale (no ripple)
                run {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(targetValue = if (isPressed && enableChipPressAnimation) 0.98f else 1f, animationSpec = tween(durationMillis = 90))
                    val selected = sortMode == SortMode.RECENTLY_ADDED_DESC || sortMode == SortMode.RECENTLY_ADDED_ASC
                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color.Black else Color.White)
                            .border(1.dp, Color.Black.copy(alpha = if (selected) 0.12f else 0.2f), RoundedCornerShape(12.dp))
                            .clickable(indication = null, interactionSource = interactionSource) {
                                sortMode = when (sortMode) {
                                    SortMode.RECENTLY_ADDED_DESC -> SortMode.RECENTLY_ADDED_ASC
                                    SortMode.RECENTLY_ADDED_ASC -> SortMode.RECENTLY_ADDED_DESC
                                    else -> SortMode.RECENTLY_ADDED_DESC
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (sortMode == SortMode.RECENTLY_ADDED_DESC) "Recently (Newest)" else if (sortMode == SortMode.RECENTLY_ADDED_ASC) "Recently (Oldest)" else "Recently",
                            color = if (selected) Color.White else Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // A→Z chip, black/white theme + subtle press scale (no ripple)
                run {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(targetValue = if (isPressed && enableChipPressAnimation) 0.98f else 1f, animationSpec = tween(durationMillis = 90))
                    val selected = sortMode == SortMode.ALPHABETICAL_ASC
                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color.Black else Color.White)
                            .border(1.dp, Color.Black.copy(alpha = if (selected) 0.12f else 0.2f), RoundedCornerShape(12.dp))
                            .clickable(indication = null, interactionSource = interactionSource) {
                                sortMode = SortMode.ALPHABETICAL_ASC
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "A → Z",
                            color = if (selected) Color.White else Color.Black
                        )
                    }
                }
            }

            val sortedSongs = remember(library.songs, sortMode) {
                when (sortMode) {
                    SortMode.RECENTLY_ADDED_DESC -> library.songs.sortedByDescending { it.dateAdded }
                    SortMode.RECENTLY_ADDED_ASC -> library.songs.sortedBy { it.dateAdded }
                    SortMode.ALPHABETICAL_ASC -> library.songs.sortedBy { it.title.lowercase() }
                }
            }

            // Group songs by initial character for sectioned display
            val grouped = remember(sortedSongs) {
                val groupedMap = sortedSongs.groupBy { getTitleInitial(it.title) }
                val orderedKeys = groupedMap.keys.sortedWith(compareBy { key: String -> if (key == "#") "Z{" else key })
                LinkedHashMap<String, List<com.example.muzic.data.Song>>().apply {
                    for (k in orderedKeys) put(k, groupedMap[k]!!)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                val totalItemsCount = listState.layoutInfo.totalItemsCount

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(end = 20.dp)
                ) {
                    grouped.forEach { (initial, songs) ->
                        item(key = "header_$initial") {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(songs, key = { it.id }) { song ->
                            SongListItem(song, onSongSelected = { selected ->
                                val idx = sortedSongs.indexOf(song)
                                musicPlayer.setPlaylist(sortedSongs, idx, true)
                                onSongSelected(selected)
                            })
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(200.dp))
                    }
                }

                // Thin unique scrollbar overlay
                val total = totalItemsCount
                if (total > 0) {
                    val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                    val first = listState.firstVisibleItemIndex
                    val barHeightRatio = visible.toFloat() / total.toFloat().coerceAtLeast(1f)
                    val barOffsetRatio = if (total - visible > 0) first.toFloat() / (total - visible).toFloat() else 0f
                    val barHeightPx = maxHeightPx * barHeightRatio
                    val barOffsetPx = (maxHeightPx - barHeightPx) * barOffsetRatio

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 12.dp) // give extra inset so the overlay doesn't visually touch cards
                                .offset { IntOffset(0, barOffsetPx.toInt()) }
                                .height(with(LocalDensity.current) { barHeightPx.toDp() })
                                .width(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                        )
                    }
                }
            }
        }
    }
}
