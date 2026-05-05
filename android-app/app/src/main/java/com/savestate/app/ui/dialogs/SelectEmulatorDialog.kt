package com.savestate.app.ui.dialogs

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.savestate.app.data.model.EmulatorInfo
import com.savestate.app.ui.theme.*

/**
 * Dialog to select an emulator when creating a new profile
 * Shows list of detected installed emulators
 */
@Composable
fun SelectEmulatorDialog(
    installedEmulators: List<EmulatorInfo>,
    isLoading: Boolean = false,
    isRootModeEnabled: Boolean = false,
    onEmulatorSelected: (EmulatorInfo) -> Unit,
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
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                DialogHeader(
                    title = "Select Emulator",
                    onClose = onDismiss
                )
                
                // Content
                if (isLoading) {
                    // Loading state
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
                            CircularProgressIndicator(
                                color = SaveStateRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Scanning for emulators...",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (installedEmulators.isEmpty()) {
                    // Empty state
                    EmptyEmulatorsState()
                } else {
                    // Emulator list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(installedEmulators) { emulator ->
                            // An emulator is disabled only when it strictly needs
                            // root AND the user has not enabled root mode AND it
                            // does not also support a manual SAF fallback.
                            val isDisabled = emulator.requiresRoot &&
                                !isRootModeEnabled &&
                                !emulator.supportsManualPath
                            EmulatorListItem(
                                emulator = emulator,
                                isDisabled = isDisabled,
                                onClick = {
                                    if (!isDisabled) onEmulatorSelected(emulator)
                                }
                            )
                        }
                        
                        // Bottom spacer
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog header with title and close button
 */
@Composable
private fun DialogHeader(
    title: String,
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
        Text(
            text = title,
            color = SaveStateRed,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
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
 * Empty state when no emulators are found
 */
@Composable
private fun EmptyEmulatorsState() {
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
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = TextMuted
            )
            
            Text(
                text = "No Emulators Found",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Install a supported emulator like PPSSPP, RetroArch, Dolphin, or DuckStation to create game profiles.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Individual emulator list item
 */
@Composable
private fun EmulatorListItem(
    emulator: EmulatorInfo,
    isDisabled: Boolean = false,
    onClick: () -> Unit
) {
    val contentAlpha = if (isDisabled) 0.45f else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isDisabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isDisabled) DarkSurfaceVariant.copy(alpha = 0.6f) else DarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDisabled) TextMuted.copy(alpha = 0.15f)
            else SaveStateRed.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emulator icon
            EmulatorIcon(
                icon = emulator.icon,
                displayName = emulator.displayName
            )
            
            // Emulator info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = emulator.displayName,
                        color = TextPrimary.copy(alpha = contentAlpha),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (emulator.requiresRoot) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDisabled) TextMuted.copy(alpha = 0.2f)
                                    else SaveStateRed.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = if (isDisabled) TextMuted
                                           else SaveStateRed
                                )
                                Text(
                                    text = "ROOT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDisabled) TextMuted
                                            else SaveStateRed
                                )
                            }
                        }
                    }
                }
                
                Text(
                    text = if (isDisabled) "Enable Root Mode in Settings"
                           else emulator.emulatorType.displayName,
                    color = if (isDisabled) TextMuted.copy(alpha = 0.7f)
                            else TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!isDisabled && emulator.defaultSavePaths.isNotEmpty()) {
                    Text(
                        text = emulator.defaultSavePaths.first(),
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Selection indicator
            if (!isDisabled) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SaveStateRed.copy(alpha = 0.6f))
                )
            }
        }
    }
}

/**
 * Emulator icon component - shows app icon if available, or fallback
 */
@Composable
private fun EmulatorIcon(
    icon: Drawable?,
    displayName: String
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkBackground)
            .border(1.dp, SaveStateRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon.toBitmap(48, 48).asImageBitmap(),
                contentDescription = displayName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            // Fallback icon with initials
            Text(
                text = displayName.take(2).uppercase(),
                color = SaveStateRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
