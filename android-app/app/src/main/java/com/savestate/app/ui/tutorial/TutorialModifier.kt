package com.savestate.app.ui.tutorial

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Register a composable as a tutorial target. The overlay looks up the
 * window-space bounds under [key] to draw a spotlight cut-out.
 *
 * Safe to apply unconditionally: when the tutorial is not active the stored
 * rect is simply unused.
 */
fun Modifier.tutorialTarget(key: String): Modifier = this.onGloballyPositioned { coords ->
    TutorialState.targets[key] = coords.boundsInWindow()
}
