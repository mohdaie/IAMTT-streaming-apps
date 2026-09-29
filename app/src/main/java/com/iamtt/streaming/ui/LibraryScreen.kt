package com.iamtt.streaming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.FolderScan
import com.iamtt.streaming.data.FolderType
import com.iamtt.streaming.data.VideoFile
import kotlinx.coroutines.delay

/**
 * Phase 1 library: one row per chosen folder. Posters, hero banner and proper
 * movie/episode grouping arrive in Phases 2–3.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(app: IamttApp, onPlay: (VideoFile) -> Unit, onOpenSetup: () -> Unit) {
    val state by app.library.state.collectAsStateWithLifecycle()
    val scans = state.snapshot.scans
    val firstCard = remember { FocusRequester() }
    val hasVideos = scans.any { it.videos.isNotEmpty() }

    LaunchedEffect(hasVideos) {
        if (hasVideos) {
            delay(150)
            runCatching { firstCard.requestFocus() }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IamttBackground)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("IAMTT", color = IamttRed, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(24.dp))
                Text(
                    text = when {
                        state.scanning -> "Scanning ${state.scanningFolder ?: ""}… ${state.foundSoFar} found"
                        state.lastError != null -> "⚠ ${state.lastError}"
                        else -> "${scans.sumOf { it.videos.size }} videos"
                    },
                    color = IamttMuted, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val rescan: () -> Unit = { app.library.rescan() }
                OutlinedButton(onClick = rescan, modifier = Modifier.tapToClick(onClick = rescan)) { Text("Rescan") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onOpenSetup, modifier = Modifier.tapToClick(onClick = onOpenSetup)) {
                    Text("Settings")
                }
            }
        }

        if (scans.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 48.dp, vertical = 40.dp)) {
                    Text(
                        if (state.scanning) "Looking through your folders…" else "Nothing here yet.",
                        color = Color.White, fontSize = 24.sp,
                    )
                    if (!state.scanning) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onOpenSetup, modifier = Modifier.tapToClick(onClick = onOpenSetup)) {
                            Text("Open settings")
                        }
                    }
                }
            }
        }

        scans.forEachIndexed { rowIndex, scan ->
            item(key = scan.folder.id) {
                FolderRow(
                    scan = scan,
                    onPlay = onPlay,
                    firstCardModifier = if (rowIndex == 0) Modifier.focusRequester(firstCard) else Modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FolderRow(scan: FolderScan, onPlay: (VideoFile) -> Unit, firstCardModifier: Modifier) {
    Column {
        val type = if (scan.folder.type == FolderType.TV_SHOWS) "TV Shows" else "Movies"
        Text(
            "${scan.folder.name}  ·  $type  ·  ${scan.videos.size}",
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        scan.error?.let {
            Text("⚠ $it", color = Color(0xFFF5A524), fontSize = 15.sp, modifier = Modifier.padding(horizontal = 48.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (scan.videos.isEmpty()) {
            Text(
                "No videos found in this folder.",
                color = IamttMuted, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 48.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(scan.videos.size, key = { scan.videos[it].id }) { i ->
                    val video = scan.videos[i]
                    VideoCard(
                        video = video,
                        onClick = { onPlay(video) },
                        modifier = if (i == 0) firstCardModifier else Modifier,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoCard(video: VideoFile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.size(width = 260.dp, height = 146.dp).tapToClick(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF2A0A0D), Color(0xFF15151A)))
                )
                .padding(14.dp),
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                if (video.path.isNotEmpty()) {
                    Text(
                        video.path, color = IamttMuted, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    video.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(details(video), color = IamttMuted, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

private fun details(v: VideoFile): String {
    val parts = mutableListOf<String>()
    v.height?.let { h -> parts += when { h >= 2000 -> "4K"; h >= 1000 -> "1080p"; h >= 700 -> "720p"; else -> "${h}p" } }
    v.durationMs?.let { ms -> val m = ms / 60000; parts += if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
    if (v.size > 0) parts += String.format(java.util.Locale.US, "%.1f GB", v.size / 1_073_741_824.0)
    parts += v.name.substringAfterLast('.', "").uppercase()
    return parts.filter { it.isNotBlank() }.joinToString("  ·  ")
}
