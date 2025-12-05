package com.example.muzic.data

import android.content.ContentResolver
import android.database.Cursor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(private val contentResolver: ContentResolver) {

    suspend fun scanMusicLibrary(): MusicLibrary = withContext(Dispatchers.IO) {
        val songs = querySongs()
        val folders = extractFolders(songs)
        val mostPlayedSongs = songs.sortedByDescending { it.playCount }.take(10)
        val favoriteSongs = songs.filter { it.isFavorite }

        MusicLibrary(
            songs = songs,
            folders = folders,
            mostPlayedSongs = mostPlayedSongs,
            favoriteSongs = favoriteSongs
        )
    }

    private fun querySongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.DURATION} >= 60000" // Minimum 1 minute
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = contentResolver.query(
            audioUri,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val title = it.getString(titleColumn) ?: "Unknown"
                val artist = it.getString(artistColumn) ?: "Unknown"
                val album = it.getString(albumColumn) ?: "Unknown"
                val duration = it.getLong(durationColumn)
                val path = it.getString(dataColumn) ?: ""
                val dateAdded = it.getLong(dateAddedColumn)

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        path = path,
                        dateAdded = dateAdded
                    )
                )
            }
        }

        return songs
    }

    private fun extractFolders(songs: List<Song>): List<Folder> {
        val folderMap = mutableMapOf<String, Int>()

        songs.forEach { song ->
            val path = song.path
            val lastSlash = path.lastIndexOf("/")
            if (lastSlash > 0) {
                val folderPath = path.substring(0, lastSlash)
                val folderName = folderPath.substring(folderPath.lastIndexOf("/") + 1)
                folderMap[folderPath] = (folderMap[folderPath] ?: 0) + 1
            }
        }

        return folderMap.map { (path, count) ->
            Folder(
                path = path,
                name = path.substring(path.lastIndexOf("/") + 1),
                songCount = count
            )
        }.sortedByDescending { it.songCount }
    }
}
