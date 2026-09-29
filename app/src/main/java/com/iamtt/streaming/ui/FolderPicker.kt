package com.iamtt.streaming.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/**
 * Shows the phone setup page (served by [com.iamtt.streaming.setup.SetupServer] on this device)
 * inside the app. Opening it in a separate browser would put the app in the background, and
 * Android then cuts off its internet access, so Drive folders stop loading.
 */
@SuppressLint("SetJavaScriptEnabled") // It's the app's own page, served from this device.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FolderPicker(url: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IamttBackground)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Choose folders", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Button(onClick = onClose, modifier = Modifier.tapToClick(onClick = onClose)) { Text("Done") }
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient() // keep every page inside the app
                    setBackgroundColor(android.graphics.Color.parseColor("#0E0E10"))
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}
