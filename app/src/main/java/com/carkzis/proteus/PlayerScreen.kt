package com.carkzis.proteus

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.graphics.toRect
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.VideoSize
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
    val mediaMetadata = playerViewModel.mediaMetadata.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inPipMode = rememberIsInPipMode()

    PlayerScreen(
        modifier = modifier,
        exoPlayer = exoPlayer.value,
        mediaMetadata = mediaMetadata.value,
        isInPipMode = inPipMode,
        onPlayerLaunch = {
            playerViewModel.createPlayerWithMediaItems(
                context,
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            )
        },
        onObtainMetadata = {
            playerViewModel.retrieveMetadata(
                context,
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            )
        }
    )
}

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer?,
    mediaMetadata: MediaMetadata?,
    isInPipMode: Boolean,
    onPlayerLaunch: () -> Unit,
    onObtainMetadata: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
    ) {
        if (!isInPipMode) {
            Column {
                Button(
                    onClick = onPlayerLaunch,
                    modifier = Modifier
                ) {
                    Text(text = "Play Video")
                }

                Button(
                    onClick = onObtainMetadata,
                    modifier = Modifier
                ) {
                    Text(text = "Obtain Metadata")
                }
            }
        }

        exoPlayer?.let {
            val context = LocalContext.current

            val pipModifier = modifier.onGloballyPositioned { layoutCoordinates ->
                val builder = PictureInPictureParams.Builder()
                builder.setActions(
                    listOfRemoteActions(context)
                )

                if (exoPlayer.videoSize != VideoSize.UNKNOWN) {
                    val sourceRect = layoutCoordinates.boundsInWindow().toAndroidRectF().toRect()
                    builder.setSourceRectHint(sourceRect)
                    builder.setAspectRatio(
                        Rational(exoPlayer.videoSize.width, exoPlayer.videoSize.height)
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                }
                context.findActivity().setPictureInPictureParams(builder.build())
            }

            VideoPlayer(
                modifier = pipModifier,
                exoPlayer = exoPlayer,
                isInPipMode = isInPipMode
            )
        }

        Text("Media Length: ${mediaMetadata?.durationUs ?: ""} Us")
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer,
    isInPipMode: Boolean
) {
    var duration by remember {
        mutableLongStateOf(exoPlayer.duration.takeIf { it >= 0 } ?: 0L)
    }
    var position by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current

    PlayerBroadcastReceiver(exoPlayer, context)

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

        if (!isInPipMode) {
            Button(
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = {
                    context.findActivity().enterPictureInPictureMode(
                        PictureInPictureParams.Builder().build()
                    )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberIsInPipMode(): Boolean {
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
}

internal fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("Picture in picture should be called in the context of an Activity")
}

fun listOfRemoteActions(context: Context): List<RemoteAction> {
    val pauseIntent = Intent(ACTION_BROADCAST_CONTROL).apply {
        setPackage(context.packageName)
        putExtra(EXTRA_CONTROL_TYPE, EXTRA_CONTROL_PAUSE)
    }
    val pausePendingIntent = PendingIntent.getBroadcast(
        context, 0, pauseIntent, PendingIntent.FLAG_IMMUTABLE
    )
    val playIntent = Intent(ACTION_BROADCAST_CONTROL).apply {
        setPackage(context.packageName)
        putExtra(EXTRA_CONTROL_TYPE, EXTRA_CONTROL_PLAY)
    }
    val playPendingIntent = PendingIntent.getBroadcast(
        context, 1, playIntent, PendingIntent.FLAG_IMMUTABLE
    )

    val pauseAction = RemoteAction(
        Icon.createWithResource(context, android.R.drawable.ic_media_pause),
        "Pause",
        "Pause the video",
        pausePendingIntent
    )
    val playAction = RemoteAction(
        Icon.createWithResource(context, android.R.drawable.ic_media_play),
        "Play",
        "Play the video",
        playPendingIntent
    )
    return listOf(pauseAction, playAction)
}