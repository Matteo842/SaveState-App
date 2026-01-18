package com.savestate.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.savestate.app.data.model.GameProfile
import com.savestate.app.ui.components.*
import com.savestate.app.ui.theme.*

/**
 * Main screen of SaveState Android app
 * Mirrors the desktop application layout:
 * - Top bar with title and icons
 * - Profiles section with scrollable list
 * - Actions section (Backup, Restore, Manage Backups)
 * - General section (New Profile, Settings)
 */
@Composable
fun MainScreen(
    profiles: List<GameProfile>,
    selectedProfileId: String?,
    onProfileSelect: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onManageBackupsClick: () -> Unit,
    onNewProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onThemeToggle: () -> Unit,
    isDarkTheme: Boolean = true,
    appVersion: String = "1.0",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top app bar - matching desktop title bar
        SaveStateTopBar(
            appVersion = appVersion,
            isDarkTheme = isDarkTheme,
            onThemeToggle = onThemeToggle,
            onSettingsClick = onSettingsClick
        )
        
        // Main content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Profiles section - matching desktop "Profiles" group box
            ProfilesSection(
                profiles = profiles,
                selectedProfileId = selectedProfileId,
                onProfileSelect = onProfileSelect,
                onFavoriteToggle = onFavoriteToggle,
                onDeleteProfile = onDeleteProfile,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Actions section - Backup, Restore, Manage Backups
            ActionsSection(
                onBackupClick = onBackupClick,
                onRestoreClick = onRestoreClick,
                onManageBackupsClick = onManageBackupsClick,
                hasProfileSelected = selectedProfileId != null
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // General section - New Profile, Settings
            GeneralSection(
                onNewProfileClick = onNewProfileClick,
                onSettingsClick = onSettingsClick
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Profiles section with table header and scrollable list
 * Matches desktop "Profiles" group box styling
 */
@Composable
fun ProfilesSection(
    profiles: List<GameProfile>,
    selectedProfileId: String?,
    onProfileSelect: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        SectionHeader(title = "Profiles")
        
        // Profile list container with border (like desktop group box)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp)),
            color = DarkSurfaceVariant,
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SaveStateRed.copy(alpha = 0.3f))
        ) {
            Column {
                // Table header
                ProfileTableHeader()
                
                // Divider
                HorizontalDivider(
                    color = DarkSurface,
                    thickness = 1.dp
                )
                
                // Profiles list
                if (profiles.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = "No profiles yet.\nTap \"New Profile...\" to add your first game.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(profiles) { index, profile ->
                            ProfileCard(
                                profile = profile,
                                isSelected = profile.id == selectedProfileId,
                                isAlternateRow = index % 2 == 1,
                                onProfileClick = { onProfileSelect(profile.id) },
                                onFavoriteClick = { onFavoriteToggle(profile.id) },
                                onDeleteClick = { onDeleteProfile(profile.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
