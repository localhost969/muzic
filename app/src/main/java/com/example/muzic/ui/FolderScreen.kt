package com.example.muzic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.muzic.data.Folder
import com.example.muzic.data.MusicLibrary
import com.example.muzic.data.Song
import com.example.muzic.player.MusicPlayer

@Composable
fun FolderContent(
    folder: Folder,
    library: MusicLibrary,
    musicPlayer: MusicPlayer,
    onSongSelected: (Song) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    val folderSongs = library.songs.filter { it.path.startsWith(folder.path) }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (showHeader) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp)
                )
            }
        }

        LazyColumn {
            items(folderSongs) { song ->
                val idx = folderSongs.indexOf(song)
                SongListItem(song, onSongSelected = { selected ->
                    // set playlist to the folder's songs and play selected
                    musicPlayer.setPlaylist(folderSongs, idx, true)
                    onSongSelected(selected)
                })
            }
            item { Spacer(Modifier.height(200.dp)) }
        }
    }
}

@Composable
fun FolderHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    showClose: Boolean = false,
    onClose: () -> Unit = {},
    centerTitle: Boolean = false,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    subtitleStyle: TextStyle? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.align(if (centerTitle) Alignment.Center else Alignment.CenterStart)) {
            Text(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.background
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = subtitleStyle ?: MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                )
            }
        }

        if (showClose) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.background, // white icon
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun FolderScreen(
    folder: Folder,
    library: MusicLibrary,
    musicPlayer: MusicPlayer,
    onSongSelected: (Song) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        FolderHeader(
            title = folder.name,
            subtitle = "${folder.songCount} songs",
            showBack = true,
            onBack = onBack,
            centerTitle = false
        )

        FolderContent(
            folder = folder,
            library = library,
            musicPlayer = musicPlayer,
            onSongSelected = onSongSelected,
            modifier = Modifier.weight(1f),
            showHeader = false
        )
    }
}

@Composable
fun FolderDialog(
    folder: Folder,
    library: MusicLibrary,
    musicPlayer: MusicPlayer,
    onSongSelected: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header without song count
                FolderHeader(
                    title = folder.name,
                    showClose = true,
                    onClose = onDismiss,
                    centerTitle = true,
                    titleStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp)
                )

                FolderContent(
                    folder = folder,
                    library = library,
                    musicPlayer = musicPlayer,
                    onSongSelected = onSongSelected,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    showHeader = false
                )

                // Song count at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${folder.songCount} songs",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
