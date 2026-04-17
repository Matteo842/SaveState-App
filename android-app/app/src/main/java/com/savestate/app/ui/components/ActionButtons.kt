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
import com.savestate.app.ui.tutorial.tutorialTarget

/**
 * Section header matching SaveState desktop style
 * Red text with subtle styling like "Profiles", "Actions", "General"
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val top = if (compact) 4.dp else 12.dp
    val bottom = if (compact) 2.dp else 6.dp
    Text(
        text = title,
        color = SaveStateRed,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = 8.dp, top = top, bottom = bottom)
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
    accentColor: Color = SaveStateRed,
    compact: Boolean = false,
    /** Narrower height for dense landscape side rail */
    dense: Boolean = false
) {
    val buttonHeight = when {
        dense && compact -> 34.dp
        compact -> 38.dp
        else -> 44.dp
    }
    val verticalPad = if (dense && compact) 4.dp else 8.dp
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(buttonHeight),
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
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = verticalPad)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (dense && compact) 16.dp else 18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = if (dense && compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
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
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    stackVertically: Boolean = false,
    tightSpacing: Boolean = false
) {
    val gap = if (tightSpacing) 4.dp else 6.dp
    val dense = tightSpacing
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "Actions", compact = compact)

        if (stackVertically) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (tightSpacing) 4.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                SaveStateButton(
                    text = "Backup",
                    icon = Icons.Filled.Save,
                    onClick = onBackupClick,
                    enabled = hasProfileSelected,
                    isPrimary = true,
                    accentColor = ButtonGreen,
                    compact = compact,
                    dense = dense,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("backup_btn")
                )
                SaveStateButton(
                    text = "Restore",
                    icon = Icons.Filled.Restore,
                    onClick = onRestoreClick,
                    enabled = hasProfileSelected,
                    isPrimary = true,
                    accentColor = ButtonBlue,
                    compact = compact,
                    dense = dense,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("restore_btn")
                )
                SaveStateButton(
                    text = "Manage",
                    icon = Icons.Filled.Folder,
                    onClick = onManageBackupsClick,
                    enabled = hasProfileSelected,
                    compact = compact,
                    dense = dense,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("manage_btn")
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SaveStateButton(
                    text = "Backup",
                    icon = Icons.Filled.Save,
                    onClick = onBackupClick,
                    enabled = hasProfileSelected,
                    isPrimary = true,
                    accentColor = ButtonGreen,
                    modifier = Modifier.weight(1f).tutorialTarget("backup_btn")
                )
                SaveStateButton(
                    text = "Restore",
                    icon = Icons.Filled.Restore,
                    onClick = onRestoreClick,
                    enabled = hasProfileSelected,
                    isPrimary = true,
                    accentColor = ButtonBlue,
                    modifier = Modifier.weight(1f).tutorialTarget("restore_btn")
                )
                SaveStateButton(
                    text = "Manage",
                    icon = Icons.Filled.Folder,
                    onClick = onManageBackupsClick,
                    enabled = hasProfileSelected,
                    modifier = Modifier.weight(1f).tutorialTarget("manage_btn")
                )
            }
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
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    stackVertically: Boolean = false,
    tightSpacing: Boolean = false
) {
    val gap = if (tightSpacing) 4.dp else 6.dp
    val dense = tightSpacing
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "General", compact = compact)

        if (stackVertically) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (tightSpacing) 4.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                SaveStateButton(
                    text = "New Profile...",
                    icon = Icons.Filled.Add,
                    onClick = onNewProfileClick,
                    isPrimary = true,
                    accentColor = SaveStateRed,
                    compact = compact,
                    dense = dense,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("new_profile_btn")
                )
                SaveStateButton(
                    text = "Settings",
                    icon = Icons.Filled.Settings,
                    onClick = onSettingsClick,
                    compact = compact,
                    dense = dense,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SaveStateButton(
                    text = "New Profile...",
                    icon = Icons.Filled.Add,
                    onClick = onNewProfileClick,
                    isPrimary = true,
                    accentColor = SaveStateRed,
                    modifier = Modifier.weight(1f).tutorialTarget("new_profile_btn")
                )
                SaveStateButton(
                    text = "Settings",
                    icon = Icons.Filled.Settings,
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
