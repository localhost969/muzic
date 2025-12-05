package com.example.muzic.player

import android.content.Context
import android.media.MediaPlayer
import android.content.SharedPreferences
import android.net.Uri
import com.example.muzic.data.Song
import com.example.muzic.data.PlayCountManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class MusicPlayer(context: Context, private val playCountManager: com.example.muzic.data.PlayCountManager) {
    // Use a mutable mediaPlayer instance so we can swap players during crossfade
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private var altMediaPlayer: MediaPlayer? = null
    private val context = context
    private val prefs: SharedPreferences = context.getSharedPreferences("muzic_prefs", Context.MODE_PRIVATE)
    private val LAST_SONG_PATH = "last_song_path"
    private val LAST_POSITION_MS = "last_position_ms"
    private val LAST_PLAYLIST_INDEX = "last_playlist_index"

    // coroutine scope for updates
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var saveJob: Job? = null
    private var fadeJob: Job? = null
    private val defaultCrossfadeMs = 600L

    private val _isPlaying = MutableStateFlow<Boolean>(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _currentPosition = MutableStateFlow<Long>(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow<Long>(0L)
    val duration: StateFlow<Long> = _duration

    private val _playMode = MutableStateFlow<PlayMode>(PlayMode.NORMAL)
    val playMode: StateFlow<PlayMode> = _playMode

    private var playlist: List<Song> = emptyList()
    private var currentIndex: Int = -1
    // Keep track of which song's play has already been counted in the current play session
    private var countedPlayPath: String? = null

    init {
        mediaPlayer.setOnCompletionListener {
            // Increment play count for completed song only if we haven't already counted it
            val song = _currentSong.value
            if (song != null) {
                if (countedPlayPath != song.path) {
                    playCountManager.incrementPlayCount(song.path)
                    countedPlayPath = song.path
                }
            }
            
            // handle next based on mode
            when (_playMode.value) {
                PlayMode.REPEAT_ONE -> {
                    try {
                        // simple replay
                        mediaPlayer.seekTo(0)
                        mediaPlayer.start()
                        _isPlaying.value = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _isPlaying.value = false
                    }
                }

                PlayMode.SHUFFLE -> {
                    if (playlist.isNotEmpty()) {
                        val nextIndex = Random.nextInt(playlist.size)
                        playAt(nextIndex)
                    } else {
                        _isPlaying.value = false
                    }
                }

                PlayMode.REPEAT_ALL -> {
                    if (hasNext()) next() else playAt(0)
                }

                PlayMode.NORMAL -> {
                    if (hasNext()) next() else {
                        _isPlaying.value = false
                        _currentPosition.value = 0L
                    }
                }
            }
        }

        mediaPlayer.setOnPreparedListener {
            _duration.value = mediaPlayer.duration.toLong()
        }

        // Attempt to restore last-played data if present (we don't auto-start playback)
        // We'll provide getters so UI/Home can map the path to a Song in the library and call prepareAt()
    }

    // Optional UI-friendly modes
    enum class PlayMode {
        NORMAL, REPEAT_ONE, REPEAT_ALL, SHUFFLE
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0, playImmediately: Boolean = true) {
        playlist = songs.toList()
        if (playlist.isEmpty()) {
            stop()
            return
        }
        currentIndex = startIndex.coerceIn(0, playlist.lastIndex)
        if (playImmediately) playAt(currentIndex)
    }

    /**
     * Prepare (load) the playlist and the song at the index but do not start playback.
     * This ensures MediaPlayer is prepared and ready to seek/resume later.
     */
    fun prepareAt(index: Int, positionMs: Long = 0L) {
        if (playlist.isEmpty() || index !in playlist.indices) return
        currentIndex = index
        val song = playlist[currentIndex]
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, Uri.parse(song.path))
            mediaPlayer.prepare()
            _currentSong.value = song
            _duration.value = if (mediaPlayer.duration > 0) mediaPlayer.duration.toLong() else _duration.value
            mediaPlayer.seekTo(positionMs.toInt())
            _currentPosition.value = positionMs
            _isPlaying.value = false
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

    fun addToPlaylist(song: Song) {
        playlist = playlist + song
    }

    fun play(song: Song) {
        try {
            // If same song is already loaded, just resume when paused, or do nothing if already playing
            if (_currentSong.value?.path == song.path) {
                if (_isPlaying.value) return
                else { resume(); return }
            }
            // Stop and clear existing progress job before preparing new one
            if (_isPlaying.value) {
                mediaPlayer.stop()
            }
            stopProgressJob()

            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, Uri.parse(song.path))
            mediaPlayer.prepare()

            _currentSong.value = song
            // Ensure playlist includes the song so 'next'/'previous' work; for new users
            if (playlist.isEmpty()) {
                playlist = listOf(song)
                currentIndex = 0
            } else if (!playlist.contains(song)) {
                playlist = playlist + song
                currentIndex = playlist.indexOf(song)
            }
            // sync index if this song is in current playlist
            val foundIndex = playlist.indexOf(song)
            if (foundIndex >= 0) currentIndex = foundIndex
            _duration.value = mediaPlayer.duration.toLong()

            // If currently playing a different file, perform a crossfade
            if (_isPlaying.value && _currentSong.value != null && _currentSong.value?.path != song.path) {
                crossfadeTo(song, defaultCrossfadeMs)
                return
            }

            // Normal start with smooth fade-in
            mediaPlayer.setVolume(0f, 0f)
            mediaPlayer.start()
            // Count this as a play immediately (so even a short play counts)
            if (countedPlayPath != song.path) {
                playCountManager.incrementPlayCount(song.path)
                countedPlayPath = song.path
            }
            _isPlaying.value = true
            startProgressJob()
            fadeIn(mediaPlayer, defaultCrossfadeMs)
            saveState()
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

    fun playAt(index: Int) {
        if (playlist.isEmpty() || index !in playlist.indices) return
        currentIndex = index
        play(playlist[currentIndex])
    }

    fun next() {
        if (playlist.isEmpty()) return
        when (_playMode.value) {
            PlayMode.SHUFFLE -> playAt(Random.nextInt(playlist.size))
            else -> {
                val nextIndex = if (currentIndex + 1 <= playlist.lastIndex) currentIndex + 1 else -1
                // if normal and at end, pick a random song instead of stopping
                if (_playMode.value == PlayMode.NORMAL && nextIndex == -1) {
                    if (playlist.isNotEmpty()) {
                        // pick a random index different from currentIndex if possible
                        val randIndex = if (playlist.size > 1) {
                            var idx = Random.nextInt(playlist.size)
                            var attempts = 0
                            while (idx == currentIndex && attempts < 5) {
                                idx = Random.nextInt(playlist.size)
                                attempts++
                            }
                            idx
                        } else 0
                        playAt(randIndex)
                    }
                } else if (nextIndex != -1) {
                    playAt(nextIndex)
                }
            }
        }
    }

    fun previous() {
        if (playlist.isEmpty()) return
        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else playlist.lastIndex
        playAt(prevIndex)
    }

    fun hasNext(): Boolean {
        return playlist.isNotEmpty() && currentIndex < playlist.lastIndex
    }

    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        if (_isPlaying.value) {
            mediaPlayer.pause()
            _isPlaying.value = false
            stopProgressJob()
            saveState()
        }
    }

    fun resume() {
        if (!_isPlaying.value && _currentSong.value != null) {
            // Start with fade-in for smooth resume
            mediaPlayer.setVolume(0f, 0f)
            mediaPlayer.start()
            // Count as a play if we haven't counted this song yet (e.g., prepared or loading then user pressed resume)
            val song = _currentSong.value
            if (song != null && countedPlayPath != song.path) {
                playCountManager.incrementPlayCount(song.path)
                countedPlayPath = song.path
            }
            _isPlaying.value = true
            startProgressJob()
            fadeIn(mediaPlayer, defaultCrossfadeMs)
            saveState()
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer.seekTo(positionMs.toInt())
            _currentPosition.value = positionMs
            saveState()
        } catch (_: Exception) {
        }
    }

    /**
     * Crossfade from current player to new song using a second MediaPlayer instance.
     * This will prepare and start the new player then smoothly transition volumes.
     */
    private fun crossfadeTo(song: Song, crossfadeMs: Long = defaultCrossfadeMs) {
        // Cancel any ongoing fade
        fadeJob?.cancel()
        scope.launch {
            try {
                val newPlayer = MediaPlayer()
                altMediaPlayer = newPlayer
                // prepare new player synchronously for now (could use async)
                newPlayer.reset()
                newPlayer.setDataSource(context, Uri.parse(song.path))
                newPlayer.prepare()
                newPlayer.setVolume(0f, 0f)
                newPlayer.start()
                // For crossfades, count the incoming song as a play immediately
                if (countedPlayPath != song.path) {
                    playCountManager.incrementPlayCount(song.path)
                    countedPlayPath = song.path
                }

                val oldPlayer = mediaPlayer
                val steps = 15.coerceAtLeast((crossfadeMs / 50).toInt())
                val delayMs = (crossfadeMs / steps).coerceAtLeast(1L)
                fadeJob = scope.launch {
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val oldVol = 1f - t
                        val newVol = t
                        try {
                            oldPlayer.setVolume(oldVol, oldVol)
                        } catch (_: Exception) {
                        }
                        try {
                            newPlayer.setVolume(newVol, newVol)
                        } catch (_: Exception) {
                        }
                        delay(delayMs)
                    }
                }
                fadeJob?.join()

                // stop and release old player
                try { if (oldPlayer.isPlaying) oldPlayer.stop() } catch (_: Exception) {}
                try { oldPlayer.reset() } catch (_: Exception) {}
                try { oldPlayer.release() } catch (_: Exception) {}

                // swap players
                mediaPlayer = newPlayer
                altMediaPlayer = null
                _currentSong.value = song
                _duration.value = if (mediaPlayer.duration > 0) mediaPlayer.duration.toLong() else _duration.value
                _currentPosition.value = mediaPlayer.currentPosition.toLong()
                _isPlaying.value = true
                stopProgressJob()
                startProgressJob()
                saveState()
            } catch (e: Exception) {
                e.printStackTrace()
                try { altMediaPlayer?.reset(); altMediaPlayer?.release() } catch (_: Exception) {}
                altMediaPlayer = null
            }
        }
    }

    /** Smoothly fade in a player's volume */
    private fun fadeIn(player: MediaPlayer, durationMs: Long) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            try {
                val steps = 12.coerceAtLeast((durationMs / 50).toInt())
                val delayMs = (durationMs / steps).coerceAtLeast(1L)
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val vol = t
                    try { player.setVolume(vol, vol) } catch (_: Exception) {}
                    delay(delayMs)
                }
                player.setVolume(1f, 1f)
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getCurrentPosition(): Long = mediaPlayer.currentPosition.toLong()

    fun stop() {
        try {
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer.reset()
        } catch (_: Exception) {
        }
        _isPlaying.value = false
        _currentPosition.value = 0L
        _currentSong.value = null
        // Reset play-count flag on stop so a resume or new play can be counted again
        countedPlayPath = null
        stopProgressJob()
        // Cancel ongoing fades and release any alternate player
        fadeJob?.cancel()
        fadeJob = null
        try { altMediaPlayer?.stop() } catch (_: Exception) {}
        try { altMediaPlayer?.reset() } catch (_: Exception) {}
        try { altMediaPlayer?.release() } catch (_: Exception) {}
        altMediaPlayer = null
        saveState()
    }

    fun release() {
        stopProgressJob()
        scope.cancel()
        try {
            mediaPlayer.release()
        } catch (_: Exception) {
        }
        // Persist final state
        saveState()
        fadeJob?.cancel()
        fadeJob = null
    }

    private fun startProgressJob() {
        stopProgressJob()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                try {
                    _currentPosition.value = mediaPlayer.currentPosition.toLong()
                    _duration.value = if (mediaPlayer.duration > 0) mediaPlayer.duration.toLong() else _duration.value
                } catch (_: Exception) {
                }
                delay(250)
            }
        }
        startSaveJob()
    }

    private fun stopProgressJob() {
        progressJob?.cancel()
        progressJob = null
        stopSaveJob()
    }

    private fun startSaveJob() {
        stopSaveJob()
        saveJob = scope.launch {
            while (isActive) {
                saveState()
                delay(5000)
            }
        }
    }

    private fun stopSaveJob() {
        saveJob?.cancel()
        saveJob = null
    }

    private fun saveState() {
        try {
            val path = _currentSong.value?.path
            val pos = _currentPosition.value
            val idx = currentIndex
            prefs.edit().apply {
                if (path != null) putString(LAST_SONG_PATH, path) else remove(LAST_SONG_PATH)
                putLong(LAST_POSITION_MS, pos)
                putInt(LAST_PLAYLIST_INDEX, idx)
            }.apply()
        } catch (_: Exception) {
        }
    }

    fun getSavedSongPath(): String? = prefs.getString(LAST_SONG_PATH, null)
    fun getSavedPosition(): Long = prefs.getLong(LAST_POSITION_MS, 0L)
    fun getSavedPlaylistIndex(): Int = prefs.getInt(LAST_PLAYLIST_INDEX, -1)
    fun getPlayCountManager(): PlayCountManager = playCountManager

    /**
     * Get the current playlist
     */
    fun getPlaylist(): List<Song> = playlist.toList()

    /**
     * Get the current index in the playlist
     */
    fun getCurrentIndex(): Int = currentIndex

    /**
     * Get the upcoming songs (songs after the current song in the playlist)
     */
    fun getUpcomingSongs(): List<Song> {
        if (playlist.isEmpty() || currentIndex >= playlist.lastIndex) return emptyList()
        return playlist.subList(currentIndex + 1, playlist.size).toList()
    }
}
