package com.savestate.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savestate.app.R
import com.savestate.app.data.model.GameProfile
import com.savestate.app.ui.theme.*
import com.savestate.app.ui.tutorial.tutorialTarget

/**
 * Profile card component matching SaveState desktop app style
 * Features:
 * - Red left border when selected (4px accent line like desktop)
 * - Alternating row colors
 * - Star for favorites
 * - Delete button on hover/selection
 * - Emulator icon + game name layout
 */
@Composable
fun ProfileCard(
    profile: GameProfile,
    isSelected: Boolean,
    isAlternateRow: Boolean,
    onProfileClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFirst: Boolean = false
) {
    val backgroundColor = when {
        isSelected -> DarkTableRowSelected
        isAlternateRow -> DarkTableRowAlt
        else -> DarkSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(backgroundColor)
            .then(if (isFirst) Modifier.tutorialTarget("first_profile_row") else Modifier)
            .clickable { onProfileClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red selection indicator (like desktop app)
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (isSelected) SaveStateRed else Color.Transparent)
        )

        // Favorite star
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier
                .size(40.dp)
                .then(if (isFirst) Modifier.tutorialTarget("favorite_btn") else Modifier)
        ) {
            Icon(
                imageVector = if (profile.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (profile.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (profile.isFavorite) StarGold else StarEmpty,
                modifier = Modifier.size(20.dp)
            )
        }

        // Emulator icon + Profile name
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emulator icon - use actual icon if available
            val emulatorIconRes = getEmulatorIconResource(profile.emulator)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                if (emulatorIconRes != null) {
                    Image(
                        painter = painterResource(id = emulatorIconRes),
                        contentDescription = profile.emulator,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Fallback to text initials if no icon
                    Text(
                        text = profile.emulator.take(2).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Profile name: only game name (without emulator prefix)
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }


        // Delete button (visible on selection like desktop)
        if (isSelected) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete profile",
                    tint = StatusError,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
    }
}

/**
 * Get the drawable resource ID for an emulator icon
 * Returns null if no icon is available for that emulator
 */
private fun getEmulatorIconResource(emulator: String): Int? {
    return when (emulator.lowercase()) {
        "ppsspp" -> R.drawable.ic_emulator_ppsspp
        "retroarch" -> R.drawable.ic_emulator_retroarch
        // Add more emulators here as icons are added:
        // "dolphin" -> R.drawable.ic_emulator_dolphin
        // "duckstation" -> R.drawable.ic_emulator_duckstation
        // "citra" -> R.drawable.ic_emulator_citra
        else -> null
    }
}
