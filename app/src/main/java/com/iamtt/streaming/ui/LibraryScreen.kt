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
import com.iamtt.streaming.data.Profile
import com.iamtt.streaming.data.VideoFile
import kotlinx.coroutines.delay

/**
 * The TV home: rows of titles for the remote, starting with this profile's Continue Watching.
 * Posters, a hero banner and proper episode grouping arrive in Phases 2–3.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    app: IamttApp,
    profile: Profile,
    onPlay: (VideoFile) -> Unit,
    onOpenSetup: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val state by app.library.state.collectAsStateWithLifecycle()
    val history = remember(profile.id, state.snapshot) { app.history.inProgress(profile.id) }
    val home = remember(state.snapshot, history, profile.name) { buildHome(state.snapshot, history, profile.name) }
    val firstCard = remember { FocusRequester() }
    val hasVideos = home.rows.isNotEmpty()

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
                IamttLogo(height = 40.dp)
                Spacer(Modifier.width(24.dp))
                Text(
                    text = when {
                        state.scanning -> "Scanning ${state.scanningFolder ?: ""}… ${state.foundSoFar} found"
                        state.lastError != null -> "⚠ ${state.lastError}"
                        else -> "${state.snapshot.scans.sumOf { it.videos.size }} videos"
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
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onSwitchProfile, modifier = Modifier.tapToClick(onClick = onSwitchProfile)) {
                    ProfileAvatar(app, profile, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(profile.name, maxLines = 1)
                }
            }
        }

        if (home.isEmpty) {
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

        home.rows.forEachIndexed { rowIndex, row ->
            item {
                Column {
                    Text(
                        row.title,
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(row.items.size, key = { row.items[it].video.id }) { i ->
                            val item = row.items[i]
                            VideoCard(
                                item = item,
                                onClick = { onPlay(item.video) },
                                modifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstCard) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoCard(item: HomeItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.size(width = 260.dp, height = 146.dp).tapToClick(onClick = onClick)) {
        Box(Modifier.fillMaxSize()) {
            PosterArt(item.video.displayName, Modifier.fillMaxSize(), titleSize = 17.sp, titleBottomPadding = 26.dp)
            Text(
                details(item.video), color = Color(0xFFD8D8DE), fontSize = 12.sp, maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 9.dp, bottom = 9.dp),
            )
            item.watchedFraction()?.let { WatchBar(it, Modifier.align(Alignment.BottomCenter)) }
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
