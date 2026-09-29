package com.iamtt.streaming.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * TV Material buttons and cards only react to the remote's OK button, not to touch.
 * This adds tapping, so the app also works on phones, tablets and touchscreen TVs.
 */
fun Modifier.tapToClick(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    if (enabled) pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) } else this
