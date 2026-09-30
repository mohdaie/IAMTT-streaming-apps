package com.iamtt.streaming.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.Catalog
import com.iamtt.streaming.data.DownloadedSubtitle
import com.iamtt.streaming.data.OnlineSubtitles
import com.iamtt.streaming.data.Subtitles
import com.iamtt.streaming.data.VideoFile
import com.iamtt.streaming.ui.rememberIsTv

/**
 * Streams a video straight from Google Drive. ExoPlayer asks Drive for byte ranges,
 * so playback starts after a few seconds and seeking jumps without downloading the whole file.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(app: IamttApp, profileId: String, video: VideoFile, title: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val history = app.history
    var error by remember { mutableStateOf<String?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // The video with its subtitle files from Drive plus any fetched online earlier.
    fun mediaItem(online: List<DownloadedSubtitle>) = MediaItem.Builder()
        .setUri(app.drive.mediaUrl(video.id))
        .setMediaId(video.id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .setSubtitleConfigurations(subtitleTracks(app, video) + onlineTracks(online, default = video.subtitles.isEmpty()))
        .build()

    val player = remember(video.id) {
        buildPlayer(context, app).apply {
            setMediaItem(mediaItem(app.onlineSubtitles.downloaded(video.id)))
            // Subtitles on by default: files beside the video, or tracks inside it in your language or English.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredTextLanguages(*app.onlineSubtitles.languages().toTypedArray())
                .build()
            val resumeAt = history.resumeAt(profileId, video.id)
            if (resumeAt > 0) seekTo(resumeAt)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        var checkedOnline = false
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = describe(e)
            }

            // Once the video's own tracks are known: no subtitles in your language? Fetch some online.
            override fun onTracksChanged(tracks: Tracks) {
                if (checkedOnline || tracks.groups.isEmpty()) return
                checkedOnline = true
                val online = app.onlineSubtitles
                if (!online.enabled || video.subtitles.isNotEmpty() || online.downloaded(video.id).isNotEmpty()) return
                val wanted = online.languages()
                val hasOwn = tracks.groups.any { g ->
                    g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { i ->
                        g.getTrackFormat(i).language?.let { Subtitles.normalizeLanguage(it) } in wanted
                    }
                }
                if (hasOwn) return
                scope.launch {
                    notice = "Looking for subtitles online…"
                    val catalog = Catalog.from(app.library.state.value.snapshot)
                    val (found, episode) = catalog.locate(video.id) ?: (null to null)
                    val imdb = found?.let { app.metadata.titles.value[it.id]?.imdbId }
                    notice = when (val result = online.fetch(video, found, episode, imdb)) {
                        is OnlineSubtitles.Result.Found -> {
                            // Add them to the video already playing, from where it is now.
                            val position = player.currentPosition
                            val playing = player.playWhenReady
                            player.setMediaItem(mediaItem(listOf(result.subtitle)), position)
                            player.prepare()
                            player.playWhenReady = playing
                            "Subtitles: ${result.subtitle.label}"
                        }
                        OnlineSubtitles.Result.NoneFound -> "No subtitles found online"
                        is OnlineSubtitles.Result.Failed -> result.message
                    }
                    delay(4_000)
                    notice = null
                }
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

    // Phones get brightness and volume sliders, and swipes on the left and right halves.
    val levels = remember { if (isTv) null else context.findActivity()?.let(::BrightnessAndVolume) }
    var brightness by remember { mutableFloatStateOf(levels?.brightness() ?: 0.5f) }
    var volume by remember { mutableFloatStateOf(levels?.volume() ?: 0.5f) }
    var adjusting by remember { mutableStateOf<String?>(null) }   // "brightness" / "volume" while swiping
    val setBrightness: (Float) -> Unit = { brightness = it.coerceIn(0.02f, 1f); levels?.setBrightness(brightness) }
    val setVolume: (Float) -> Unit = { volume = it.coerceIn(0f, 1f); levels?.setVolume(volume) }
    if (levels != null) {
        DisposableEffect(levels) { onDispose { levels.resetBrightness() } }
    }
    LaunchedEffect(adjusting, brightness, volume) {
        if (adjusting != null) {
            delay(900)
            adjusting = null
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
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == View.VISIBLE })
                    if (levels != null) {
                        setOnTouchListener(swipeListener(ctx, this) { which, delta ->
                            adjusting = which
                            if (which == "volume") setVolume(volume + delta) else setBrightness(brightness + delta)
                        })
                    }
                    playerView = this
                }
            },
        )

        // Title (and, on phones, Back) along the top while the controls are showing.
        if (controlsVisible || adjusting != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isTv) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
                Text(title, color = Color.White, fontSize = 17.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (levels != null && (controlsVisible || adjusting != null)) {
            if (controlsVisible || adjusting == "brightness") {
                LevelSlider("☀", brightness, setBrightness, Modifier.align(Alignment.CenterStart).padding(start = 28.dp))
            }
            if (controlsVisible || adjusting == "volume") {
                LevelSlider("🔊", volume, setVolume, Modifier.align(Alignment.CenterEnd).padding(end = 28.dp))
            }
        }

        notice?.let {
            Text(
                it, color = Color.White, fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

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
    // Drive requests go through the Drive client's OkHttp, which adds the access token;
    // subtitles fetched online are local files.
    val dataSource = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(app.drive.http))
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

/** Subtitle files beside the video, streamed from Drive like the video; the one in your language is on by default. */
@OptIn(UnstableApi::class)
private fun subtitleTracks(app: IamttApp, video: VideoFile): List<MediaItem.SubtitleConfiguration> {
    val preferred = app.onlineSubtitles.languages()
    val ordered = video.subtitles.sortedBy { s -> preferred.indexOf(s.language).let { if (it < 0) preferred.size else it } }
    return ordered.mapIndexed { i, s ->
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(app.drive.mediaUrl(s.id)))
            .setId(s.id)
            .setMimeType(
                when (s.name.substringAfterLast('.').lowercase()) {
                    "vtt" -> MimeTypes.TEXT_VTT
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
            )
            .setLanguage(s.language)
            .setLabel(s.label)
            .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
            .build()
    }
}

/** Subtitles fetched online, as local files. [default] turns the first on when there are no Drive ones. */
@OptIn(UnstableApi::class)
private fun onlineTracks(online: List<DownloadedSubtitle>, default: Boolean) = online.mapIndexed { i, s ->
    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(s.file))
        .setId("online-${s.language}")
        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
        .setLanguage(s.language)
        .setLabel(s.label)
        .setSelectionFlags(if (default && i == 0) C.SELECTION_FLAG_DEFAULT else 0)
        .build()
}
