package com.savestate.app.ui.tutorial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Global, process-wide state holder for the coach-mark tutorial.
 *
 * Kept as a Kotlin object so that target composables (deep in the tree)
 * can register their bounds via `Modifier.tutorialTarget(...)` without
 * needing to receive a CompositionLocal or a ViewModel.
 */
object TutorialState {

    var isActive by mutableStateOf(false)
        private set

    var currentStepIndex by mutableStateOf(0)
        private set

    /** window-space rects of registered target composables, keyed by step id. */
    val targets = mutableStateMapOf<String, Rect>()

    fun start(fromStep: Int = 0) {
        currentStepIndex = fromStep.coerceAtLeast(0)
        isActive = true
    }

    fun advance() {
        currentStepIndex += 1
    }

    fun goTo(index: Int) {
        currentStepIndex = index.coerceAtLeast(0)
    }

    fun finish() {
        isActive = false
        currentStepIndex = 0
        targets.clear()
    }
}
