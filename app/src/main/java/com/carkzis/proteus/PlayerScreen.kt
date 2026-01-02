package com.carkzis.proteus

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import kotlinx.coroutines.delay

@Composable
fun PlayerRoute(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val exoPlayer = playerViewModel.playerState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Row {
        Button(
            onClick = {
                playerViewModel.createPlayerWithMediaItems(
                    context,
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                )
            },
            modifier = modifier
        ) {
            Text(text = "Play Video")
        }
    }

    Box(modifier.fillMaxSize()) {
        exoPlayer.value?.let {
            PlayerScreen(exoPlayer = it)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer,
) {
    var duration by remember { mutableLongStateOf(exoPlayer.duration.takeIf { it >= 0 } ?: 0L) }
    var position by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            position = exoPlayer.currentPosition
            delay(500L)
        }
    }

    LaunchedEffect(
        exoPlayer.isPlaying
    ) {
        if (exoPlayer.isPlaying) {
            duration = exoPlayer.duration
        }
    }

    Box(
        modifier = modifier.padding(8.dp)
    ) {
        PlayerSurface(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
        )

        Slider(
            value = position.toFloat(),
            onValueChange = {
                exoPlayer.isScrubbingModeEnabled = true
                exoPlayer.seekTo(it.toLong())
                            },
            valueRange = 0f..duration.toFloat(),
            onValueChangeFinished = {
                exoPlayer.isScrubbingModeEnabled = false
            },
            modifier = Modifier.fillMaxWidth().padding(8.dp).align(Alignment.BottomCenter)
        )
    }
}