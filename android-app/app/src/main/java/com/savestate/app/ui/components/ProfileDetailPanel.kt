package com.savestate.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savestate.app.R
import com.savestate.app.data.model.GameProfile
import com.savestate.app.ui.theme.*

@Composable
fun ProfileDetailPanel(
    profile: GameProfile?,
    onEditProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    ultraCompact: Boolean = false
) {
    val outerPad = if (ultraCompact) 12.dp else 16.dp
    val titleStyle =
        if (ultraCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val labelStyle =
        if (ultraCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    val bodyStyle =
        if (ultraCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp)),
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SaveStateRed.copy(alpha = 0.3f))
    ) {
        if (profile == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(outerPad),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a profile to view details",
                    color = TextMuted,
                    style = bodyStyle,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = outerPad, vertical = outerPad.coerceAtMost(14.dp))
                    .verticalScroll(scroll)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val emuRes = getEmulatorIconResourceStatic(profile.emulator)
                    Box(
                        modifier = Modifier
                            .size(if (ultraCompact) 40.dp else 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (emuRes != null) {
                            Image(
                                painter = painterResource(id = emuRes),
                                contentDescription = profile.emulator,
                                modifier = Modifier.size(if (ultraCompact) 32.dp else 40.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = profile.emulator.take(2).uppercase(),
                                fontSize = if (ultraCompact) 12.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(if (ultraCompact) 10.dp else 14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = titleStyle,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = profile.emulator,
                            style = labelStyle,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (ultraCompact) 12.dp else 16.dp))

                Text(
                    text = "SAVE LOCATION",
                    style = labelStyle,
                    color = SaveStateRed.copy(alpha = 0.85f),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = profile.savePath,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = if (ultraCompact) 11.sp else 12.sp
                        ),
                        color = TextSecondary,
                        maxLines = if (ultraCompact) 4 else 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                profile.parentPath?.takeIf { it.isNotBlank() }?.let { parent ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "PARENT PATH",
                        style = labelStyle,
                        color = SaveStateRed.copy(alpha = 0.85f),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = parent,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (ultraCompact) 11.sp else 12.sp
                            ),
                            color = TextMuted,
                            maxLines = if (ultraCompact) 2 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (ultraCompact) 12.dp else 16.dp))

                Text(
                    text = "BACKUPS",
                    style = labelStyle,
                    color = SaveStateRed.copy(alpha = 0.85f),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                val backupSummary = when {
                    profile.backupCount > 0 && profile.lastBackup != null ->
                        "${profile.backupCount} saved • Last ${profile.lastBackup}"
                    profile.backupCount > 0 ->
                        "${profile.backupCount} saved"
                    else -> "No backups yet"
                }
                Text(
                    text = backupSummary,
                    style = bodyStyle,
                    color = if (profile.backupCount > 0) TextSecondary else TextMuted
                )

                if (profile.requiresRoot) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Uses root filesystem path",
                        style = MaterialTheme.typography.labelSmall,
                        color = ButtonBlue
                    )
                }

                profile.gameFilePrefix?.takeIf { it.isNotBlank() }?.let { prefix ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Prefix: $prefix",
                        style = labelStyle,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(if (ultraCompact) 12.dp else 16.dp))
                OutlinedButton(
                    onClick = { onEditProfile(profile.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(
                        vertical = if (ultraCompact) 6.dp else 10.dp,
                        horizontal = 12.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = SaveStateRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit profile",
                        color = TextPrimary,
                        fontSize = if (ultraCompact) 13.sp else 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun getEmulatorIconResourceStatic(emulator: String): Int? {
    return when (emulator.lowercase()) {
        "ppsspp" -> R.drawable.ic_emulator_ppsspp
        "retroarch" -> R.drawable.ic_emulator_retroarch
        "yuzu" -> R.drawable.ic_emulator_yuzu
        "citron" -> R.drawable.ic_emulator_citron
        "eden" -> R.drawable.ic_emulator_eden
        else -> null
    }
}
