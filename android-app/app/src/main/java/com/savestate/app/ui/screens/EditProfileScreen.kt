package com.savestate.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savestate.app.data.model.GameProfile
import com.savestate.app.ui.theme.*

/**
 * Full-screen profile editor.
 * Mirrors the desktop "Edit Profile" view. For now only the profile name is
 * editable — additional fields (icon, save path, per-profile overrides) will
 * be added here as features land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profile: GameProfile,
    onSave: (GameProfile) -> Unit,
    onCancel: () -> Unit,
    appVersion: String,
    modifier: Modifier = Modifier
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    val trimmedName = name.trim()
    val isDirty = trimmedName != profile.name
    val isValid = trimmedName.isNotEmpty()
    val canSave = isValid && isDirty

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        EditProfileTopBar(
            onBackClick = onCancel,
            appVersion = appVersion
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            EditProfileGroupHeader(title = "Edit Profile")

            Spacer(modifier = Modifier.height(16.dp))

            EditProfileSection(title = "Name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = !isValid,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = SaveStateRed,
                        unfocusedBorderColor = SaveStateRed.copy(alpha = 0.4f),
                        cursorColor = SaveStateRed,
                        errorBorderColor = StatusError,
                        errorTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        errorContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(4.dp)
                )

                if (!isValid) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Name cannot be empty",
                        color = StatusError,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            EditProfileSection(title = "Details") {
                ReadOnlyRow(label = "Emulator", value = profile.emulator)
                Spacer(modifier = Modifier.height(8.dp))
                ReadOnlyRow(label = "Save Path", value = profile.savePath)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        SaveStateRed.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        onSave(profile.copy(name = trimmedName))
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SaveStateRed,
                        contentColor = TextPrimary,
                        disabledContainerColor = DarkSurface,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Save",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileTopBar(
    onBackClick: () -> Unit,
    appVersion: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Text(
                text = "SaveState - $appVersion",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Edit Profile",
                color = SaveStateRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}

@Composable
private fun EditProfileGroupHeader(title: String) {
    Text(
        text = title,
        color = SaveStateRed,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun EditProfileSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = SaveStateRed.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
            color = DarkSurfaceVariant,
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                SaveStateRed.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurface)
                .border(
                    width = 1.dp,
                    color = TextMuted.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 13.sp
            )
        }
    }
}
