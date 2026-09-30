package com.iamtt.streaming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.Profile
import kotlinx.coroutines.delay

/** How many profiles one install can have, like most streaming apps. */
const val MAX_PROFILES = 6

/** "Who's watching?": pick a profile, add one, or switch to managing them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(
    app: IamttApp,
    onPick: (Profile) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Profile) -> Unit,
) {
    val config by app.config.config.collectAsStateWithLifecycle()
    val profiles = config.profiles
    var managing by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0A0C), IamttBackground, Color.Black)))
            .safeDrawingPadding(),
    ) {
        // Three across on a phone held upright; bigger tiles on tablets and TVs.
        val tile: Dp = when {
            maxWidth < 360.dp -> 88.dp
            maxWidth < 600.dp -> 100.dp
            else -> 124.dp
        }
        val tall = maxHeight > 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IamttLogo(height = 44.dp)
            Spacer(Modifier.height(if (tall) 72.dp else 24.dp))
            Text(
                if (managing) "Manage profiles" else "Who's watching?",
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(28.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                profiles.forEachIndexed { i, profile ->
                    ProfileItem(
                        name = profile.name,
                        tile = tile,
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { if (managing) onEdit(profile) else onPick(profile) },
                    ) {
                        ProfileAvatar(app, profile, tile)
                        if (managing) {
                            Box(
                                Modifier
                                    .size(tile)
                                    .background(Color(0x99000000)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(tile * 0.34f))
                            }
                        }
                    }
                }
                if (profiles.size < MAX_PROFILES) {
                    ProfileItem(
                        name = "Add profile",
                        tile = tile,
                        modifier = if (profiles.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = onAdd,
                    ) {
                        Box(
                            Modifier
                                .size(tile)
                                .border(2.dp, Color(0xFF55555E), avatarShape(tile))
                                .background(Color(0xFF1C1C21), avatarShape(tile)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFFB8B8C0), modifier = Modifier.size(tile * 0.42f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            if (profiles.isNotEmpty()) {
                TextButton(onClick = { managing = !managing }) {
                    Icon(if (managing) Icons.Filled.Check else Icons.Filled.Edit, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (managing) "Done" else "Manage profiles", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileItem(
    name: String,
    tile: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    picture: @Composable () -> Unit,
) {
    // The picture and the name are one tap target; the focus highlight goes on the picture.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = avatarShape(tile)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(tile + 8.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val scale = if (focused) 1.08f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
                .clip(shape)
                .indication(interaction, ripple()),
        ) { picture() }
        Spacer(Modifier.height(8.dp))
        Text(
            name, color = if (focused) Color.White else Color(0xFFD8D8DE), fontSize = 15.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
