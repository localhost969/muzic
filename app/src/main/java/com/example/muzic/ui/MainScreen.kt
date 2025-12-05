package com.example.muzic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import com.example.muzic.data.MusicLibrary
import com.example.muzic.data.Song
import com.example.muzic.player.MusicPlayer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    library: MusicLibrary,
    currentSong: Song?,
    musicPlayer: MusicPlayer,
    onSongSelected: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf<com.example.muzic.data.Folder?>(null) }
    
    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    // Keep a LazyListState to reset the music player screen scroll whenever the mini player opens.
    val musicPlayerListState = rememberLazyListState()

    // Restore last-played song from MusicPlayer preferences and prepare it (do not auto-play)
    LaunchedEffect(library) {
        try {
            // Only prepare if there's no currently loaded song
            if (musicPlayer.currentSong.value == null) {
                val lastPath = musicPlayer.getSavedSongPath()
                val lastPos = musicPlayer.getSavedPosition()
                if (!lastPath.isNullOrEmpty()) {
                    val idx = library.songs.indexOfFirst { it.path == lastPath }
                    if (idx >= 0) {
                        musicPlayer.setPlaylist(library.songs, idx, playImmediately = false)
                        musicPlayer.prepareAt(idx, lastPos)
                    } else {
                        // If last path isn't found, just set the playlist but do not auto-prepare
                        musicPlayer.setPlaylist(library.songs, 0, playImmediately = false)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            if (currentSong != null) {
                MusicPlayerScreen(
                    musicPlayer = musicPlayer,
                    song = currentSong,
                    onCollapse = {
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    },
                    onToggleFavorite = onToggleFavorite,
                    listState = musicPlayerListState
                )
            } else {
                Box(Modifier.fillMaxSize().height(1.dp))
            }
        },
        sheetPeekHeight = 0.dp,
        sheetDragHandle = null,
        sheetContainerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(top = 16.dp)
        ) {
            // Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Always present the selected tab's content in the main area
                when (selectedTab) {
                    0 -> HomeScreen(
                        library,
                        onSongSelected,
                        onFolderSelected = { folder -> selectedFolder = folder },
                        onFolderPlay = { folder ->
                            try {
                                val folderSongs = library.songs.filter { it.path.startsWith(folder.path) }
                                if (folderSongs.isNotEmpty()) {
                                    musicPlayer.setPlaylist(folderSongs, 0, true)
                                    onSongSelected(folderSongs[0])
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                    1 -> FavouritesScreen(library, onSongSelected)
                    2 -> SongsScreen(library, musicPlayer, onSongSelected)
                }

                // If a folder is selected, show it in a modal dialog overlay
                if (selectedFolder != null) {
                    FolderDialog(
                        folder = selectedFolder!!,
                        library = library,
                        musicPlayer = musicPlayer,
                        onSongSelected = onSongSelected,
                        onDismiss = {
                            // when returning from a folder, always make the active tab Home and clear folder
                            selectedTab = 0
                            selectedFolder = null
                        }
                    )
                }
            }

            // Mini Player
            if (currentSong != null) {
                MiniPlayer(
                    song = currentSong,
                    musicPlayer = musicPlayer,
                    onClick = {
                        scope.launch {
                            try {
                                musicPlayerListState.animateScrollToItem(0)
                            } catch (_: Exception) {}
                            scaffoldState.bottomSheetState.expand()
                        }
                    }
                )
            }

            // Bottom Navigation
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it })
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    musicPlayer: MusicPlayer,
    onClick: () -> Unit
) {
    val isPlaying by musicPlayer.isPlaying.collectAsState()
    val currentPos by musicPlayer.currentPosition.collectAsState()
    val duration by musicPlayer.duration.collectAsState()

    val progress = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f

    // Swiss Design: High contrast, bold typography, clean layout.
    // We use a floating card style with the Primary Color (DeepInk/Black) background.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp) // Floating effect
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onBackground) // DeepInk (Black)
            .clickable { onClick() }
            .padding(12.dp) // Inner padding
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Album Art Placeholder
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Art",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.background, // PaperWhite
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Play/Pause Button - High contrast (White button on Black card)
            IconButton(
                onClick = { musicPlayer.togglePlayPause() },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onBackground, // Black icon
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress Bar
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.tertiary, // InternationalOrange
            trackColor = MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
        )
    }
}@Composable
fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Triple(0, "Home", Icons.Default.Home),
                Triple(1, "Likes", Icons.Default.Favorite),
                Triple(2, "Songs", Icons.Default.List)
            )

            items.forEach { (index, label, icon) ->
                val isSelected = selectedTab == index
                
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                    label = "bgAnim"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    label = "contentAnim"
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .clickable(onClick = { onTabSelected(index) })
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}