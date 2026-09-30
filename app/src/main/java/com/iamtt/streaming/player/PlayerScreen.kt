package com.iamtt.streaming.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.VideoFile
import com.iamtt.streaming.ui.rememberIsTv

/**
 * Streams a video straight from Google Drive. ExoPlayer asks Drive for byte ranges,
 * so playback starts after a few seconds and seeking jumps without downloading the whole file.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(app: IamttApp, profileId: String, video: VideoFile, onExit: () -> Unit) {
    val context = LocalContext.current
    val history = app.history
    var error by remember { mutableStateOf<String?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    val player = remember(video.id) {
        buildPlayer(context, app).apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(app.drive.mediaUrl(video.id))
                    .setMediaId(video.id)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(video.displayName).build())
                    .build()
            )
            val resumeAt = history.resumeAt(profileId, video.id)
            if (resumeAt > 0) seekTo(resumeAt)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = describe(e)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) history.clear(profileId, video.id)
            }
        }
        player.addListener(listener)
        onDispose {
            if (player.playbackState != Player.STATE_ENDED) {
                history.save(profileId, video.id, player.currentPosition, player.duration)
            }
            player.removeListener(listener)
            player.release()
        }
    }

    // Pause when the TV goes to the home screen or sleeps.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                history.save(profileId, video.id, player.currentPosition, player.duration)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // On a phone, play sideways and full screen like other video apps; put things back afterwards.
    val isTv = rememberIsTv()
    if (!isTv) {
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val bars = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                bars?.show(WindowInsetsCompat.Type.systemBars())
                activity?.requestedOrientation = previous
            }
        }
    }

    BackHandler {
        val view = playerView
        if (view != null && view.isControllerFullyVisible && error == null) view.hideController() else onExit()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowSubtitleButton(true)
                    controllerShowTimeoutMs = 4000
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    playerView = this
                }
            },
        )

        error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xDD000000))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Can't play this video", color = Color.White, fontSize = 26.sp)
                Text(message, color = Color(0xFFBBBBC2), fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
                Text("Go back to return", color = Color(0xFF8A8A92), fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp))
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context, app: IamttApp): ExoPlayer {
    // All requests go through the Drive client's OkHttp, which adds the access token.
    val dataSource = OkHttpDataSource.Factory(app.drive.http)
    return ExoPlayer.Builder(context)
        .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
        .setLoadControl(
            DefaultLoadControl.Builder()
                // min buffer, max buffer, buffer before start, buffer after a stall (ms)
                .setBufferDurationsMs(20_000, 90_000, 2_500, 5_000)
                .build()
        )
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
}

private fun describe(e: PlaybackException): String {
    val cause = generateSequence(e as Throwable) { it.cause }.last()
    return when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "Google Drive refused the stream. If this keeps happening the file may have hit Drive's daily download limit."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network problem — check the internet connection."
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
            "This device can't decode this video/audio format (${e.errorCodeName})."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "This file type isn't supported."
        else -> "${e.errorCodeName}: ${cause.message ?: e.message ?: ""}"
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
