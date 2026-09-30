package com.iamtt.streaming.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme as PhoneTheme
import androidx.compose.material3.darkColorScheme as phoneDarkColorScheme

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
    ) {
        // The phone screens are built from regular Material 3, so give them the same colours.
        PhoneTheme(
            colorScheme = phoneDarkColorScheme(
                primary = IamttRed,
                onPrimary = Color.White,
                background = IamttBackground,
                onBackground = Color.White,
                surface = IamttSurface,
                onSurface = Color.White,
                surfaceVariant = Color(0xFF222228),
                onSurfaceVariant = Color(0xFFB8B8C0),
                secondaryContainer = Color(0xFF2E2E36),
                onSecondaryContainer = Color.White,
            ),
            content = content,
        )
    }
}
