package com.savestate.app.ui.tutorial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savestate.app.ui.theme.DarkSurfaceVariant
import com.savestate.app.ui.theme.SaveStateRed
import com.savestate.app.ui.theme.TextMuted
import com.savestate.app.ui.theme.TextPrimary
import com.savestate.app.ui.theme.TextSecondary

/**
 * Full-screen coach-mark overlay. Renders a dark scrim with a rounded
 * cut-out at the current step's target (if any), plus a tooltip card with
 * Skip / Next. Blocks pointer input on the rest of the screen so the user
 * always advances through the overlay's buttons.
 *
 * Host composable (MainActivity) is responsible for:
 *  - calling `TutorialState.start()` on first launch,
 *  - supplying the current screen + hasProfiles flag,
 *  - wiring the finish callback to persist completion.
 */
@Composable
fun TutorialOverlay(
    currentScreen: TutorialScreen,
    hasProfiles: Boolean,
    callbacks: TutorialCallbacks,
    modifier: Modifier = Modifier
) {
    if (!TutorialState.isActive) return

    val steps = TutorialSteps.steps
    val index = TutorialState.currentStepIndex

    if (index >= steps.size) {
        LaunchedEffect(Unit) { callbacks.finish() }
        return
    }

    val step = steps[index]
    val shouldSkipStep =
        (step.requiredScreen != TutorialScreen.ANY && step.requiredScreen != currentScreen) ||
        (step.requiresProfiles && !hasProfiles)

    LaunchedEffect(step.id, shouldSkipStep) {
        if (shouldSkipStep) TutorialState.advance()
    }
    if (shouldSkipStep) return

    val target = step.targetKey?.let { TutorialState.targets[it] }
    var showSkipConfirm by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(step.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                }
            }
    ) {
        val density = LocalDensity.current
        val padPx = with(density) { 8.dp.toPx() }
        val cornerPx = with(density) { 12.dp.toPx() }
        val ringPx = with(density) { 2.dp.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val cardMarginPx = with(density) { 16.dp.toPx() }
        // Resolve theme-aware colors here (composable scope) so they can be
        // captured by the Canvas DrawScope lambda below.
        val ringColor = SaveStateRed

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.82f))
            target?.let { rect ->
                val holeTopLeft = Offset(rect.left - padPx, rect.top - padPx)
                val holeSize = Size(rect.width + padPx * 2, rect.height + padPx * 2)
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = holeTopLeft,
                    size = holeSize,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = ringColor,
                    topLeft = holeTopLeft,
                    size = holeSize,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = ringPx)
                )
            }
        }

        var cardHeightPx by remember(step.id) { mutableIntStateOf(0) }
        val cardMaxWidthDp = if (maxWidth > 400.dp) 360.dp else maxWidth - 32.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .offset {
                        val offsetY: Int = if (target == null) {
                            val centered = (containerHeightPx - cardHeightPx) / 2f
                            centered.coerceAtLeast(cardMarginPx).toInt()
                        } else {
                            val belowY = (target.bottom + padPx * 2 + cardMarginPx).toInt()
                            val aboveY = (target.top - padPx * 2 - cardMarginPx - cardHeightPx).toInt()
                            val maxY = (containerHeightPx - cardMarginPx - cardHeightPx).toInt()
                            when {
                                belowY <= maxY -> belowY
                                aboveY >= cardMarginPx.toInt() -> aboveY
                                else -> cardMarginPx.toInt()
                            }
                        }
                        IntOffset(0, offsetY)
                    }
                    .widthIn(max = cardMaxWidthDp)
                    .fillMaxWidth()
                    .onSizeChanged { cardHeightPx = it.height },
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SaveStateRed.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "${index + 1} / ${steps.size}",
                        color = SaveStateRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = step.title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = step.body,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isLast = index == steps.size - 1
                        TextButton(onClick = { showSkipConfirm = true }) {
                            Text(text = "Skip tour", color = TextMuted, fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                step.onAdvance?.invoke(callbacks)
                                if (isLast) {
                                    callbacks.finish()
                                } else {
                                    TutorialState.advance()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SaveStateRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isLast) "Got it" else "Next",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSkipConfirm) {
        AlertDialog(
            onDismissRequest = { showSkipConfirm = false },
            title = {
                Text(text = TutorialStrings.SKIP_DIALOG_TITLE, color = TextPrimary)
            },
            text = {
                Text(text = TutorialStrings.SKIP_DIALOG_BODY, color = TextSecondary)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSkipConfirm = false
                        callbacks.finish()
                    }
                ) {
                    Text(text = TutorialStrings.SKIP_DIALOG_CONFIRM, color = SaveStateRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirm = false }) {
                    Text(text = TutorialStrings.SKIP_DIALOG_CANCEL, color = TextPrimary)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }
}
