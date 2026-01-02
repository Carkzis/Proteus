package com.carkzis.proteus

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class PlayerViewModel: ViewModel() {

    private val _playerState = MutableStateFlow<ExoPlayer?>(null)
    val playerState: StateFlow<ExoPlayer?> = _playerState

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
}