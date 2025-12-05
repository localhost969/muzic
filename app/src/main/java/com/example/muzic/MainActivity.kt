package com.example.muzic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.muzic.data.MediaStoreScanner
import com.example.muzic.data.MusicLibrary
import com.example.muzic.data.PlayCountManager
import com.example.muzic.data.Song
import com.example.muzic.player.MusicPlayer
import com.example.muzic.ui.MainScreen
import com.example.muzic.ui.theme.MuzicTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var musicPlayer: MusicPlayer
    private lateinit var playCountManager: PlayCountManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission handling is done in onCreate via composition
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permission if not already granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }

        // Create a single PlayCountManager instance and pass it into the player
        playCountManager = PlayCountManager(this)
        musicPlayer = MusicPlayer(this, playCountManager)

        setContent {
            MuzicTheme {
                var musicLibrary by remember { mutableStateOf<MusicLibrary?>(null) }
                var currentSong by remember { mutableStateOf<Song?>(null) }
                var hasPermission by remember { 
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.READ_MEDIA_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                    )
                }

                // Update permission state when permission is granted
                LaunchedEffect(Unit) {
                    // Check permission periodically to detect when it's granted
                    while (true) {
                        val currentHasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.READ_MEDIA_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                        if (currentHasPermission != hasPermission) {
                            hasPermission = currentHasPermission
                        }
                        kotlinx.coroutines.delay(500)
                    }
                }

                // Scan music library when permission is granted
                LaunchedEffect(hasPermission) {
                    if (hasPermission && musicLibrary == null) {
                        val scanner = MediaStoreScanner(contentResolver)
                        var library = scanner.scanMusicLibrary()
                        
                        // Enrich songs with actual play counts from local storage
                        library = library.copy(
                            songs = playCountManager.enrichSongsWithPlayCounts(library.songs),
                            mostPlayedSongs = playCountManager.enrichSongsWithPlayCounts(library.songs)
                                .sortedByDescending { it.playCount }
                                .filter { it.playCount > 0 }
                                .take(5)
                                .ifEmpty {
                                    // If no songs have been played, show top 5 recently added
                                    library.songs.sortedByDescending { it.dateAdded }.take(5)
                                },
                            favoriteSongs = playCountManager.enrichSongsWithPlayCounts(library.favoriteSongs)
                        )
                        musicLibrary = library
                        if (library.songs.isNotEmpty()) {
                            currentSong = library.songs[0]
                            // set playlist on music player so next/prev work
                            musicPlayer.setPlaylist(library.songs, 0, false)
                        }
                    }
                }

                // Listen for play count updates and refresh library counts when they change.
                LaunchedEffect(playCountManager) {
                    playCountManager.playCountUpdated.collect {
                        // Only refresh if we have a library
                        val lib = musicLibrary ?: return@collect
                        val enriched = playCountManager.enrichSongsWithPlayCounts(lib.songs)
                        musicLibrary = lib.copy(
                            songs = enriched,
                            mostPlayedSongs = enriched
                                .sortedByDescending { it.playCount }
                                .filter { it.playCount > 0 }
                                .take(5)
                                .ifEmpty { enriched.sortedByDescending { it.dateAdded }.take(5) },
                            favoriteSongs = enriched.filter { it.isFavorite }
                        )
                    }
                }
                
                // Listen for favorite status updates and refresh library when they change
                LaunchedEffect(playCountManager) {
                    playCountManager.favoriteStatusUpdated.collect {
                        // Only refresh if we have a library
                        val lib = musicLibrary ?: return@collect
                        val enriched = playCountManager.enrichSongsWithPlayCounts(lib.songs)
                        musicLibrary = lib.copy(
                            songs = enriched,
                            favoriteSongs = enriched.filter { it.isFavorite }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Keep the UI's currentSong in sync with the MusicPlayer.
                    LaunchedEffect(musicPlayer) {
                        musicPlayer.currentSong.collect { song ->
                            currentSong = song
                        }
                    }
                    if (musicLibrary != null) {
                        MainScreen(
                            library = musicLibrary!!,
                            currentSong = currentSong,
                            musicPlayer = musicPlayer,
                            onSongSelected = { song ->
                                currentSong = song
                                musicPlayer.play(song)
                            },
                            onToggleFavorite = { song ->
                                playCountManager.toggleFavorite(song.path)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        musicPlayer.release()
        super.onDestroy()
    }
}