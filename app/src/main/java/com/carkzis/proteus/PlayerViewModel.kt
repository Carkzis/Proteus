package com.carkzis.proteus

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.inspector.MetadataRetriever
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel: ViewModel() {

    private val _playerState = MutableStateFlow<ExoPlayer?>(null)
    val playerState: StateFlow<ExoPlayer?> = _playerState

    private val _mediaMetadata = MutableStateFlow<MediaMetadata?>(null)
    val mediaMetadata: StateFlow<MediaMetadata?> = _mediaMetadata

    @OptIn(UnstableApi::class)
    fun createPlayerWithMediaItems(context: Context, uri: String) {
        if (_playerState.value == null) {
            // Create Media item
            val mediaItem = MediaItem.Builder().setUri(uri).build()

            // Create the player instance and update it to UI via stateFlow
            _playerState.update {
                val exoPlayer = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                }
                exoPlayer
            }
        }
    }

    fun retrieveMetadata(context: Context, uri: String) {
        val mediaItem = MediaItem.Builder().setUri(uri).build()

        viewModelScope.launch {
            retrieveMetadata(context, mediaItem)
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun retrieveMetadata(context: Context, mediaItem: MediaItem) {
        try {
            // 1. Build the retriever.
            // `MetadataRetriever` implements `AutoCloseable`, so wrap it in
            // a Kotlin `.use` block, which calls `close()` automatically.
            MetadataRetriever.Builder(context, mediaItem).build().use { retriever ->
                // 2. Retrieve metadata asynchronously.
                val trackGroups = retriever.retrieveTrackGroups().await()
                val timeline = retriever.retrieveTimeline().await()
                val durationUs = retriever.retrieveDurationUs().await()

                val mediaMetadata = MediaMetadata(
                    trackGroups,
                    timeline,
                    durationUs
                )

                _mediaMetadata.value = mediaMetadata
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}

data class MediaMetadata(
    @param:SuppressLint("UnsafeOptInUsageError") val trackGroups: TrackGroupArray,
    val timeline: Timeline,
    val durationUs: Long
)