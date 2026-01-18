package com.savestate.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savestate.app.ui.theme.*

/**
 * Section header matching SaveState desktop style
 * Red text with subtle styling like "Profiles", "Actions", "General"
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        color = SaveStateRed,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = 8.dp, top = 12.dp, bottom = 6.dp)
    )
}

/**
 * Action button matching SaveState desktop style
 * Used for Backup, Restore, Manage Backups buttons
 */
@Composable
fun SaveStateButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    accentColor: Color = SaveStateRed
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isPrimary) accentColor.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (isPrimary) accentColor else TextPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextMuted
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                if (isPrimary) accentColor else TextMuted
            } else {
                TextMuted.copy(alpha = 0.5f)
            }
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Actions section with Backup, Restore, Manage Backups buttons
 * Matches desktop app "Actions" section layout
 */
@Composable
fun ActionsSection(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onManageBackupsClick: () -> Unit,
    hasProfileSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "Actions")
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Backup button - matching desktop green/primary style
            SaveStateButton(
                text = "Backup",
                icon = Icons.Filled.Save,
                onClick = onBackupClick,
                enabled = hasProfileSelected,
                isPrimary = true,
                accentColor = ButtonGreen,
                modifier = Modifier.weight(1f)
            )
            
            // Restore button - matching desktop style
            SaveStateButton(
                text = "Restore",
                icon = Icons.Filled.Restore,
                onClick = onRestoreClick,
                enabled = hasProfileSelected,
                isPrimary = true,
                accentColor = ButtonBlue,
                modifier = Modifier.weight(1f)
            )
            
            // Manage Backups button
            SaveStateButton(
                text = "Manage",
                icon = Icons.Filled.Folder,
                onClick = onManageBackupsClick,
                enabled = hasProfileSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * General section with New Profile and Settings buttons
 * Matches desktop app "General" section
 */
@Composable
fun GeneralSection(
    onNewProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "General")
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // New Profile button
            SaveStateButton(
                text = "New Profile...",
                icon = Icons.Filled.Add,
                onClick = onNewProfileClick,
                isPrimary = true,
                accentColor = SaveStateRed,
                modifier = Modifier.weight(1f)
            )
            
            // Settings button
            SaveStateButton(
                text = "Settings",
                icon = Icons.Filled.Settings,
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
