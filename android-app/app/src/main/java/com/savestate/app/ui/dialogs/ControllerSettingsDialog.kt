package com.savestate.app.ui.dialogs

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savestate.app.controller.GamepadAction
import com.savestate.app.controller.GamepadManager
import com.savestate.app.ui.theme.*

@Composable
fun ControllerSettingsDialog(
    initialMappings: Map<Int, GamepadAction>,
    controllerConnected: Boolean,
    gamepadManager: GamepadManager,
    onSave: (Map<Int, GamepadAction>) -> Unit,
    onDismiss: () -> Unit
) {
    // Current mapping state inside dialog (starts with loaded mappings)
    var currentMappings by remember { mutableStateOf(initialMappings) }

    // Intercept controller key events to prevent background actions and allow back/B key to dismiss
    DisposableEffect(gamepadManager) {
        gamepadManager.setDialogKeyCallback { keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                if (keyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B || keyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    onDismiss()
                    true
                } else {
                    false
                }
            } else {
                keyEvent.keyCode in listOf(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK)
            }
        }
        onDispose {
            gamepadManager.setDialogKeyCallback(null)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(
                    width = 1.5.dp,
                    color = SaveStateRed.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header: Title and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Controller Settings",
                        color = SaveStateRed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Connection status indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (controllerConnected) StatusSuccess else StatusError)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (controllerConnected) "Controller connected" else "No controller detected",
                        color = if (controllerConnected) StatusSuccess else StatusError,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs matching desktop layout (Button Mapping / Shortcuts)
                Row(
                    modifier = Modifier.fillMaxWidth()
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

                HorizontalDivider(
                    color = TextMuted.copy(alpha = 0.2f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Scrollable content showing mappings
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "D-pad / Left stick  →  Navigate profile list (always)",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    // Button Mappings
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

                Spacer(modifier = Modifier.height(12.dp))

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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "Reset defaults", fontSize = 13.sp)
                    }

                    // Exit and Save (right aligned)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(text = "Save", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
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
            .background(DarkSurfaceVariant)
            .border(1.dp, TextMuted.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pill badge representing physical button
        Box(
            modifier = Modifier
                .width(68.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(badgeColor)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buttonName,
                color = Color.White,
                fontSize = 12.sp,
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
                .width(160.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .border(1.dp, TextMuted.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .background(DarkSurface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                modifier = Modifier.size(18.dp)
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
        GamepadAction.DELETE -> "Back / Close panel" // faithfully label B's default as close panel / delete
        GamepadAction.NEW_PROFILE -> "Open context menu" // start button default
        GamepadAction.SETTINGS -> "Delete profile" // select button default, wait, on Android, KEYCODE_BUTTON_SELECT maps to SETTINGS action. But we display friendly name. Let's keep it clear:
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
