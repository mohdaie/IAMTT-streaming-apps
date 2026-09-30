package com.iamtt.streaming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.FolderType
import com.iamtt.streaming.data.Profile
import com.iamtt.streaming.data.VideoFile
import kotlin.random.Random

/**
 * The phone home screen, in the style of streaming apps: a featured title up top, then rows of
 * posters. Pull down to rescan the folders. Works upright or sideways.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHomeScreen(
    app: IamttApp,
    profile: Profile,
    onPlay: (VideoFile) -> Unit,
    onOpenSetup: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val state by app.library.state.collectAsStateWithLifecycle()
    var only by rememberSaveable { mutableStateOf<FolderType?>(null) }
    val pick = remember { Random.nextInt(8) }
    val history = remember(profile.id, state.snapshot) { app.history.inProgress(profile.id) }
    val home = remember(state.snapshot, history, only, profile.name) {
        buildHome(state.snapshot, history, profile.name, only, pick)
    }
    val listState = rememberLazyListState()
    // The bar starts see-through over the featured title and darkens as you scroll.
    val barAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 0.94f
            else (listState.firstVisibleItemScrollOffset / 220f).coerceIn(0f, 0.94f)
        }
    }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(IamttBackground),
    ) {
        PullToRefreshBox(
            isRefreshing = state.scanning,
            onRefresh = { app.library.rescan() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = statusBar + 56.dp, bottom = navBar + 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        CategoryChip("TV Shows", only == FolderType.TV_SHOWS) {
                            only = if (only == FolderType.TV_SHOWS) null else FolderType.TV_SHOWS
                        }
                        CategoryChip("Movies", only == FolderType.MOVIES) {
                            only = if (only == FolderType.MOVIES) null else FolderType.MOVIES
                        }
                    }
                }
                val status = when {
                    state.scanning -> "Scanning ${state.scanningFolder.orEmpty()}… ${state.foundSoFar} found"
                    state.lastError != null -> "⚠ ${state.lastError}"
                    else -> null
                }
                if (status != null) {
                    item {
                        Text(
                            status, color = IamttMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                home.featured?.let { featured ->
                    item {
                        Hero(
                            item = featured,
                            onPlay = { onPlay(featured.video) },
                            onStartOver = if (featured.progress != null) {
                                { app.history.clear(profile.id, featured.video.id); onPlay(featured.video) }
                            } else null,
                        )
                    }
                }
                if (home.isEmpty) {
                    item { EmptyLibrary(scanning = state.scanning, onOpenSetup = onOpenSetup, onRescan = { app.library.rescan() }) }
                }
                items(home.rows) { row ->
                    Column(Modifier.padding(top = 20.dp)) {
                        Text(
                            row.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(row.items, key = { it.video.id }) { item ->
                                PosterCard(item, width = 112.dp) { onPlay(item.video) }
                            }
                        }
                    }
                }
            }
        }

        // Top bar: logo, settings and the profile picture (tap it to switch profile).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = maxOf(0.75f, barAlpha)), Color.Black.copy(alpha = barAlpha))
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IamttLogo(height = 28.dp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSetup) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.pressable(avatarShape(30.dp), onSwitchProfile)) { ProfileAvatar(app, profile, 30.dp) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 14.sp) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = Color.White,
            selectedContainerColor = Color(0xFFE6E6EA),
            selectedLabelColor = Color.Black,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = Color(0xFF8A8A92), selectedBorderColor = Color.Transparent,
        ),
    )
}

/** The big featured title with Play (or Resume) and, when part-watched, Start over. */
@Composable
private fun Hero(item: HomeItem, onPlay: () -> Unit, onStartOver: (() -> Unit)?) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(12.dp)
        val sideways = maxWidth > 600.dp
        // Sideways, the banner leaves room for the top bar and chips so Play stays on screen.
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        Box(
            Modifier
                .width(min(maxWidth, if (sideways) 760.dp else 400.dp))
                .then(
                    if (sideways) Modifier.height((screenHeight - 150.dp).coerceIn(200.dp, 340.dp))
                    else Modifier.aspectRatio(0.72f)
                )
                .shadow(20.dp, shape)
                .clip(shape)
                .clickable(onClick = onPlay),
        ) {
            PosterArt(item.video.displayName, Modifier.fillMaxSize(), titleSize = 34.sp, titleBottomPadding = 118.dp)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    item.subtitle(), color = Color(0xFFD8D8DE), fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                item.watchedFraction()?.let {
                    Spacer(Modifier.height(10.dp))
                    WatchBar(it, Modifier.clip(CircleShape))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 420.dp)) {
                    Button(
                        onClick = onPlay,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (item.progress != null) "Resume" else "Play", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    if (onStartOver != null) {
                        Button(
                            onClick = onStartOver,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x996D6D78), contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Start over", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(item: HomeItem, width: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .pressable(shape, onClick),
    ) {
        PosterArt(item.video.displayName, Modifier.fillMaxSize())
        item.watchedFraction()?.let { WatchBar(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun EmptyLibrary(scanning: Boolean, onOpenSetup: () -> Unit, onRescan: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (scanning) "Looking through your folders…" else "Nothing to watch yet",
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        )
        if (!scanning) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Add your Movies and TV Shows folders in Settings, or pull down to scan again.",
                color = IamttMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenSetup) { Text("Open settings") }
                Button(
                    onClick = onRescan,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E36), contentColor = Color.White),
                ) { Text("Scan again") }
            }
        }
    }
}
