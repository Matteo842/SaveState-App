package com.savestate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savestate.app.ui.theme.*
import com.savestate.app.ui.tutorial.tutorialTarget

/**
 * Top app bar matching SaveState desktop title bar style
 * Features:
 * - "SaveState" title on left
 * - Settings gear icon
 * - Theme toggle (sun/moon) like desktop
 */
@Composable
fun SaveStateTopBar(
    appVersion: String,
    isDarkTheme: Boolean = true,
    onThemeToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    onControllerClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shorter bar and title in landscape to give content more height */
    compact: Boolean = false
) {
    val barHeight = if (compact) 44.dp else 56.dp
    val titleMain = if (compact) 16.sp else 18.sp
    val titleSub = if (compact) 12.sp else 0.sp
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkBackground,
        shadowElevation = if (compact) 2.dp else 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(horizontal = if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App title — full version in portrait; compact + subtitle in landscape
            if (compact) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "SaveState",
                        color = TextPrimary,
                        fontSize = titleMain,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "v$appVersion",
                        color = TextMuted,
                        fontSize = titleSub,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            } else {
                Text(
                    text = "SaveState - $appVersion",
                    color = TextPrimary,
                    fontSize = titleMain,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Right side icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val iconSize = if (compact) 20.dp else 22.dp
                
                // Controller mapping button
                IconButton(onClick = onControllerClick) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = "Controller Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(iconSize)
                    )
                }

                // Theme toggle (sun/moon) - matching desktop
                IconButton(onClick = onThemeToggle) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "Toggle theme",
                        tint = if (isDarkTheme) StarGold else TextSecondary,
                        modifier = Modifier.size(iconSize)
                    )
                }

                // Settings gear icon
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.tutorialTarget("settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

/**
 * Table header row matching SaveState desktop "Profile" / "Backup Info" columns
 */
@Composable
fun ProfileTableHeader(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val rowH = if (compact) 32.dp else 40.dp
    val starCol = if (compact) 40.dp else 44.dp
    val deleteCol = if (compact) 36.dp else 40.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowH)
            .background(DarkSurface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Empty space for star column
        Spacer(modifier = Modifier.width(starCol))
        
        // Profile column header
        Text(
            text = "Profile",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        
        // Space for delete button column
        Spacer(modifier = Modifier.width(deleteCol))
    }
}
