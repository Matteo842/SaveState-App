package com.savestate.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savestate.app.data.BackupDirectoryInfo
import com.savestate.app.ui.theme.*

/**
 * Settings screen for SaveState Android app.
 * Styled to match the desktop application's settings dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentBackupPath: String,
    backupInfo: BackupDirectoryInfo?,
    isMigrating: Boolean,
    migrationProgress: Pair<Int, Int>?, // (current, total)
    maxBackupsPerProfile: Int,
    onMaxBackupsChange: (Int) -> Unit,
    onBackClick: () -> Unit,
    onBrowseBackupPath: () -> Unit,
    onResetToDefault: () -> Unit,
    appVersion: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        // Top bar with back button
        SettingsTopBar(
            onBackClick = onBackClick,
            appVersion = appVersion
        )
        
        // Settings content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Application Settings section header (like desktop)
            SettingsGroupHeader(title = "Application Settings")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Backup Base Path section
            SettingsSection(title = "Backup Base Path") {
                // Path display (full width)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .border(
                            width = 1.dp,
                            color = SaveStateRed.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = currentBackupPath,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Backup info
                if (backupInfo != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Files: ${backupInfo.fileCount}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Size: ${backupInfo.formattedSize}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                
                // Migration progress
                AnimatedVisibility(
                    visible = isMigrating,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        val progress = migrationProgress
                        if (progress != null && progress.second > 0) {
                            Text(
                                text = "Migrating files... ${progress.first}/${progress.second}",
                                color = SaveStateRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress.first.toFloat() / progress.second.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = SaveStateRed,
                                trackColor = DarkSurface
                            )
                        } else {
                            Text(
                                text = "Preparing migration...",
                                color = SaveStateRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = SaveStateRed,
                                trackColor = DarkSurface
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Browse and Reset buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onBrowseBackupPath,
                        enabled = !isMigrating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurface,
                            contentColor = TextPrimary,
                            disabledContainerColor = DarkSurface.copy(alpha = 0.5f),
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse...")
                    }
                    
                    OutlinedButton(
                        onClick = onResetToDefault,
                        enabled = !isMigrating,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary,
                            disabledContentColor = TextMuted
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isMigrating) TextMuted.copy(alpha = 0.3f) 
                            else SaveStateRed.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Default")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Placeholder sections for future settings (grayed out)
            FutureSectionPlaceholder(title = "Portable Mode")
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSection(title = "Maximum Number of Backups per Profile") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (maxBackupsPerProfile < 0) "Unlimited" else "$maxBackupsPerProfile",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (maxBackupsPerProfile > 1) onMaxBackupsChange(maxBackupsPerProfile - 1)
                            },
                            enabled = maxBackupsPerProfile > 1,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPrimary,
                                disabledContentColor = TextMuted
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (maxBackupsPerProfile > 1) SaveStateRed.copy(alpha = 0.5f)
                                else TextMuted.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("−", fontSize = 18.sp)
                        }
                        
                        OutlinedButton(
                            onClick = { onMaxBackupsChange(maxBackupsPerProfile + 1) },
                            enabled = maxBackupsPerProfile < 99,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPrimary,
                                disabledContentColor = TextMuted
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (maxBackupsPerProfile < 99) SaveStateRed.copy(alpha = 0.5f)
                                else TextMuted.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("+", fontSize = 18.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FutureSectionPlaceholder(title = "Maximum Source Size for Backup")
            Spacer(modifier = Modifier.height(16.dp))
            FutureSectionPlaceholder(title = "Backup Compression")
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Top bar for settings screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    onBackClick: () -> Unit,
    appVersion: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            
            // Title
            Text(
                text = "SaveState - $appVersion",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            // Settings label
            Text(
                text = "Settings",
                color = SaveStateRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}

/**
 * Group header matching desktop style
 */
@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        color = SaveStateRed,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/**
 * Settings section with border (matches desktop GroupBox style)
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section title
        Text(
            text = title,
            color = SaveStateRed.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        // Content box with border
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
            color = DarkSurfaceVariant,
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SaveStateRed.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

/**
 * Placeholder for future settings (grayed out)
 */
@Composable
private fun FutureSectionPlaceholder(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextMuted.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = DarkSurfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Coming soon...",
                    color = TextMuted.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
