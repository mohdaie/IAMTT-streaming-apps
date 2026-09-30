package com.iamtt.streaming.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt

/** Screen brightness for this app only (not the system setting), and media volume, as 0..1. */
class BrightnessAndVolume(private val activity: Activity) {
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    fun brightness(): Float {
        val own = activity.window.attributes.screenBrightness
        if (own >= 0) return own
        // Following the system: start from its current level.
        return Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
    }

    fun setBrightness(value: Float) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = value.coerceIn(0.02f, 1f) }
    }

    /** Hands brightness back to the system when leaving the player. */
    fun resetBrightness() {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    fun volume(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume

    fun setVolume(value: Float) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value.coerceIn(0f, 1f) * maxVolume).roundToInt(), 0)
    }
}

/**
 * A tall slider like the brightness slider in streaming apps: drag up or down, or tap a spot.
 * [symbol] sits above it ("☀" or "🔊") and the level shows as a percentage.
 */
@Composable
fun LevelSlider(symbol: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    // The drag handler is started once, so it reads the latest level through this.
    val latest by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, fontSize = 20.sp, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(36.dp)
                .height(170.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x55FFFFFF))
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTapGestures { p -> if (size.height > 0) change(1f - p.y / size.height) }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        if (size.height > 0) change(latest - dy / size.height)
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .background(Color.White),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("${(value * 100).roundToInt()}%", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Vertical swipes on the video: left half for brightness, right half for volume. [onSwipe] gets
 * which one and how much to change it (a swipe over most of the screen covers the whole range).
 * Taps still reach the player (to show or hide its controls); a swipe doesn't count as a tap.
 */
fun swipeListener(context: Context, view: View, onSwipe: (which: String, delta: Float) -> Unit): View.OnTouchListener {
    var which: String? = null
    val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val start = e1 ?: return false
            if (which == null) {
                val dy = abs(e2.y - start.y)
                if (dy < slop || dy < abs(e2.x - start.x)) return false
                which = if (start.x < view.width / 2f) "brightness" else "volume"
            }
            onSwipe(which!!, distanceY / (view.height * 0.75f).coerceAtLeast(1f))
            return true
        }
    })
    return View.OnTouchListener { _, event ->
        detector.onTouchEvent(event)
        val swiping = which != null
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) which = null
        swiping
    }
}
