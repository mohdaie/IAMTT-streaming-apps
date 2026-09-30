package com.iamtt.streaming

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iamtt.streaming.data.Catalog
import com.iamtt.streaming.data.TitleInfo
import com.iamtt.streaming.data.VideoFile
import com.iamtt.streaming.player.PlayerScreen
import com.iamtt.streaming.ui.EditProfileScreen
import com.iamtt.streaming.ui.IamttTheme
import com.iamtt.streaming.ui.LibraryScreen
import com.iamtt.streaming.ui.PhoneHomeScreen
import com.iamtt.streaming.ui.ProfilesScreen
import com.iamtt.streaming.ui.SetupScreen
import com.iamtt.streaming.ui.TitleScreen
import com.iamtt.streaming.ui.label
import com.iamtt.streaming.ui.rememberIsTv

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as IamttApp
        setContent {
            IamttTheme {
                AppRoot(app)
            }
        }
    }
}

private sealed interface Screen {
    data object Setup : Screen
    data object Profiles : Screen
    /** [profileId] null adds a new profile. */
    data class EditProfile(val profileId: String?) : Screen
    data object Home : Screen
    /** A show's or movie's page; [id] is a CatalogTitle id. */
    data class Title(val id: String) : Screen
    /** Playback goes back to [from] (home or the title page) when it ends. */
    data class Player(val video: VideoFile, val from: Screen) : Screen
}

@Composable
private fun AppRoot(app: IamttApp) {
    val config by app.config.config.collectAsStateWithLifecycle()
    val isTv = rememberIsTv()
    // Like other streaming apps, ask who's watching every time the app opens.
    var screen by remember { mutableStateOf<Screen>(if (config.isReady) Screen.Profiles else Screen.Setup) }
    var profileId by remember { mutableStateOf<String?>(null) }
    val profile = config.profiles.firstOrNull { it.id == profileId }
    val afterSetup = if (profile != null) Screen.Home else Screen.Profiles

    // Refresh the library in the background each time the app opens.
    LaunchedEffect(Unit) {
        if (app.config.current.isReady) app.library.rescan()
    }
    // Look up artwork for anything new whenever the library changes.
    val library by app.library.state.collectAsStateWithLifecycle()
    val catalog = remember(library.snapshot) { Catalog.from(library.snapshot) }
    LaunchedEffect(catalog) { app.metadata.request(catalog) }

    when (val s = screen) {
        Screen.Setup -> {
            BackHandler(enabled = config.isReady) { screen = afterSetup }
            SetupScreen(app = app, onDone = { screen = afterSetup })
        }
        Screen.Profiles -> if (config.profiles.isEmpty()) {
            // First run: there's nobody to pick yet, so start by creating a profile.
            EditProfileScreen(
                app = app, existing = null,
                onSaved = { profileId = it.id; screen = Screen.Home },
                onDeleted = {}, onCancel = null,
            )
        } else {
            ProfilesScreen(
                app = app,
                onPick = { profileId = it.id; screen = Screen.Home },
                onAdd = { screen = Screen.EditProfile(null) },
                onEdit = { screen = Screen.EditProfile(it.id) },
            )
        }
        is Screen.EditProfile -> {
            BackHandler { screen = Screen.Profiles }
            EditProfileScreen(
                app = app,
                existing = config.profiles.firstOrNull { it.id == s.profileId },
                onSaved = { screen = Screen.Profiles },
                onDeleted = { screen = Screen.Profiles },
                onCancel = { screen = Screen.Profiles },
            )
        }
        Screen.Home -> when {
            profile == null -> LaunchedEffect(Unit) { screen = Screen.Profiles }
            isTv -> LibraryScreen(
                app = app, profile = profile,
                onPlay = { screen = Screen.Player(it, Screen.Home) },
                onOpenTitle = { screen = Screen.Title(it.id) },
                onOpenSetup = { screen = Screen.Setup },
                onSwitchProfile = { screen = Screen.Profiles },
            )
            else -> PhoneHomeScreen(
                app = app, profile = profile,
                onPlay = { screen = Screen.Player(it, Screen.Home) },
                onOpenTitle = { screen = Screen.Title(it.id) },
                onOpenSetup = { screen = Screen.Setup },
                onSwitchProfile = { screen = Screen.Profiles },
            )
        }
        is Screen.Title -> {
            val title = catalog.title(s.id)
            if (profile == null || title == null) LaunchedEffect(Unit) { screen = Screen.Home }
            else TitleScreen(
                app = app, profile = profile, title = title,
                onPlay = { screen = Screen.Player(it, s) },
                onBack = { screen = Screen.Home },
            )
        }
        is Screen.Player -> {
            val id = profileId
            if (id == null) LaunchedEffect(Unit) { screen = Screen.Profiles }
            else PlayerScreen(
                app = app, profileId = id, video = s.video,
                title = playerTitle(catalog, app.metadata.titles.value, s.video),
                onExit = { screen = s.from },
            )
        }
    }
}

/** "Lanterns · S1:E2 · Trust Fall" or "Inception", shown at the top of the player. */
private fun playerTitle(catalog: Catalog, infos: Map<String, TitleInfo>, video: VideoFile): String {
    val (title, episode) = catalog.locate(video.id) ?: return video.displayName
    val info = infos[title.id]
    if (episode == null) return info?.name ?: title.name
    // The episode's name from the show's details, else whatever the file name said.
    val name = info?.episodes?.firstOrNull { it.season == episode.season && it.number == episode.episode }?.name ?: episode.title
    return listOfNotNull(info?.name ?: title.name, episode.label(), name).joinToString(" · ")
}
