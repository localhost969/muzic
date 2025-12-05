package com.example.muzic.data

import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val path: String,
    val dateAdded: Long,
    val playCount: Int = 0,
    val isFavorite: Boolean = false
)

@Immutable
data class Folder(
    val path: String,
    val name: String,
    val songCount: Int
)

@Immutable
data class MusicLibrary(
    val songs: List<Song>,
    val folders: List<Folder>,
    val mostPlayedSongs: List<Song>,
    val favoriteSongs: List<Song>
)
