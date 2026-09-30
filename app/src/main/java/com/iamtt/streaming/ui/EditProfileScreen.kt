package com.iamtt.streaming.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iamtt.streaming.IamttApp
import com.iamtt.streaming.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Creates or edits a profile: a name and a picture (a built-in avatar or a photo).
 * [existing] null means a new profile. [onCancel] null hides Cancel (the very first profile).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    app: IamttApp,
    existing: Profile?,
    onSaved: (Profile) -> Unit,
    onDeleted: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val isTv = rememberIsTv()
    val profileId = remember { existing?.id ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    // A new profile starts on a different avatar from the ones already taken.
    var avatar by remember {
        mutableStateOf(existing?.avatar ?: (Avatars.indices.firstOrNull { i -> app.config.current.profiles.none { it.avatar == i } } ?: 0))
    }
    var usePhoto by remember { mutableStateOf(existing?.photoVersion != null) }
    var pickedPhoto by remember { mutableStateOf<ImageBitmap?>(null) }
    val savedPhoto = remember { if (existing?.photoVersion != null) app.photos.load(profileId)?.asImageBitmap() else null }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            try {
                pickedPhoto = withContext(Dispatchers.IO) { app.photos.savePending(uri).asImageBitmap() }
                usePhoto = true
            } catch (e: Exception) {
                error = e.message ?: "Couldn't use that picture."
            }
        }
    }

    fun save() {
        val version = when {
            !usePhoto -> null
            pickedPhoto != null -> System.currentTimeMillis()
            else -> existing?.photoVersion
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                if (pickedPhoto != null && usePhoto) app.photos.commitPending(profileId) else app.photos.discardPending()
                if (version == null) app.photos.delete(profileId)
            }
            val profile = Profile(id = profileId, name = name.trim(), avatar = avatar, photoVersion = version)
            app.config.saveProfile(profile)
            onSaved(profile)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IamttBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onCancel != null) {
                IconButton(onClick = { app.photos.discardPending(); onCancel() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
                }
            }
            Text(
                when {
                    existing != null -> "Edit profile"
                    onCancel == null -> "Create your profile"
                    else -> "Add profile"
                },
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onCancel == null) 4.dp else 0.dp),
            )
            TextButton(onClick = ::save, enabled = name.isNotBlank()) {
                Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(20.dp))

        val preview = pickedPhoto ?: savedPhoto
        if (usePhoto && preview != null) {
            Image(
                bitmap = preview, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(128.dp)
                    .clip(avatarShape(128.dp)),
            )
        } else {
            AvatarTile(Avatars[avatar], 128.dp)
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        )
        error?.let { Text("⚠ $it", color = Color(0xFFF5A524), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }

        Spacer(Modifier.height(28.dp))
        Text("Choose a picture", color = Color(0xFFB8B8C0), fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Phones and tablets can use a photo from the gallery; TVs have no gallery to pick from.
            if (!isTv) {
                val shape = avatarShape(64.dp)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(if (usePhoto) 3.dp else 1.dp, if (usePhoto) Color.White else Color(0xFF55555E), shape)
                        .pressable(shape) {
                            try {
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } catch (e: ActivityNotFoundException) {
                                error = "No photo picker on this device."
                            }
                        }
                        .background(Color(0xFF1C1C21)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AccountBox, contentDescription = null, tint = Color(0xFFD8D8DE))
                        Text("Photo", color = Color(0xFFD8D8DE), fontSize = 11.sp)
                    }
                }
            }
            Avatars.forEachIndexed { i, style ->
                val selected = !usePhoto && avatar == i
                val shape = avatarShape(64.dp)
                AvatarTile(
                    style, 64.dp,
                    Modifier
                        .border(if (selected) 3.dp else 0.dp, if (selected) Color.White else Color.Transparent, shape)
                        .pressable(shape) {
                            avatar = i
                            usePhoto = false
                        },
                )
            }
        }

        if (existing != null) {
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1215), contentColor = Color(0xFFFF8A90)),
            ) { Text("Delete profile") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("Their picture and Continue Watching will be removed. Your videos stay in Google Drive.") },
            confirmButton = {
                TextButton(onClick = {
                    app.config.deleteProfile(existing.id)
                    app.photos.delete(existing.id)
                    app.photos.discardPending()
                    app.history.forgetProfile(existing.id)
                    onDeleted()
                }) { Text("Delete", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
