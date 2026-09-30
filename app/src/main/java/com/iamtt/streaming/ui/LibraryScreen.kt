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
import com.iamtt.streaming.data.Catalog
import com.iamtt.streaming.data.CatalogTitle
import com.iamtt.streaming.data.Profile
import com.iamtt.streaming.data.TitleInfo
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
    onOpenTitle: (CatalogTitle) -> Unit,
    onOpenSetup: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val state by app.library.state.collectAsStateWithLifecycle()
    val infos by app.metadata.titles.collectAsStateWithLifecycle()
    val catalog = remember(state.snapshot) { Catalog.from(state.snapshot) }
    val history = remember(profile.id, state.snapshot) { app.history.inProgress(profile.id) }
    val home = remember(catalog, history, infos, profile.name) { buildHome(catalog, history, infos, profile.name) }
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
                        else -> "${catalog.shows.size} shows · ${catalog.movies.size} movies"
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
                        items(row.cards.size, key = { row.cards[it].title.id }) { i ->
                            val card = row.cards[i]
                            TvPosterCard(
                                card = card,
                                info = infos[card.title.id],
                                onClick = {
                                    val resume = card.resume
                                    if (resume != null) onPlay(resume) else onOpenTitle(card.title)
                                },
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
private fun TvPosterCard(card: HomeCard, info: TitleInfo?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.size(width = 150.dp, height = 225.dp).tapToClick(onClick = onClick)) {
        Box(Modifier.fillMaxSize()) {
            TitlePoster(card.title, info, Modifier.fillMaxSize(), fallbackTitleSize = 16.sp)
            card.watchedFraction()?.let { WatchBar(it, Modifier.align(Alignment.BottomCenter)) }
        }
    }
}
