package com.iamtt.streaming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Deep two-tone gradients; each title always gets the same one. */
private val PosterPalettes = listOf(
    Color(0xFF7B2CBF) to Color(0xFF1A0633),
    Color(0xFFC1121F) to Color(0xFF2B0507),
    Color(0xFF0F7C8C) to Color(0xFF031F24),
    Color(0xFF2A9D8F) to Color(0xFF08221F),
    Color(0xFFE76F51) to Color(0xFF301008),
    Color(0xFF3A86FF) to Color(0xFF08183D),
    Color(0xFFD4A017) to Color(0xFF2E2104),
    Color(0xFFEF476F) to Color(0xFF330816),
    Color(0xFF6A994E) to Color(0xFF142110),
    Color(0xFF8D99AE) to Color(0xFF1B1F27),
)

/**
 * Stand-in poster art until real posters arrive (Phase 2): a gradient picked from the title,
 * two soft lights, the title and a small IAMTT badge.
 */
@Composable
fun PosterArt(
    title: String,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 15.sp,
    titleBottomPadding: Dp = 10.dp,
    badge: Boolean = true,
) {
    val (light, dark) = PosterPalettes[(title.hashCode() and 0x7fffffff) % PosterPalettes.size]
    BoxWithConstraints(
        modifier = modifier
            .background(Brush.linearGradient(listOf(light, dark), start = Offset.Zero, end = Offset.Infinite))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.1f),
                        radius = size.maxDimension * 0.75f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(light.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.7f),
                        radius = size.maxDimension * 0.6f,
                    )
                )
                drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f)))
            },
    ) {
        if (badge) {
            IamttLogo(
                height = (maxWidth * 0.1f).coerceIn(9.dp, 26.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
        Text(
            title,
            color = Color.White,
            style = TextStyle(
                fontSize = titleSize,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = titleSize * 1.1f,
                shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 8f),
            ),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 9.dp, end = 9.dp, bottom = titleBottomPadding),
        )
    }
}

/** A thin red "how far you got" bar, like the one under Continue Watching posters. */
@Composable
fun WatchBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color(0x88FFFFFF)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .background(IamttRed),
        )
    }
}

/** How far into [item] the profile got, 0..1, when that's known. */
fun HomeItem.watchedFraction(): Float? {
    val p = progress ?: return null
    val total = p.durationMs.takeIf { it > 0 } ?: video.durationMs ?: return null
    return if (total > 0) (p.positionMs.toFloat() / total).coerceIn(0f, 1f) else null
}
