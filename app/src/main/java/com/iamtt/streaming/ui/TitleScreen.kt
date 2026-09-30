package com.iamtt.streaming.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.CatalogTitle
import com.iamtt.streaming.data.EpisodeEntry
import com.iamtt.streaming.data.MovieEntry
import com.iamtt.streaming.data.Profile
import com.iamtt.streaming.data.ShowEntry
import com.iamtt.streaming.data.TitleInfo
import com.iamtt.streaming.data.VideoFile
import com.iamtt.streaming.data.WatchProgress
import kotlinx.coroutines.delay

/** A show's or movie's page: artwork, what it is, Play/Resume, and for shows every season's episodes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleScreen(
    app: IamttApp,
    profile: Profile,
    title: CatalogTitle,
    onPlay: (VideoFile) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val infos by app.metadata.titles.collectAsStateWithLifecycle()
    val info = infos[title.id]
    // Read fresh each time the page opens, so progress is current after watching.
    val history = remember(profile.id, title.id) { app.history.inProgress(profile.id) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { playFocus.requestFocus() }
    }

    val (playVideo, playLabel, progress) = when (title) {
        is ShowEntry -> title.nextUp(history).let { (e, p) -> Triple(e.video, (if (p != null) "Resume " else "Play ") + e.label(), p) }
        is MovieEntry -> history.firstOrNull { it.videoId == title.video.id }.let { p -> Triple(title.video, if (p != null) "Resume" else "Play", p) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IamttBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IamttLogo(height = 24.dp, modifier = Modifier.padding(end = 12.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 600.dp
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TitlePoster(
                    title, info,
                    Modifier
                        .width(if (wide) 180.dp else 128.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp)),
                    large = true,
                )
                Column(Modifier.weight(1f)) {
                    Text(info?.name ?: title.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(title.metaLine(info), color = IamttMuted, fontSize = 13.sp)
                    Text(
                        when (title) {
                            is ShowEntry -> "${title.episodes.size} episode${if (title.episodes.size == 1) "" else "s"} in your Drive"
                            is MovieEntry -> title.video.details()
                        },
                        color = IamttMuted, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onPlay(playVideo) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.widthIn(min = 160.dp).focusRequester(playFocus),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(playLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    if (progress != null) {
                        Button(
                            onClick = { app.history.clear(profile.id, playVideo.id); onPlay(playVideo) },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E36), contentColor = Color.White),
                            modifier = Modifier.widthIn(min = 160.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Start over", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
        info?.overview?.let {
            Text(it, color = Color(0xFFD8D8DE), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(16.dp))
        }
        if (title is MovieEntry && title.video.subtitles.isNotEmpty()) {
            Text(
                "Subtitles: " + title.video.subtitles.map { it.label }.distinct().joinToString(", "),
                color = IamttMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (title is ShowEntry) Episodes(title, info, history, onPlay)
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Episodes(show: ShowEntry, info: TitleInfo?, history: List<WatchProgress>, onPlay: (VideoFile) -> Unit) {
    var season by rememberSaveable(show.id) { mutableIntStateOf(show.nextUp(history).first.season) }
    val progressById = history.associateBy { it.videoId }
    if (show.seasons.size > 1) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            show.seasons.forEach { s ->
                FilterChip(
                    selected = s == season,
                    onClick = { season = s },
                    label = { Text("Season $s") },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.White, selectedContainerColor = Color(0xFFE6E6EA), selectedLabelColor = Color.Black,
                    ),
                )
            }
        }
    } else {
        Text(
            "Season $season", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    show.episodes.filter { it.season == season }.forEach { e ->
        EpisodeRow(show, e, info?.episodes?.firstOrNull { it.season == e.season && it.number == e.episode }, progressById[e.video.id]) {
            onPlay(e.video)
        }
    }
}

@Composable
private fun EpisodeRow(
    show: ShowEntry,
    episode: EpisodeEntry,
    info: com.iamtt.streaming.data.EpisodeInfo?,
    progress: WatchProgress?,
    onClick: () -> Unit,
) {
    val name = info?.name ?: episode.title ?: "Episode ${episode.episode}"
    val minutes = info?.runtime ?: episode.video.durationMs?.let { (it / 60_000).toInt() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pressable(RoundedCornerShape(8.dp), onClick)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(136.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            EpisodeStill(show.name, info?.still, Modifier.fillMaxSize())
            val total = progress?.durationMs?.takeIf { it > 0 } ?: episode.video.durationMs
            if (progress != null && total != null && total > 0) {
                WatchBar(progress.positionMs.toFloat() / total, Modifier.align(Alignment.BottomCenter))
            }
        }
        Column(Modifier.weight(1f)) {
            Text("${episode.episode}. $name", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            minutes?.let { Text("${it}m", color = IamttMuted, fontSize = 12.sp) }
            info?.summary?.let {
                Text(it, color = Color(0xFFB8B8C0), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
        }
    }
}

/** "1080p · 2h 20m · 2.2 GB · MKV" */
fun VideoFile.details(): String {
    val parts = mutableListOf<String>()
    height?.let { h -> parts += when { h >= 2000 -> "4K"; h >= 1000 -> "1080p"; h >= 700 -> "720p"; else -> "${h}p" } }
    durationMs?.let { ms -> val m = ms / 60000; parts += if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
    if (size > 0) parts += String.format(java.util.Locale.US, "%.1f GB", size / 1_073_741_824.0)
    parts += name.substringAfterLast('.', "").uppercase()
    return parts.filter { it.isNotBlank() }.joinToString("  ·  ")
}
