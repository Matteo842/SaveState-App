package com.savestate.app.security.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savestate.app.security.BlockReason
import com.savestate.app.ui.theme.DarkBackground
import com.savestate.app.ui.theme.SaveStateRed
import com.savestate.app.ui.theme.TextMuted
import com.savestate.app.ui.theme.TextPrimary
import com.savestate.app.ui.theme.TextSecondary

/**
 * Full-screen, non-dismissable block screen shown when license verification
 * fails. The only legitimate way out is to open the Play Store and install
 * the official build.
 */
@Composable
fun LicenseBlockScreen(
    reason: BlockReason,
    message: String,
    onOpenPlayStore: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(horizontal = 32.dp, vertical = 48.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SaveStateRed,
                    modifier = Modifier.size(96.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "License verification failed",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = headlineFor(reason),
                    color = SaveStateRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(36.dp))

                Button(
                    onClick = onOpenPlayStore,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SaveStateRed,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Open Google Play",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Retry",
                        fontSize = 14.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "SaveState is distributed through Google Play. " +
                            "Unauthorised copies cannot be used.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private fun headlineFor(reason: BlockReason): String = when (reason) {
    BlockReason.NOT_FROM_PLAY_STORE -> "Installation source not recognised"
    BlockReason.INTEGRITY_FAILED -> "App integrity check failed"
    BlockReason.LICENSE_INVALID -> "No valid Google Play license"
    BlockReason.NETWORK_ERROR -> "Could not reach Google Play"
    BlockReason.UNKNOWN -> "Unknown error"
}
