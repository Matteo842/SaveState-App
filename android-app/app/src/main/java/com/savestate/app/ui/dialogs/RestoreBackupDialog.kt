package com.savestate.app.ui.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savestate.app.controller.GamepadManager
import com.savestate.app.data.BackupInfo

/**
 * Dialog for selecting a backup to restore from.
 */
@Composable
fun RestoreBackupDialog(
    profileName: String,
    backups: List<BackupInfo>,
    isRestoring: Boolean,
    gamepadManager: GamepadManager,
    onRestore: (BackupInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBackup by remember { mutableStateOf(if (backups.isNotEmpty()) backups.first() else null) }
    
    val listState = rememberLazyListState()
    val controllerMode by gamepadManager.controllerMode.collectAsState()
    
    DisposableEffect(gamepadManager, backups, isRestoring) {
        gamepadManager.setDialogKeyCallback { keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (backups.isNotEmpty()) {
                            val currentIndex = backups.indexOf(selectedBackup)
                            val nextIndex = (currentIndex + 1).coerceAtMost(backups.size - 1)
                            selectedBackup = backups[nextIndex]
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (backups.isNotEmpty()) {
                            val currentIndex = backups.indexOf(selectedBackup)
                            val prevIndex = if (currentIndex == -1) 0 else (currentIndex - 1).coerceAtLeast(0)
                            selectedBackup = backups[prevIndex]
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        if (selectedBackup != null && !isRestoring) {
                            onRestore(selectedBackup!!)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                        if (!isRestoring) {
                            onDismiss()
                        }
                        true
                    }
                    else -> false
                }
            } else {
                keyEvent.keyCode in listOf(
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK
                )
            }
        }
        onDispose {
            gamepadManager.setDialogKeyCallback(null)
        }
    }
    
    LaunchedEffect(selectedBackup) {
        selectedBackup?.let { backup ->
            val index = backups.indexOf(backup)
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }
    
    Dialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isRestoring,
            dismissOnClickOutside = !isRestoring
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1a1f2e),
                            Color(0xFF0d1117)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF3d4663),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Restore Backup",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = profileName,
                            fontSize = 14.sp,
                            color = Color(0xFF8b949e)
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isRestoring,
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF8b949e)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content
                if (backups.isEmpty()) {
                    // No backups available
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📦",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No backups available",
                                fontSize = 18.sp,
                                color = Color(0xFF8b949e)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create a backup first using the Backup button",
                                fontSize = 14.sp,
                                color = Color(0xFF6e7681),
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                } else {
                    // Backup list
                    Text(
                        text = "Select a backup to restore:",
                        fontSize = 14.sp,
                        color = Color(0xFF8b949e),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(backups) { backup ->
                            BackupItem(
                                backup = backup,
                                isSelected = selectedBackup == backup,
                                isEnabled = !isRestoring,
                                onSelect = { selectedBackup = backup }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (controllerMode) {
                        // Premium controller navigation hints
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(Color(0xFF161b22), shape = RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF3d4663), shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFF238636), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (isRestoring) "Restoring..." else "Restore",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFFf85149), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "B",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Back",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        // Restore button
                        Button(
                            onClick = { 
                                selectedBackup?.let { onRestore(it) }
                            },
                            enabled = selectedBackup != null && !isRestoring,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .focusProperties { canFocus = false },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF238636),
                                disabledContainerColor = Color(0xFF21262d)
                            )
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Restoring...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Restore Selected Backup",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupItem(
    backup: BackupInfo,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF238636) else Color(0xFF30363d)
    val backgroundColor = if (isSelected) Color(0xFF0d1117) else Color.Transparent
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = isEnabled) { onSelect() }
            .focusProperties { canFocus = false }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = backup.formattedDate,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = backup.formattedSize,
                    fontSize = 13.sp,
                    color = Color(0xFF8b949e)
                )
                Text(
                    text = backup.fileName,
                    fontSize = 13.sp,
                    color = Color(0xFF6e7681),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
