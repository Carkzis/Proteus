package com.carkzis.proteus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player

// Constant for broadcast receiver
const val ACTION_BROADCAST_CONTROL = "broadcast_control"

// Intent extras for broadcast controls from Picture-in-Picture mode.
const val EXTRA_CONTROL_TYPE = "control_type"
const val EXTRA_CONTROL_PLAY = 1
const val EXTRA_CONTROL_PAUSE = 2

@Composable
fun PlayerBroadcastReceiver(player: Player?) {
    val isInPipMode = rememberIsInPipMode()

    if (!isInPipMode || player == null) {
        return
    }

    val context = LocalContext.current
    DisposableEffect(player) {
        val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if ((intent == null) || (intent.action != ACTION_BROADCAST_CONTROL)) {
                    return
                }

                when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                    EXTRA_CONTROL_PAUSE -> player.pause()
                    EXTRA_CONTROL_PLAY -> player.play()
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            broadcastReceiver,
            IntentFilter(ACTION_BROADCAST_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(broadcastReceiver)
        }
    }
}