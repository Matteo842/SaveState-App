package com.savestate.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence for the first-launch coach-mark tutorial.
 *
 * - `completed` flips to true when the user finishes or skips the tour;
 *   the tour then never shows again (unless the user clears app data).
 * - `last_step` is written on every advance so that a process death during
 *   the tour can still resume at the right step on next launch.
 */
class TutorialPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCompletedTutorial(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    fun setCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    fun getLastStep(): Int = prefs.getInt(KEY_LAST_STEP, 0)

    fun setLastStep(step: Int) {
        prefs.edit().putInt(KEY_LAST_STEP, step).apply()
    }

    /** Debug helper: wipe tutorial state so the tour runs again on next launch. */
    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "tutorial_prefs"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_LAST_STEP = "last_step"
    }
}
