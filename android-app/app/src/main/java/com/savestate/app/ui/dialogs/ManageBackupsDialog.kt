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
import androidx.compose.material.icons.filled.Delete
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
 * Dialog for managing backups (delete only).
 */
@Composable
fun ManageBackupsDialog(
    profileName: String,
    backups: List<BackupInfo>,
    gamepadManager: GamepadManager,
    onDelete: (BackupInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf<BackupInfo?>(null) }
    
    var focusedBackupIndex by remember { mutableStateOf(if (backups.isNotEmpty()) 0 else -1) }
    val listState = rememberLazyListState()
    val controllerMode by gamepadManager.controllerMode.collectAsState()
    
    DisposableEffect(gamepadManager, backups, focusedBackupIndex, showDeleteConfirm) {
        gamepadManager.setDialogKeyCallback { keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (backups.isNotEmpty()) {
                            focusedBackupIndex = (focusedBackupIndex + 1).coerceAtMost(backups.size - 1)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (backups.isNotEmpty()) {
                            focusedBackupIndex = (focusedBackupIndex - 1).coerceAtLeast(0)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        val currentConfirm = showDeleteConfirm
                        if (currentConfirm != null) {
                            onDelete(currentConfirm)
                            showDeleteConfirm = null
                        } else {
                            if (focusedBackupIndex in backups.indices) {
                                showDeleteConfirm = backups[focusedBackupIndex]
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                        if (showDeleteConfirm != null) {
                            showDeleteConfirm = null
                        } else {
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
    
    LaunchedEffect(focusedBackupIndex) {
        if (focusedBackupIndex in backups.indices) {
            listState.animateScrollToItem(focusedBackupIndex)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
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
                            text = "Manage Backups",
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
                        text = "Tap the delete icon to remove a backup:",
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
                            val index = backups.indexOf(backup)
                            ManageBackupItem(
                                backup = backup,
                                isFocused = index == focusedBackupIndex,
                                onDelete = { showDeleteConfirm = backup }
                            )
                        }
                    }
                }
                
                if (controllerMode) {
                    Spacer(modifier = Modifier.height(16.dp))
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
                                    .background(Color(0xFFf85149), shape = CircleShape),
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
                                text = "Delete",
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
                                    .background(Color(0xFF8b949e), shape = CircleShape),
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
                }
            }
        }
        
        // Delete confirmation dialog
        showDeleteConfirm?.let { backupToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("Delete Backup?") },
                text = { 
                    Text("Are you sure you want to delete this backup?\n\n${backupToDelete.fileName}\n\nThis action cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDelete(backupToDelete)
                            showDeleteConfirm = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFf85149)
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = Color(0xFF161b22),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFc9d1d9)
            )
        }
    }
}

@Composable
private fun ManageBackupItem(
    backup: BackupInfo,
    isFocused: Boolean = false,
    onDelete: () -> Unit
) {
    val borderColor = if (isFocused) Color(0xFFf85149) else Color(0xFF30363d)
    val backgroundColor = if (isFocused) Color(0xFF1f1515) else Color.Transparent
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .focusProperties { canFocus = false }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
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
        
        IconButton(
            onClick = onDelete,
            modifier = Modifier.focusProperties { canFocus = false }
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete backup",
                tint = Color(0xFFf85149)
            )
        }
    }
}
