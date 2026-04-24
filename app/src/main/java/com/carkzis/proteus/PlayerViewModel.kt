package com.carkzis.proteus


import android.annotation.SuppressLint
import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.inspector.MetadataRetriever
import androidx.media3.inspector.frame.FrameExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel: ViewModel() {

    private val _playerState = MutableStateFlow<ExoPlayer?>(null)
    val playerState: StateFlow<ExoPlayer?> = _playerState

    private val _mediaMetadata = MutableStateFlow<MediaMetadata?>(null)
    val mediaMetadata: StateFlow<MediaMetadata?> = _mediaMetadata

    private val _frameData = MutableStateFlow<FrameData?>(null)
    val frameData: StateFlow<FrameData?> = _frameData

    @OptIn(UnstableApi::class)
    fun createPlayerWithMediaItems(context: Context, uri: String, reverseAudio: Boolean = false) {
        if (_playerState.value == null) {
            // Create Media item
            val mediaItem = MediaItem.Builder().setUri(uri).build()

            val audioProcessors = if (reverseAudio) {
                arrayOf(
                    InvertAudioProcessor(),
                    ReverseAudioProcessor()
                )
            } else {
                arrayOf(
                    InvertAudioProcessor(),
                )
            }

            // Create the custom audio sink with the processor
            val defaultAudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(audioProcessors)
                .build()

            // Use the custom RenderersFactory to inject the audio sink
            val renderersFactory = CustomRenderersFactory(context, defaultAudioSink)

            // Create the player instance and update it to UI via stateFlow
            _playerState.update {
                val exoPlayer = ExoPlayer.Builder(context)
                    .setRenderersFactory(renderersFactory)
                    .build().apply {
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

    fun extractFrame(context: Context, uri: String) {
        val mediaItem = MediaItem.Builder().setUri(uri).build()

        viewModelScope.launch {
            extractFrame(context, mediaItem)
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun extractFrame(context: Context, mediaItem: MediaItem) {
        try {
            // 1. Build the frame extractor.
            // `FrameExtractor` implements `AutoCloseable`, so wrap it in
            // a Kotlin `.use` block, which calls `close()` automatically.
            FrameExtractor.Builder(context, mediaItem).build().use { extractor ->
                // 2. Extract frames asynchronously.
                val frame = extractor.getFrame(30000L).await()
                val thumbnail = extractor.thumbnail.await()

                val frameData = FrameData(
                    frame,
                    thumbnail
                )

                _frameData.value = frameData
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun boostBass() {
        boost(BoostType.BASS)
    }

    fun boostMid() {
        boost(BoostType.MID)
    }

    fun boostTreble() {
        boost(BoostType.TREBLE)
    }

    private fun boost(band: BoostType) {
        _playerState.value?.let { player ->
            val audioSessionId = player.audioSessionId
            val equalizer = Equalizer(0, audioSessionId)
            equalizer.enabled = true
            val numberOfBands = equalizer.numberOfBands
            // Boost the highest frequency band (treble)
            if (numberOfBands > 0) {
                val maxLevel = equalizer.bandLevelRange[1]
                val bandId = band.toBandShort(numberOfBands)
                Log.e("PlayerViewModel", "Current $band band level: ${equalizer.getBandLevel(bandId)}")
                Log.e("PlayerViewModel", "Range for $band band $bandId: ${equalizer.bandLevelRange[0]} to ${equalizer.bandLevelRange[1]}}")
                Log.e("PlayerViewModel", "Boosting $band band $bandId to max level $maxLevel")
                equalizer.setBandLevel(bandId, maxLevel) // Boost to max
            }
        }
    }

    enum class BoostType {
        BASS, MID, TREBLE;

        fun toBandShort(numberOfBands: Short): Short {
            return when (this) {
                BASS -> 0.toShort()
                MID -> (numberOfBands / 2).toShort()
                TREBLE -> (numberOfBands - 1).toShort()
            }
        }
    }
}

data class MediaMetadata(
    @param:SuppressLint("UnsafeOptInUsageError") val trackGroups: TrackGroupArray,
    val timeline: Timeline,
    val durationUs: Long
)

data class FrameData(
    @param:SuppressLint("UnsafeOptInUsageError") val frame: FrameExtractor.Frame,
    @param:SuppressLint("UnsafeOptInUsageError") val thumbnail: FrameExtractor.Frame
)