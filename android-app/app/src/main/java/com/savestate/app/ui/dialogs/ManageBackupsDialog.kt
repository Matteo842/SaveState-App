package com.savestate.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savestate.app.data.BackupInfo

/**
 * Dialog for managing backups (delete only).
 */
@Composable
fun ManageBackupsDialog(
    profileName: String,
    backups: List<BackupInfo>,
    onDelete: (BackupInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf<BackupInfo?>(null) }
    
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
                    
                    IconButton(onClick = onDismiss) {
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
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(backups) { backup ->
                            ManageBackupItem(
                                backup = backup,
                                onDelete = { showDeleteConfirm = backup }
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
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .border(
                width = 1.dp,
                color = Color(0xFF30363d),
                shape = RoundedCornerShape(12.dp)
            )
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
        
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete backup",
                tint = Color(0xFFf85149)
            )
        }
    }
}
