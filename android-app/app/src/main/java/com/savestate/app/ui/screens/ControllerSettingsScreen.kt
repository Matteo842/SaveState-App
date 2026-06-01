package com.savestate.app.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savestate.app.controller.GamepadAction
import com.savestate.app.ui.theme.*

/**
 * Controller Settings screen for SaveState Android app.
 * Styled as a standalone screen matching the Fluent design guidelines.
 */
@Composable
fun ControllerSettingsScreen(
    initialMappings: Map<Int, GamepadAction>,
    controllerConnected: Boolean,
    onSave: (Map<Int, GamepadAction>) -> Unit,
    onBackClick: () -> Unit,
    appVersion: String,
    modifier: Modifier = Modifier
) {
    // Current mapping state inside screen (starts with loaded mappings)
    var currentMappings by remember { mutableStateOf(initialMappings) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        // Top bar with back button, screen title, and controller status on far right
        ControllerSettingsTopBar(
            onBackClick = onBackClick,
            controllerConnected = controllerConnected,
            appVersion = appVersion
        )

        // Screen content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Main Button Mapping Section
            SettingsSection(title = "Button Mapping Settings") {
                // Custom Tab simulation inside the container
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    // Button Mapping Tab (Active)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Button Mapping",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(2.5.dp)
                                .background(ButtonBlue)
                        )
                    }

                    // Shortcuts Tab (Inactive / Disabled)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Shortcuts",
                            color = TextMuted.copy(alpha = 0.5f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Text(
                    text = "D-pad / Left stick  →  Navigate profile list (always)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )

                // Button Mappings rows
                val mappingsList = listOf(
                    MappingItemInfo("A", BadgeA, KeyEvent.KEYCODE_BUTTON_A),
                    MappingItemInfo("B", BadgeB, KeyEvent.KEYCODE_BUTTON_B),
                    MappingItemInfo("X", BadgeX, KeyEvent.KEYCODE_BUTTON_X),
                    MappingItemInfo("Y", BadgeY, KeyEvent.KEYCODE_BUTTON_Y),
                    MappingItemInfo("Start", BadgeA, KeyEvent.KEYCODE_BUTTON_START),
                    MappingItemInfo("Select", BadgeDefault, KeyEvent.KEYCODE_BUTTON_SELECT),
                    MappingItemInfo("LB", Color(0xFF6A1B9A), KeyEvent.KEYCODE_BUTTON_L1),
                    MappingItemInfo("RB", Color(0xFF6A1B9A), KeyEvent.KEYCODE_BUTTON_R1)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mappingsList.forEach { item ->
                        val currentAction = currentMappings[item.keyCode] ?: GamepadAction.NONE
                        MappingRow(
                            buttonName = item.name,
                            badgeColor = item.badgeColor,
                            currentAction = currentAction,
                            onActionSelected = { newAction ->
                                currentMappings = currentMappings.toMutableMap().apply {
                                    put(item.keyCode, newAction)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Footer Buttons (Reset defaults, Exit, Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset defaults (left aligned)
                    OutlinedButton(
                        onClick = {
                            currentMappings = mapOf(
                                KeyEvent.KEYCODE_BUTTON_A to GamepadAction.BACKUP,
                                KeyEvent.KEYCODE_BUTTON_B to GamepadAction.DELETE,
                                KeyEvent.KEYCODE_BUTTON_X to GamepadAction.RESTORE,
                                KeyEvent.KEYCODE_BUTTON_Y to GamepadAction.MANAGE_BACKUPS,
                                KeyEvent.KEYCODE_BUTTON_START to GamepadAction.NEW_PROFILE,
                                KeyEvent.KEYCODE_BUTTON_SELECT to GamepadAction.SETTINGS,
                                KeyEvent.KEYCODE_BUTTON_L1 to GamepadAction.PAGE_UP,
                                KeyEvent.KEYCODE_BUTTON_R1 to GamepadAction.PAGE_DOWN
                            )
                        },
                        border = BorderStroke(1.dp, Color(0xFFD84315)), // custom dark orange border
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7043)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = "Reset defaults", fontSize = 13.sp)
                    }

                    // Exit and Save (right aligned)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = onBackClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(text = "Exit", fontSize = 14.sp)
                        }

                        Button(
                            onClick = {
                                onSave(currentMappings)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(text = "Save", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ControllerSettingsTopBar(
    onBackClick: () -> Unit,
    controllerConnected: Boolean,
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
            // Back button
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            // Title
            Text(
                text = "SaveState - $appVersion",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Controller Settings label and status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text(
                    text = "Controller Settings",
                    color = SaveStateRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(16.dp))
                // Connection status dot and text
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (controllerConnected) StatusSuccess else StatusError)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (controllerConnected) "Connected" else "Not connected",
                    color = if (controllerConnected) StatusSuccess else StatusError,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
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
            border = BorderStroke(1.dp, SaveStateRed.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun MappingRow(
    buttonName: String,
    badgeColor: Color,
    currentAction: GamepadAction,
    onActionSelected: (GamepadAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(DarkSurface)
            .border(1.dp, TextMuted.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pill badge representing physical button
        Box(
            modifier = Modifier
                .width(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(badgeColor)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buttonName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Action dropdown selector
        ActionDropdown(
            currentAction = currentAction,
            onActionSelected = onActionSelected
        )
    }
}

@Composable
private fun ActionDropdown(
    currentAction: GamepadAction,
    onActionSelected: (GamepadAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .width(180.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .border(1.dp, TextMuted.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .background(DarkSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getActionDisplayName(currentAction),
                color = TextPrimary,
                fontSize = 13.sp
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(DarkSurfaceVariant)
                .border(1.dp, TextMuted.copy(alpha = 0.2f))
        ) {
            val eligibleActions = listOf(
                GamepadAction.BACKUP,
                GamepadAction.RESTORE,
                GamepadAction.MANAGE_BACKUPS,
                GamepadAction.DELETE,
                GamepadAction.NEW_PROFILE,
                GamepadAction.SETTINGS,
                GamepadAction.PAGE_UP,
                GamepadAction.PAGE_DOWN,
                GamepadAction.NONE
            )
            eligibleActions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = getActionDisplayName(action),
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onActionSelected(action)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun getActionDisplayName(action: GamepadAction): String {
    return when (action) {
        GamepadAction.BACKUP -> "Backup"
        GamepadAction.RESTORE -> "Restore"
        GamepadAction.MANAGE_BACKUPS -> "Manage Backups"
        GamepadAction.DELETE -> "Back / Close panel"
        GamepadAction.NEW_PROFILE -> "Open context menu"
        GamepadAction.SETTINGS -> "Delete profile"
        GamepadAction.PAGE_UP -> "Page up"
        GamepadAction.PAGE_DOWN -> "Page down"
        GamepadAction.NONE -> "None"
        GamepadAction.NAV_UP -> "Navigate up"
        GamepadAction.NAV_DOWN -> "Navigate down"
    }
}

private data class MappingItemInfo(
    val name: String,
    val badgeColor: Color,
    val keyCode: Int
)
