package com.iamtt.streaming.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.FolderType
import com.iamtt.streaming.setup.SetupServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(app: IamttApp, onDone: () -> Unit) {
    val context = LocalContext.current
    val config by app.config.config.collectAsStateWithLifecycle()
    val library by app.library.state.collectAsStateWithLifecycle()

    var port by remember { mutableStateOf<Int?>(null) }
    var pin by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val server = SetupServer(app, app.config, app.drive, app.auth, app.library)
        try {
            port = server.start()
            pin = server.pin
        } catch (e: Exception) {
            serverError = e.message
        }
        onDispose { server.stop() }
    }
    // Wi-Fi may come up after setup opens (or change), so keep the address current.
    LaunchedEffect(port) {
        while (port != null) {
            ip = withContext(Dispatchers.IO) { SetupServer.localIpAddress() }
            delay(if (ip == null) 3_000 else 15_000)
        }
    }
    val url = if (port != null && ip != null) "http://$ip:$port/?pin=$pin" else null

    // Phones and tablets can open the folder picker in their own browser; TVs rarely have one.
    val isTv = remember {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    var openHereError by remember { mutableStateOf<String?>(null) }
    val openHere: () -> Unit = {
        port?.let { p ->
            openHereError = try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$p/?pin=$pin")))
                null
            } catch (e: ActivityNotFoundException) {
                "No web browser found on this device."
            }
        }
    }

    val signIn = rememberGoogleSignIn(app)
    val signInClick: () -> Unit = { signIn.start(switching = config.hasAccess) }
    val signInFocus = remember { FocusRequester() }
    val doneFocus = remember { FocusRequester() }
    // Re-run when the sign-in button swaps styles after signing in, so focus isn't lost.
    LaunchedEffect(config.isReady, config.hasAccess) {
        delay(150)
        runCatching { if (config.isReady) doneFocus.requestFocus() else signInFocus.requestFocus() }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(IamttBackground)
            // Phones on Android 15 draw apps behind the status bar; keep text clear of it.
            .safeDrawingPadding(),
    ) {
        // A phone held sideways is much shorter than a TV screen, so shrink the QR code to fit.
        val compact = maxHeight < 480.dp
        val qrSize = (maxHeight - if (compact) 200.dp else 250.dp).coerceIn(140.dp, 320.dp)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 32.dp else 56.dp, vertical = if (compact) 16.dp else 40.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 32.dp else 48.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("IAMTT", color = IamttRed, fontSize = 40.sp, fontWeight = FontWeight.Black)
                Text("Set up", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isTv) "Sign in with the Google account that has your movies, then scan the QR code with " +
                        "your phone (on the same Wi-Fi) to choose which Drive folders to use. " +
                        "Only those folders are ever scanned."
                    else "Sign in with the Google account that has your movies, then choose which Drive " +
                        "folders to use. Only those folders are ever scanned.",
                    color = IamttMuted, fontSize = 18.sp,
                )
                Spacer(Modifier.height(if (compact) 16.dp else 28.dp))

                StatusLine(
                    done = config.hasAccess,
                    text = when {
                        config.usesGoogleAccount -> "Signed in as ${config.googleAccount}"
                        config.hasKey -> "Using service account ${config.serviceAccountEmail}"
                        else -> "Step 1: sign in with Google"
                    },
                )
                Row(modifier = Modifier.padding(start = 34.dp, top = 6.dp, bottom = 8.dp)) {
                    val label = when {
                        signIn.busy -> "Signing in…"
                        config.usesGoogleAccount -> "Switch account"
                        else -> "Sign in with Google"
                    }
                    val modifier = Modifier.focusRequester(signInFocus).tapToClick(onClick = signInClick)
                    if (config.hasAccess) {
                        OutlinedButton(onClick = signInClick, modifier = modifier) { Text(label) }
                    } else {
                        Button(onClick = signInClick, modifier = modifier) { Text(label) }
                    }
                }
                signIn.error?.let { Warning(it) }
                StatusLine(
                    done = config.folders.isNotEmpty(),
                    text = when {
                        config.folders.isNotEmpty() -> "${config.folders.size} folder(s) added"
                        isTv -> "Step 2: scan the QR code to add your Movies / TV Shows folders"
                        else -> "Step 2: add your Movies / TV Shows folders"
                    },
                )
                if (!isTv) {
                    Row(modifier = Modifier.padding(start = 34.dp, top = 6.dp, bottom = 8.dp)) {
                        val enabled = port != null && config.hasAccess
                        OutlinedButton(
                            onClick = openHere,
                            enabled = enabled,
                            modifier = Modifier.tapToClick(enabled, openHere),
                        ) { Text("Choose folders here") }
                    }
                    openHereError?.let { Warning(it) }
                }
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
                Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
                Button(
                    onClick = onDone,
                    enabled = config.isReady,
                    modifier = Modifier.focusRequester(doneFocus).tapToClick(config.isReady, onDone),
                ) {
                    Text(if (config.isReady) "Done — go to my library" else "Waiting for setup…")
                }
            }

            Column(
                modifier = Modifier
                    .width(maxOf(qrSize + 40.dp, 280.dp))
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val qr = remember(url) { url?.let { runCatching { qrBitmap(it) }.getOrNull() } }
                Box(
                    modifier = Modifier
                        .size(qrSize)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(if (compact) 10.dp else 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(bitmap = qr, contentDescription = "Setup QR code", modifier = Modifier.fillMaxSize())
                    } else {
                        Text(
                            serverError ?: when {
                                port == null -> "Starting…"
                                isTv -> "The TV isn't connected to your home network."
                                else -> "Not on Wi-Fi. Use \"Choose folders here\", or join Wi-Fi to use another phone."
                            },
                            color = Color.Black, fontSize = 15.sp, textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                Text(
                    "Scan to choose folders, or open this address on your phone:",
                    color = IamttMuted, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
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
}

@Composable
private fun Warning(text: String) {
    Text(
        "⚠ $text", color = Color(0xFFF5A524), fontSize = 16.sp,
        modifier = Modifier.padding(start = 34.dp, bottom = 8.dp),
    )
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
