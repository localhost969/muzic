package com.example.muzic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Folder
// Removed unused Play/Pause icon imports not used in HomeScreen to avoid unresolved reference errors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.muzic.data.Folder as DataFolder
import com.example.muzic.data.MusicLibrary
import com.example.muzic.data.Song

data class TreeNode(
    val folder: DataFolder,
    val children: MutableList<TreeNode> = mutableListOf()
)

data class TreeItem(
    val node: TreeNode,
    val prefix: String
)

fun buildTree(folders: List<DataFolder>): List<TreeNode> {
    // Build a tree, but only include folders that actually contain songs.
    // If an intermediate (parent) folder contains 0 songs, it won't be shown;
    // any child folder that has songs will be attached to the nearest ancestor
    // that also has songs; if none exists, the child becomes a root.
    val included = folders.filter { it.songCount > 0 }.sortedBy { it.path }
    val map = mutableMapOf<String, TreeNode>()

    // Create nodes only for included folders
    for (folder in included) {
        map[folder.path] = TreeNode(folder)
    }

    // Attach each included node to the nearest included parent, if any
    for ((path, node) in map) {
        var parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
        var attached = false
        while (parentPath.isNotEmpty()) {
            if (map.containsKey(parentPath)) {
                map[parentPath]!!.children.add(node)
                attached = true
                break
            }
            parentPath = parentPath.substringBeforeLast('/', missingDelimiterValue = "")
        }
        // if no parent found, node remains a root
    }

    // Return roots (paths with no parent included). Root path will have only one slash at the start if it is like "/Music".
    return map.values.filter { node ->
        val path = node.folder.path
        var parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
        var foundParent = false
        while (parentPath.isNotEmpty()) {
            if (map.containsKey(parentPath)) {
                foundParent = true
                break
            }
            parentPath = parentPath.substringBeforeLast('/', missingDelimiterValue = "")
        }
        !foundParent
    }
}

fun buildTreeItems(nodes: List<TreeNode>, prefix: String = ""): List<TreeItem> {
    val list = mutableListOf<TreeItem>()
    nodes.forEachIndexed { index, node ->
        val isLast = index == nodes.size - 1
        val newPrefix = prefix + if (isLast) "└─" else "├─"
        list.add(TreeItem(node, newPrefix))
        val childPrefix = prefix + if (isLast) "  " else "│ "
        list.addAll(buildTreeItems(node.children, childPrefix))
    }
    return list
}

@Composable
fun HomeScreen(
    library: MusicLibrary,
    onSongSelected: (Song) -> Unit,
    onFolderSelected: (DataFolder) -> Unit,
    onFolderPlay: ((DataFolder) -> Unit)? = null
) {
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
                text = "MUZIC",
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

        // Compute a stable remember key and the tree items in the @Composable scope
        val folderKey = library.folders.map { "${it.path}:${it.songCount}" }
        val treeItems = remember(folderKey) { buildTreeItems(buildTree(library.folders)) }

        LazyColumn {
            // Most Played Section
            item {
                Text(
                    text = "MOST PLAYED",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            items(library.mostPlayedSongs.take(5)) { song ->
                SongListItem(song, onSongSelected, showPlayCount = true)
            }

            // Folders Section
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "FOLDERS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // Use a derived key so remember only recomputes when the folder contents change
            // Build a list of stable strings (path:songCount) and use it as the key for remember.
            // This way, even if a new List instance is created but its contents are equal, remember
            // will NOT re-run the heavy buildTree/buildTreeItems computation.
            items(treeItems) { item ->
                FolderTreeItem(item.node, item.prefix, onFolderSelected, onFolderPlay)
            }

            item {
                Spacer(modifier = Modifier.height(200.dp))
            }
        }
        }
    }
}

@Composable
fun SongListItem(song: Song, onSongSelected: (Song) -> Unit, showPlayCount: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onSongSelected(song) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            if (showPlayCount) {
                Text(
                    text = if (song.playCount == 1) "1 time" else "${song.playCount} times",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun FolderListItem(
    folder: DataFolder,
    onFolderSelected: (DataFolder) -> Unit,
    onFolderPlay: ((DataFolder) -> Unit)? = null
) {
        Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
            .padding(16.dp)
            .clickable { onFolderSelected(folder) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
            Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
                Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${folder.songCount} songs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp
                )
            }
        }

        // Folder icon on the right for folder cards
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Folder",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun FolderTreeItem(
    node: TreeNode,
    prefix: String,
    onFolderSelected: (DataFolder) -> Unit,
    onFolderPlay: ((DataFolder) -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFolderSelected(node.folder) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace
        )
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Folder",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = node.folder.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${node.folder.songCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
