package com.example.muzic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class PlayCountManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("muzic_play_counts", Context.MODE_PRIVATE)
    private val PLAY_COUNT_PREFIX = "play_count_"
    private val FAVORITE_PREFIX = "favorite_"
    // Emits song paths whose play count has been incremented. Useful for UI to refresh lists.
    private val _playCountUpdated = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val playCountUpdated: SharedFlow<String> = _playCountUpdated
    
    // Emits song paths whose favorite status has changed. Useful for UI to refresh lists.
    private val _favoriteStatusUpdated = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val favoriteStatusUpdated: SharedFlow<String> = _favoriteStatusUpdated

    /**
     * Increment the play count for a song by its path
     */
    fun incrementPlayCount(songPath: String) {
        val currentCount = getPlayCount(songPath)
        prefs.edit().putInt(PLAY_COUNT_PREFIX + songPath.hashCode(), currentCount + 1).apply()
        // Try to notify listeners — use tryEmit to avoid needing coroutine scope here.
        _playCountUpdated.tryEmit(songPath)
    }

    /**
     * Get the play count for a song by its path
     */
    fun getPlayCount(songPath: String): Int {
        return prefs.getInt(PLAY_COUNT_PREFIX + songPath.hashCode(), 0)
    }

    /**
     * Toggle the favorite status for a song by its path
     */
    fun toggleFavorite(songPath: String): Boolean {
        val currentStatus = isFavorite(songPath)
        val newStatus = !currentStatus
        prefs.edit().putBoolean(FAVORITE_PREFIX + songPath.hashCode(), newStatus).apply()
        _favoriteStatusUpdated.tryEmit(songPath)
        return newStatus
    }

    /**
     * Check if a song is marked as favorite by its path
     */
    fun isFavorite(songPath: String): Boolean {
        return prefs.getBoolean(FAVORITE_PREFIX + songPath.hashCode(), false)
    }

    /**
     * Update a list of songs with their actual play counts from local storage
     */
    fun enrichSongsWithPlayCounts(songs: List<Song>): List<Song> {
        return songs.map { song ->
            song.copy(playCount = getPlayCount(song.path), isFavorite = isFavorite(song.path))
        }
    }
}
