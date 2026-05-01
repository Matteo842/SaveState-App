package com.savestate.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
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
    onEditProfile: (String) -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onManageBackupsClick: () -> Unit,
    onNewProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onThemeToggle: () -> Unit,
    isDarkTheme: Boolean = true,
    appVersion: String,
    modifier: Modifier = Modifier
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top app bar - matching desktop title bar
        SaveStateTopBar(
            appVersion = appVersion,
            isDarkTheme = isDarkTheme,
            onThemeToggle = onThemeToggle,
            onSettingsClick = onSettingsClick
        )

        val contentPadding = if (isLandscape) 8.dp else 12.dp

        if (isLandscape) {
            val sideRailScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                ProfilesSection(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    onProfileSelect = onProfileSelect,
                    onFavoriteToggle = onFavoriteToggle,
                    onDeleteProfile = onDeleteProfile,
                    onEditProfile = onEditProfile,
                    compact = true,
                    showSectionTitle = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                // Compact side rail (~10% narrower than previous step) → more width for profile list
                Column(
                    modifier = Modifier
                        .widthIn(min = 128.dp, max = 168.dp)
                        .fillMaxHeight()
                        .verticalScroll(sideRailScroll),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ActionsSection(
                        onBackupClick = onBackupClick,
                        onRestoreClick = onRestoreClick,
                        onManageBackupsClick = onManageBackupsClick,
                        hasProfileSelected = selectedProfileId != null,
                        compact = true,
                        stackVertically = true,
                        tightSpacing = true
                    )
                    GeneralSection(
                        onNewProfileClick = onNewProfileClick,
                        onSettingsClick = onSettingsClick,
                        compact = true,
                        stackVertically = true,
                        tightSpacing = true
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                ProfilesSection(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    onProfileSelect = onProfileSelect,
                    onFavoriteToggle = onFavoriteToggle,
                    onDeleteProfile = onDeleteProfile,
                    onEditProfile = onEditProfile,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                ActionsSection(
                    onBackupClick = onBackupClick,
                    onRestoreClick = onRestoreClick,
                    onManageBackupsClick = onManageBackupsClick,
                    hasProfileSelected = selectedProfileId != null
                )

                Spacer(modifier = Modifier.height(8.dp))

                GeneralSection(
                    onNewProfileClick = onNewProfileClick,
                    onSettingsClick = onSettingsClick
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
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
    onEditProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** Landscape: hide red "Profiles" label to free vertical space for the list */
    showSectionTitle: Boolean = true
) {
    // Favorites first; stable sort keeps prior order within favorites / non-favorites
    val displayProfiles = remember(profiles) {
        profiles.sortedWith(compareByDescending<GameProfile> { it.isFavorite })
    }
    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        if (showSectionTitle) {
            SectionHeader(title = "Profiles", compact = compact)
        }

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
                if (displayProfiles.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        itemsIndexed(displayProfiles) { index, profile ->
                            ProfileCard(
                                profile = profile,
                                isSelected = profile.id == selectedProfileId,
                                isAlternateRow = index % 2 == 1,
                                onProfileClick = { onProfileSelect(profile.id) },
                                onFavoriteClick = { onFavoriteToggle(profile.id) },
                                onDeleteClick = { onDeleteProfile(profile.id) },
                                onEditClick = { onEditProfile(profile.id) },
                                isFirst = index == 0
                            )
                        }
                    }
                }

                // Backup Info Footer (New)
                HorizontalDivider(
                    color = DarkSurface,
                    thickness = 1.dp
                )

                val selectedProfile = profiles.find { it.id == selectedProfileId }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 40.dp else 50.dp)
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = if (compact) 10.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    val footerStyle =
                        if (compact) MaterialTheme.typography.bodySmall
                        else MaterialTheme.typography.bodyMedium
                    if (selectedProfile != null) {
                        if (selectedProfile.backupCount > 0 && selectedProfile.lastBackup != null) {
                            Text(
                                text = "Backups: ${selectedProfile.backupCount} | Last: ${selectedProfile.lastBackup}",
                                style = footerStyle,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = "No backups available",
                                style = footerStyle,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    } else {
                         Text(
                            text = "Select a profile to view details",
                            style = footerStyle,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
