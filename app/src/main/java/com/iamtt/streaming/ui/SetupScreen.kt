package com.iamtt.streaming.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.FolderType
import com.iamtt.streaming.setup.SetupServer
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(app: IamttApp, onDone: () -> Unit) {
    val config by app.config.config.collectAsStateWithLifecycle()
    val library by app.library.state.collectAsStateWithLifecycle()

    var url by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var serverError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val server = SetupServer(app, app.config, app.drive, app.auth, app.library)
        try {
            val port = server.start()
            val ip = SetupServer.localIpAddress()
            pin = server.pin
            url = ip?.let { "http://$it:$port/?pin=${server.pin}" }
            if (ip == null) serverError = "The TV isn't connected to a network."
        } catch (e: Exception) {
            serverError = e.message
        }
        onDispose { server.stop() }
    }

    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(config.isReady) {
        if (config.isReady) {
            delay(150)
            runCatching { doneFocus.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(IamttBackground)
            .padding(horizontal = 56.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("IAMTT", color = IamttRed, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text("Set up on your phone", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "Scan the QR code with your phone (on the same Wi-Fi). The page lets you upload your " +
                    "Google service account key and choose which Drive folders to use. " +
                    "Only those folders are ever scanned.",
                color = IamttMuted, fontSize = 18.sp,
            )
            Spacer(Modifier.height(28.dp))

            StatusLine(
                done = config.hasKey,
                text = if (config.hasKey) "Key added — ${config.serviceAccountEmail}" else "Step 1: upload the service account key",
            )
            StatusLine(
                done = config.folders.isNotEmpty(),
                text = if (config.folders.isEmpty()) "Step 2: add your Movies / TV Shows folders"
                else "${config.folders.size} folder(s) added",
            )
            config.folders.forEach { f ->
                val scan = library.snapshot.scans.firstOrNull { it.folder.id == f.id }
                val detail = when {
                    scan?.error != null -> "⚠ ${scan.error}"
                    scan != null -> "${scan.videos.size} videos"
                    library.scanning && library.scanningFolder == f.name -> "scanning… ${library.foundSoFar} found"
                    else -> "waiting to scan"
                }
                val type = if (f.type == FolderType.TV_SHOWS) "TV Shows" else "Movies"
                Text(
                    "      •  ${f.name}  ($type) — $detail",
                    color = Color(0xFFD8D8DE), fontSize = 17.sp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onDone,
                enabled = config.isReady,
                modifier = Modifier.focusRequester(doneFocus),
            ) {
                Text(if (config.isReady) "Done — go to my library" else "Waiting for setup…")
            }
        }

        Column(
            modifier = Modifier.width(360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val qr = remember(url) { url?.let { runCatching { qrBitmap(it) }.getOrNull() } }
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) {
                    Image(bitmap = qr, contentDescription = "Setup QR code", modifier = Modifier.fillMaxSize())
                } else {
                    Text(serverError ?: "Starting…", color = Color.Black)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Or open this address on your phone:", color = IamttMuted, fontSize = 15.sp)
            Text(
                url?.substringBefore("/?") ?: "—",
                color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(10.dp))
            Text("PIN", color = IamttMuted, fontSize = 15.sp)
            Text(pin, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
        }
    }
}

@Composable
private fun StatusLine(done: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(if (done) Color(0xFF2FBF71) else Color(0xFF3A3A42), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else "", color = Color.White, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 19.sp)
    }
}
