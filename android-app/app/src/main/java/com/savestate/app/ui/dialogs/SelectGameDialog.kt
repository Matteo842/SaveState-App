package com.savestate.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    isRootMode: Boolean = false,
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
                                text = if (isRootMode) "Scanning with root access..."
                                       else "Scanning folder...",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (detectedGames.isEmpty()) {
                    if (isRootMode) {
                        // Root mode empty: scan found nothing — offer manual
                        // SAF fallback if the emulator supports a custom save
                        // path (e.g. Eden's custom save-data location).
                        RootEmptyState(
                            emulatorName = emulator.emulatorType.displayName,
                            supportsManualPath = emulator.supportsManualPath,
                            onBrowseFolder = onBrowseFolder
                        )
                    } else {
                        // Normal mode: ask user to select folder
                        SelectFolderPrompt(
                            emulatorName = emulator.emulatorType.displayName,
                            onBrowseFolder = onBrowseFolder
                        )
                    }
                } else {
                    // Games list with subtitle
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Found ${detectedGames.size} game(s). Select one to add:",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            lineHeight = 18.sp
                        )
                        
                        LazyColumn(
                            modifier = Modifier
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
                }
                
                // Bottom buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
 * Prompt to select the SAVEDATA folder - clear and direct
 */
@Composable
private fun SelectFolderPrompt(
    emulatorName: String,
    onBrowseFolder: () -> Unit
) {
    val isRetroArch = emulatorName == "RetroArch"
    val isDolphin = emulatorName == "Dolphin"
    val isEden = emulatorName == "Eden"
    
    // Get folder structure based on emulator
    val folderPath = when (emulatorName) {
        "PPSSPP" -> "PSP/SAVEDATA"
        "RetroArch" -> "RetroArch/saves  or  RetroArch/states"
        "Dolphin" -> "dolphin-emu"
        "DuckStation" -> "duckstation/memcards"
        "M64Plus FZ" -> "M64Plus FZ  (external data folder)"
        "Citra" -> "citra-emu/sdmc"
        "Azahar" -> "azahar-emu/sdmc"
        "DraStic" -> "DraStic/backup"
        "Flycast" -> "flycast/data"
        "mGBA" -> "mGBA/saves"
        "Lemuroid" -> "Lemuroid/saves"
        "Pizza Boy" -> "PizzaBoy/saves"
        "AetherSX2" -> "AetherSX2/memcards"
        "Vita3K" -> "Vita3K/ux0/user"
        "Yuzu" -> "yuzu/nand/user/save"
        "Citron" -> "citron/nand/user/save"
        "Eden" -> "Eden/nand/user/save"
        else -> "emulator/saves"
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f, fill = false))
        
        // Icon
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = SaveStateRed
        )
        
        // Title
        Text(
            text = "Select Save Folder",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Instructions
        Text(
            text = "Navigate to your $emulatorName save folder:",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        
        // Path
        Text(
            text = folderPath,
            color = Color(0xFF4CAF50),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        // RetroArch-specific hint
        if (isRetroArch) {
            Text(
                text = "Select \"saves\" for battery saves (.srm) or \"states\" for save states",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
        
        // Dolphin-specific hint
        if (isDolphin) {
            Text(
                text = "Select the root folder or a subfolder:\n• GC → GameCube memory card saves\n• Wii → Wii NAND saves\n• StateSaves → save states",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
        
        // Big prominent button — FIRST, always visible
        Button(
            onClick = onBrowseFolder,
            colors = ButtonDefaults.buttonColors(
                containerColor = SaveStateRed
            ),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Select Save Folder",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Important warning — at the bottom, less prominent
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = DarkSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Text(
                    text = when {
                        isRetroArch -> "Make sure RetroArch saves to an accessible folder (e.g. Internal Storage/RetroArch), not the default Android/data location."
                        isDolphin -> "Make sure Dolphin's user directory is set to an accessible folder (e.g. Internal Storage/dolphin-emu)."
                        isEden -> "Without root, Eden must be configured with a custom save path: open Eden → Settings → System → Storage and set a save data location outside Android/data, then pick that folder here."
                        else -> "If you haven't set a custom save folder in your emulator, the default Android/data location may not be accessible."
                    },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Shown when a root scan completes but finds no saves.
 * If [supportsManualPath] is true, also offers a SAF "Browse manually" button
 * so the user can locate a custom save folder configured inside the emulator.
 */
@Composable
private fun RootEmptyState(
    emulatorName: String,
    supportsManualPath: Boolean = false,
    onBrowseFolder: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f, fill = false))

        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = TextMuted
        )

        Text(
            text = "No Saves Found",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (supportsManualPath) {
                "No save data was found in $emulatorName's default folders.\n" +
                    "If you set a custom save path inside $emulatorName, " +
                    "browse to that folder manually below."
            } else {
                "No save data was found in $emulatorName's protected folders.\n" +
                    "Make sure the emulator has been launched at least once and has save data."
            },
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        if (supportsManualPath) {
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onBrowseFolder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SaveStateRed
                ),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Browse Save Folder",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
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
