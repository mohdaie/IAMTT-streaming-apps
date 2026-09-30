package com.iamtt.streaming.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** True on Android TV / Google TV; phones and tablets get the touch layouts. */
fun Context.isTv(): Boolean {
    val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isTv() }
}
