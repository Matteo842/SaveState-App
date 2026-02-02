package com.savestate.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.EmulatorInfo
import com.savestate.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog to select a game from the detected games list
 * Shows after user selects an emulator
 */
@Composable
fun SelectGameDialog(
    emulator: EmulatorInfo,
    detectedGames: List<DetectedGame>,
    isLoading: Boolean = false,
    onGameSelected: (DetectedGame) -> Unit,
    onBrowseFolder: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                GameDialogHeader(
                    emulatorName = emulator.emulatorType.displayName,
                    onClose = onDismiss
                )
                
                // Subtitle
                Text(
                    text = "The following profiles/games have been found for ${emulator.emulatorType.displayName}.\nSelect the one to add:",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    lineHeight = 18.sp
                )
                
                // Content
                if (isLoading) {
                    // Loading state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = SaveStateRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Scanning for games...",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (detectedGames.isEmpty()) {
                    // Empty state with browse option
                    EmptyGamesState(
                        emulatorName = emulator.emulatorType.displayName,
                        onBrowseFolder = onBrowseFolder
                    )
                } else {
                    // Games list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(detectedGames.sortedBy { it.gameName }) { game ->
                            GameListItem(
                                game = game,
                                onClick = { onGameSelected(game) }
                            )
                        }
                    }
                }
                
                // Bottom buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Browse folder button
                    OutlinedButton(
                        onClick = onBrowseFolder,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SaveStateRed
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SaveStateRed.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse...")
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

/**
 * Dialog header with emulator name
 */
@Composable
private fun GameDialogHeader(
    emulatorName: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = null,
                tint = SaveStateRed,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Select $emulatorName Game",
                color = SaveStateRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = TextSecondary
            )
        }
    }
    
    HorizontalDivider(color = SaveStateRed.copy(alpha = 0.3f))
}

/**
 * Empty state when no games are found - with browse option
 */
@Composable
private fun EmptyGamesState(
    emulatorName: String,
    onBrowseFolder: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = TextMuted
            )
            
            Text(
                text = "No Games Found",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "No save data was found for $emulatorName.\n\nOn Android 11+, we may not have access to the app's private folder. Use the \"Browse\" button below to manually select the SAVEDATA folder.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Browse button
            Button(
                onClick = onBrowseFolder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SaveStateRed
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select SAVEDATA Folder")
            }
        }
    }
}

/**
 * Individual game list item
 */
@Composable
private fun GameListItem(
    game: DetectedGame,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Game icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SaveStateRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Gamepad,
                    contentDescription = null,
                    tint = SaveStateRed,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Game info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = game.gameName,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Game ID (if different from name)
                    if (game.gameId != game.gameName) {
                        Text(
                            text = game.gameId,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    
                    // Last modified
                    if (game.lastModified > 0) {
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        Text(
                            text = dateFormat.format(Date(game.lastModified)),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            // Selection dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SaveStateRed.copy(alpha = 0.6f))
            )
        }
    }
}
