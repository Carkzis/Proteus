package com.carkzis.proteus

import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.Log
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
    val inPipMode = rememberIsInPipMode()

    PlayerScreen(
        modifier = modifier,
        exoPlayer = exoPlayer.value,
        isInPipMode = inPipMode,
        onPlayerLaunch = {
            playerViewModel.createPlayerWithMediaItems(
                context,
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            )
        }
    )
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer?,
    isInPipMode: Boolean,
    onPlayerLaunch: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (!isInPipMode) {
            Button(
                onClick = onPlayerLaunch,
                modifier = Modifier
            ) {
                Text(text = "Play Video")
            }
        }

        exoPlayer?.let {
            var duration by remember {
                mutableLongStateOf(exoPlayer.duration.takeIf { it >= 0 } ?: 0L)
            }
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
                modifier = Modifier.padding(8.dp)
            ) {
                PlayerSurface(
                    player = exoPlayer,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.Center)
                )

                val context = LocalContext.current

                if (!isInPipMode) {
                    Button(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.findActivity().enterPictureInPictureMode(
                                    PictureInPictureParams.Builder().build()
                                )
                            } else {
                                Log.i("PROTEUS_TAG", "API does not support PiP")
                            }
                        }) {
                        Text(text = "Enter PiP mode!")
                    }

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
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberIsInPipMode(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = LocalContext.current.findActivity()
        var pipMode by remember { mutableStateOf(activity.isInPictureInPictureMode) }
        DisposableEffect(activity) {
            val observer = Consumer<PictureInPictureModeChangedInfo> { info ->
                pipMode = info.isInPictureInPictureMode
            }
            activity.addOnPictureInPictureModeChangedListener(
                observer
            )
            onDispose { activity.removeOnPictureInPictureModeChangedListener(observer) }
        }
        return pipMode
    } else {
        return false
    }
}

internal fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("Picture in picture should be called in the context of an Activity")
}