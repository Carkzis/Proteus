package com.carkzis.proteus

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import kotlin.math.abs

@UnstableApi
class PreloadStatusControl(var currentPlayingIndex: Int = 0) :
    TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {

    override fun getTargetPreloadStatus(index: Int): DefaultPreloadManager.PreloadStatus {
        if (index - currentPlayingIndex == 1) {
            return DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(3000L)
        } else if (index - currentPlayingIndex == -1) {
            return DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(3000L)
        } else if (abs(index - currentPlayingIndex) == 2) {
            return DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_TRACKS_SELECTED
        } else if (abs(index - currentPlayingIndex) <= 4) {
            return DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_SOURCE_PREPARED
        }
        return DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
    }
}