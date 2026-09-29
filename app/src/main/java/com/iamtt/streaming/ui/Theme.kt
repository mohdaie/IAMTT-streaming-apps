package com.iamtt.streaming.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val IamttRed = Color(0xFFE50914)
val IamttBackground = Color(0xFF0B0B0D)
val IamttSurface = Color(0xFF18181C)
val IamttMuted = Color(0xFF9A9AA3)

@Composable
fun IamttTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = IamttRed,
            onPrimary = Color.White,
            background = IamttBackground,
            onBackground = Color.White,
            surface = IamttSurface,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF222228),
            onSurfaceVariant = Color(0xFFD8D8DE),
        ),
        content = content,
    )
}
