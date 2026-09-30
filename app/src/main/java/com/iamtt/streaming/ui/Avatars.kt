package com.iamtt.streaming.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.Profile

/** A built-in profile picture: an animal (or thing) on a bold gradient tile. */
data class AvatarStyle(val emoji: String, val top: Color, val bottom: Color)

val Avatars = listOf(
    AvatarStyle("🦁", Color(0xFFFFB02E), Color(0xFFE5471B)),
    AvatarStyle("🐼", Color(0xFF7D8B99), Color(0xFF2C3440)),
    AvatarStyle("🦊", Color(0xFFFF7A45), Color(0xFFB8360F)),
    AvatarStyle("🐸", Color(0xFF7BD389), Color(0xFF1F8A4C)),
    AvatarStyle("🐧", Color(0xFF5AB2FF), Color(0xFF1E5AA8)),
    AvatarStyle("🦄", Color(0xFFF7A8FF), Color(0xFF8F4FD9)),
    AvatarStyle("🐙", Color(0xFFFF6B9A), Color(0xFFC0245C)),
    AvatarStyle("🐯", Color(0xFFFFD166), Color(0xFFF08A24)),
    AvatarStyle("🐶", Color(0xFFD9A066), Color(0xFF8A5528)),
    AvatarStyle("🐱", Color(0xFF9AD0EC), Color(0xFF3B7EA1)),
    AvatarStyle("🚀", Color(0xFF6A5CFF), Color(0xFF241A8C)),
    AvatarStyle("⚽", Color(0xFF3ED6A8), Color(0xFF0E7A5E)),
)

fun avatarShape(size: Dp) = RoundedCornerShape(size * 0.12f)

/** A profile's picture: their photo if they chose one, otherwise their built-in avatar. */
@Composable
fun ProfileAvatar(app: IamttApp, profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    val photo = remember(profile.id, profile.photoVersion) {
        if (profile.photoVersion == null) null else app.photos.load(profile.id)?.asImageBitmap()
    }
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = profile.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(avatarShape(size)),
        )
    } else {
        AvatarTile(Avatars[profile.avatar.mod(Avatars.size)], size, modifier)
    }
}

@Composable
fun AvatarTile(style: AvatarStyle, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(avatarShape(size))
            .background(Brush.linearGradient(listOf(style.top, style.bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(style.emoji, fontSize = (size.value * 0.52f).sp)
    }
}

/**
 * Clickable by touch and by a TV remote's OK button. On a TV the focused item grows a little
 * and gets a white outline, so it's clear where the remote is.
 */
@Composable
fun Modifier.pressable(shape: Shape, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    return this
        .graphicsLayer {
            val scale = if (focused) 1.06f else 1f
            scaleX = scale
            scaleY = scale
        }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
        .clip(shape)
        .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
}
