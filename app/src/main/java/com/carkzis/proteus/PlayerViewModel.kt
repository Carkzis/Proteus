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
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
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

    private val _preloadManagerState = MutableStateFlow<DefaultPreloadManager?>(null)
    val preloadManagerState: StateFlow<DefaultPreloadManager?> = _preloadManagerState

    private val _preloadStatusControl = MutableStateFlow<PreloadStatusControl?>(null)
    private val preloadMediaItems = MutableStateFlow<List<MediaItem>>(emptyList())

    @OptIn(UnstableApi::class)
    fun createPlayerWithMediaItem(context: Context, uri: String, reverseAudio: Boolean = false) {
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

    @OptIn(ExperimentalApi::class)
    fun createPlayerWithPreloadManager(context: Context, uris: List<String>) {
        val preloadManagerBuilder = createPreloadManagerBuilder(context)
        val preloadManager = preloadManagerBuilder.build()

        _preloadManagerState.value = preloadManager

        val mediaItems = uris.map { uri ->
            MediaItem.Builder().setUri(uri).build()
        }

        addItemsToPreloadManager(mediaItems, preloadManager)

        if (_playerState.value == null) {
            _playerState.update {
                val exoPlayer = preloadManagerBuilder.buildExoPlayer()

                exoPlayer
            }
        }
    }

    fun updateCurrentPlayingIndex(index: Int) {
        _playerState.value?.let { player ->
            _preloadStatusControl.value?.currentPlayingIndex = index

            val preloadManager = _preloadManagerState.value
            val mediaItem = preloadMediaItems.value[index]

            val mediaSource = preloadManager?.getMediaSource(mediaItem)

            if (mediaSource != null) {
                player.setMediaSource(mediaSource)
            } else {
                player.setMediaItem(mediaItem)
            }
            player.prepare()

            player.play()

            preloadManager?.setCurrentPlayingIndex(index)

            preloadManager?.invalidate()
        }
    }

    fun invalidateExoPlayer() {
        _playerState.value?.release()
        _playerState.value = null
    }

    fun invalidatePreloadManager() {
        _preloadManagerState.value?.invalidate()
        _preloadManagerState.value = null
    }

    private fun createPreloadManagerBuilder(context: Context) : DefaultPreloadManager.Builder {
        val targetPreloadStatusControl = PreloadStatusControl()

        _preloadStatusControl.value = targetPreloadStatusControl

        val preloadManagerBuilder = DefaultPreloadManager.Builder(context, targetPreloadStatusControl)
        return preloadManagerBuilder
    }

    private fun addItemsToPreloadManager(mediaItems: List<MediaItem>, preloadManager: DefaultPreloadManager) {
        for (index in 0 until mediaItems.size) {
            preloadManager.add(mediaItems[index], index)
        }

        preloadMediaItems.value = mediaItems

        preloadManager.invalidate()
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