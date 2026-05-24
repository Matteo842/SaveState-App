package com.savestate.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    compact: Boolean = false,
    /** Single header in landscape side rail (tighter padding) */
    forRail: Boolean = false
) {
    if (forRail) {
        Text(
            text = title,
            color = SaveStateRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = modifier.padding(start = 5.dp, top = 2.dp, bottom = 6.dp)
        )
    } else {
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
}

/**
 * Gamepad button badge (Xbox style: green A, red B, blue X, yellow Y)
 */
@Composable
fun GamepadBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    val bgColor = when (label.uppercase()) {
        "A" -> BadgeA
        "B" -> BadgeB
        "X" -> BadgeX
        "Y" -> BadgeY
        else -> BadgeDefault
    }
    Box(
        modifier = modifier
            .size(16.dp)
            .background(bgColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 9.sp
        )
    }
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
    dense: Boolean = false,
    /** Unified landscape rail column (fixed width, no scrolling) */
    railStyle: Boolean = false,
    railExtraDense: Boolean = false,
    gamepadBadge: String? = null,
    isFocused: Boolean = false
) {
    val buttonHeight = when {
        railStyle && railExtraDense -> 28.dp
        railStyle -> 31.dp
        dense && compact -> 34.dp
        compact -> 38.dp
        else -> 44.dp
    }
    val verticalPad = when {
        railStyle && railExtraDense -> 2.dp
        railStyle -> 3.dp
        dense && compact -> 4.dp
        else -> 8.dp
    }
    val iconSize = when {
        railStyle && railExtraDense -> 14.dp
        railStyle -> 15.dp
        dense && compact -> 16.dp
        else -> 18.dp
    }
    val fontSz = when {
        railStyle && railExtraDense -> 11.sp
        railStyle -> 11.5.sp
        dense && compact -> 12.sp
        else -> 13.sp
    }
    val horizontalPad = if (railStyle) {
        if (railExtraDense) 6.dp else 8.dp
    } else {
        10.dp
    }
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
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) {
                accentColor
            } else if (enabled) {
                if (isPrimary) accentColor else TextMuted
            } else {
                TextMuted.copy(alpha = 0.5f)
            }
        ),
        contentPadding = PaddingValues(horizontal = horizontalPad, vertical = verticalPad)
    ) {
        if (gamepadBadge != null) {
            GamepadBadge(label = gamepadBadge)
            Spacer(modifier = Modifier.width(if (railStyle) 3.dp else 4.dp))
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.width(if (railStyle) 3.dp else 4.dp))
        Text(
            text = text,
            fontSize = fontSz,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
    tightSpacing: Boolean = false,
    controllerMode: Boolean = false
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
                    gamepadBadge = if (controllerMode) "A" else null,
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
                    gamepadBadge = if (controllerMode) "X" else null,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("restore_btn")
                )
                SaveStateButton(
                    text = "Manage",
                    icon = Icons.Filled.Folder,
                    onClick = onManageBackupsClick,
                    enabled = hasProfileSelected,
                    compact = compact,
                    dense = dense,
                    gamepadBadge = if (controllerMode) "Y" else null,
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
                    gamepadBadge = if (controllerMode) "A" else null,
                    modifier = Modifier.weight(1f).tutorialTarget("backup_btn")
                )
                SaveStateButton(
                    text = "Restore",
                    icon = Icons.Filled.Restore,
                    onClick = onRestoreClick,
                    enabled = hasProfileSelected,
                    isPrimary = true,
                    accentColor = ButtonBlue,
                    gamepadBadge = if (controllerMode) "X" else null,
                    modifier = Modifier.weight(1f).tutorialTarget("restore_btn")
                )
                SaveStateButton(
                    text = "Manage",
                    icon = Icons.Filled.Folder,
                    onClick = onManageBackupsClick,
                    enabled = hasProfileSelected,
                    gamepadBadge = if (controllerMode) "Y" else null,
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
    tightSpacing: Boolean = false,
    controllerMode: Boolean = false
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
                    gamepadBadge = if (controllerMode) "St" else null,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("new_profile_btn")
                )
                SaveStateButton(
                    text = "Settings",
                    icon = Icons.Filled.Settings,
                    onClick = onSettingsClick,
                    compact = compact,
                    dense = dense,
                    gamepadBadge = if (controllerMode) "Bk" else null,
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
                    gamepadBadge = if (controllerMode) "St" else null,
                    modifier = Modifier.weight(1f).tutorialTarget("new_profile_btn")
                )
                SaveStateButton(
                    text = "Settings",
                    icon = Icons.Filled.Settings,
                    onClick = onSettingsClick,
                    gamepadBadge = if (controllerMode) "Bk" else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


/**
 * Landscape side column: Backup / Restore / Manage backups + New profile / Settings.
 * No vertical scroll — [extraDense] shrinks buttons when vertical space is tight.
 */
@Composable
fun LandscapeUnifiedActionRail(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onManageBackupsClick: () -> Unit,
    onNewProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    hasProfileSelected: Boolean,
    extraDense: Boolean,
    modifier: Modifier = Modifier,
    controllerMode: Boolean = false
) {
    val gap = if (extraDense) 2.dp else 4.dp
    val railWidth = if (extraDense) 144.dp else 154.dp
    Surface(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight(),
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, SaveStateRed.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (extraDense) 5.dp else 6.dp, vertical = if (extraDense) 4.dp else 6.dp)
        ) {
            SectionHeader(title = "Actions", compact = true, forRail = true)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                SaveStateButton(
                    text = "Backup",
                    icon = Icons.Filled.Save,
                    onClick = onBackupClick,
                    enabled = hasProfileSelected,
                    isPrimary = true,
                    accentColor = ButtonGreen,
                    railStyle = true,
                    railExtraDense = extraDense,
                    gamepadBadge = if (controllerMode) "A" else null,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("backup_btn")
                )
                SaveStateButton(
                    text = "Restore",
                    icon = Icons.Filled.Restore,
                    onClick = onRestoreClick,
                    enabled = hasProfileSelected,
                    isPrimary = true,
                    accentColor = ButtonBlue,
                    railStyle = true,
                    railExtraDense = extraDense,
                    gamepadBadge = if (controllerMode) "X" else null,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("restore_btn")
                )
                SaveStateButton(
                    text = "Manage backups",
                    icon = Icons.Filled.Folder,
                    onClick = onManageBackupsClick,
                    enabled = hasProfileSelected,
                    railStyle = true,
                    railExtraDense = extraDense,
                    gamepadBadge = if (controllerMode) "Y" else null,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("manage_btn")
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(color = DarkSurface, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = gap),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                SaveStateButton(
                    text = "New Profile...",
                    icon = Icons.Filled.Add,
                    onClick = onNewProfileClick,
                    isPrimary = true,
                    accentColor = SaveStateRed,
                    railStyle = true,
                    railExtraDense = extraDense,
                    gamepadBadge = if (controllerMode) "St" else null,
                    modifier = Modifier.fillMaxWidth().tutorialTarget("new_profile_btn")
                )
                SaveStateButton(
                    text = "Settings",
                    icon = Icons.Filled.Settings,
                    onClick = onSettingsClick,
                    railStyle = true,
                    railExtraDense = extraDense,
                    gamepadBadge = if (controllerMode) "Bk" else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

