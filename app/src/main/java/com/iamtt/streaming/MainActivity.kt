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
import com.iamtt.streaming.data.VideoFile
import com.iamtt.streaming.player.PlayerScreen
import com.iamtt.streaming.ui.IamttTheme
import com.iamtt.streaming.ui.LibraryScreen
import com.iamtt.streaming.ui.SetupScreen

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
    data object Library : Screen
    data class Player(val video: VideoFile) : Screen
}

@Composable
private fun AppRoot(app: IamttApp) {
    val config by app.config.config.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(if (config.isReady) Screen.Library else Screen.Setup) }

    // Refresh the library in the background each time the app opens.
    LaunchedEffect(Unit) {
        if (app.config.current.isReady) app.library.rescan()
    }

    when (val s = screen) {
        Screen.Setup -> {
            BackHandler(enabled = config.isReady) { screen = Screen.Library }
            SetupScreen(app = app, onDone = { screen = Screen.Library })
        }
        Screen.Library -> LibraryScreen(
            app = app,
            onPlay = { screen = Screen.Player(it) },
            onOpenSetup = { screen = Screen.Setup },
        )
        is Screen.Player -> {
            PlayerScreen(app = app, video = s.video, onExit = { screen = Screen.Library })
        }
    }
}
