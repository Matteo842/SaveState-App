package com.savestate.app.ui.tutorial

/** Identifies which screen a tutorial step belongs to. */
enum class TutorialScreen { MAIN, SETTINGS, ANY }

/**
 * Navigation hooks supplied by the host (MainActivity) so tutorial steps
 * can drive the app between screens without the user having to hunt for
 * buttons.
 */
data class TutorialCallbacks(
    val openSettings: () -> Unit,
    val closeSettings: () -> Unit,
    val finish: () -> Unit
)

/**
 * A single coach-mark step.
 *
 * @param targetKey key registered via `Modifier.tutorialTarget`. `null`
 *   means the tooltip is rendered centered (no spotlight).
 * @param requiresProfiles if true, the step is auto-skipped when the
 *   profile list is empty.
 * @param onAdvance optional side-effect invoked when the user presses
 *   Next (before the step index advances) — typically to change screen.
 */
data class TutorialStep(
    val id: String,
    val targetKey: String?,
    val title: String,
    val body: String,
    val requiredScreen: TutorialScreen = TutorialScreen.ANY,
    val requiresProfiles: Boolean = false,
    val onAdvance: ((TutorialCallbacks) -> Unit)? = null
)

/** Centralized copy for the tour. Easy to move to strings.xml later. */
object TutorialStrings {
    const val WELCOME_TITLE = "Welcome to SaveState"
    const val WELCOME_BODY =
        "This quick tour shows you how to back up and restore your emulator saves. " +
        "It takes about a minute. You can skip it at any time."

    const val PERMISSION_TITLE = "Storage Permission"
    const val PERMISSION_BODY =
        "SaveState needs \"All Files Access\" to read save files from your emulators. " +
        "If you haven't granted it yet, Android will ask you once the tour is over."

    const val SETTINGS_BTN_TITLE = "Open Settings"
    const val SETTINGS_BTN_BODY =
        "Tap the gear icon to open Settings. First we'll pick a folder where SaveState " +
        "will keep all its backups."

    const val BROWSE_TITLE = "Pick a Backup Folder"
    const val BROWSE_BODY =
        "Tap Browse to choose where backups are saved. Any folder on your device works " +
        "(for example Documents/SaveState). You can change it later."

    const val ROOT_TITLE = "Root Mode (Optional)"
    const val ROOT_BODY =
        "Turn this ON only if your device is rooted. It lets SaveState reach saves in " +
        "protected folders (Dolphin, DuckStation). Leave it OFF otherwise."

    const val ADVANCED_TITLE = "Advanced Options"
    const val ADVANCED_BODY =
        "How many backups to keep per profile, the max size of a save, and compression " +
        "level. The defaults work great — tweak them later if you need to."

    const val BACK_TITLE = "Return to Main"
    const val BACK_BODY =
        "Use the back arrow to return to the main screen. We'll go back now and create " +
        "your first profile."

    const val NEW_PROFILE_TITLE = "Add a Profile"
    const val NEW_PROFILE_BODY =
        "Tap New Profile to pick an installed emulator and a game. Each profile tracks " +
        "the saves of one single game."

    const val SELECT_PROFILE_TITLE = "Select a Profile"
    const val SELECT_PROFILE_BODY =
        "Tap any row to select a profile. The red bar on the left shows which one is " +
        "currently active — the action buttons below work on that profile."

    const val BACKUP_TITLE = "Backup"
    const val BACKUP_BODY =
        "Saves a snapshot of the selected game's save files. Do this any time you want " +
        "a safe checkpoint — before risky updates, edits, or trying new mods."

    const val RESTORE_MANAGE_TITLE = "Restore & Manage"
    const val RESTORE_MANAGE_BODY =
        "Restore brings back a previous snapshot. Manage Backups lists every snapshot " +
        "for the selected profile so you can review or delete old ones."

    const val FAVORITE_TITLE = "Favorites"
    const val FAVORITE_BODY =
        "Tap the star to pin a profile to the top of the list. Handy when you have a " +
        "lot of games."

    const val DONE_TITLE = "You're all set"
    const val DONE_BODY =
        "That's it — your saves are now easy to protect. Enjoy SaveState, and thanks " +
        "for your support!"

    const val SKIP_DIALOG_TITLE = "Skip tutorial?"
    const val SKIP_DIALOG_BODY =
        "You won't be able to replay this tour from inside the app. If you need it " +
        "again, clear app data from Android settings."
    const val SKIP_DIALOG_CONFIRM = "Skip"
    const val SKIP_DIALOG_CANCEL = "Continue"
}

/** Ordered list of steps shown to a first-launch user. */
object TutorialSteps {

    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = "welcome",
            targetKey = null,
            title = TutorialStrings.WELCOME_TITLE,
            body = TutorialStrings.WELCOME_BODY,
            requiredScreen = TutorialScreen.MAIN
        ),
        TutorialStep(
            id = "permission",
            targetKey = null,
            title = TutorialStrings.PERMISSION_TITLE,
            body = TutorialStrings.PERMISSION_BODY,
            requiredScreen = TutorialScreen.MAIN
        ),
        TutorialStep(
            id = "settings_btn",
            targetKey = "settings_btn",
            title = TutorialStrings.SETTINGS_BTN_TITLE,
            body = TutorialStrings.SETTINGS_BTN_BODY,
            requiredScreen = TutorialScreen.MAIN,
            onAdvance = { cbs -> cbs.openSettings() }
        ),
        TutorialStep(
            id = "browse_btn",
            targetKey = "browse_btn",
            title = TutorialStrings.BROWSE_TITLE,
            body = TutorialStrings.BROWSE_BODY,
            requiredScreen = TutorialScreen.SETTINGS
        ),
        TutorialStep(
            id = "root_switch",
            targetKey = "root_switch",
            title = TutorialStrings.ROOT_TITLE,
            body = TutorialStrings.ROOT_BODY,
            requiredScreen = TutorialScreen.SETTINGS
        ),
        TutorialStep(
            id = "compression_group",
            targetKey = "compression_group",
            title = TutorialStrings.ADVANCED_TITLE,
            body = TutorialStrings.ADVANCED_BODY,
            requiredScreen = TutorialScreen.SETTINGS
        ),
        TutorialStep(
            id = "settings_back",
            targetKey = "settings_back",
            title = TutorialStrings.BACK_TITLE,
            body = TutorialStrings.BACK_BODY,
            requiredScreen = TutorialScreen.SETTINGS,
            onAdvance = { cbs -> cbs.closeSettings() }
        ),
        TutorialStep(
            id = "new_profile_btn",
            targetKey = "new_profile_btn",
            title = TutorialStrings.NEW_PROFILE_TITLE,
            body = TutorialStrings.NEW_PROFILE_BODY,
            requiredScreen = TutorialScreen.MAIN
        ),
        TutorialStep(
            id = "first_profile_row",
            targetKey = "first_profile_row",
            title = TutorialStrings.SELECT_PROFILE_TITLE,
            body = TutorialStrings.SELECT_PROFILE_BODY,
            requiredScreen = TutorialScreen.MAIN,
            requiresProfiles = true
        ),
        TutorialStep(
            id = "backup_btn",
            targetKey = "backup_btn",
            title = TutorialStrings.BACKUP_TITLE,
            body = TutorialStrings.BACKUP_BODY,
            requiredScreen = TutorialScreen.MAIN
        ),
        TutorialStep(
            id = "restore_manage",
            targetKey = "restore_btn",
            title = TutorialStrings.RESTORE_MANAGE_TITLE,
            body = TutorialStrings.RESTORE_MANAGE_BODY,
            requiredScreen = TutorialScreen.MAIN
        ),
        TutorialStep(
            id = "favorite_btn",
            targetKey = "favorite_btn",
            title = TutorialStrings.FAVORITE_TITLE,
            body = TutorialStrings.FAVORITE_BODY,
            requiredScreen = TutorialScreen.MAIN,
            requiresProfiles = true
        ),
        TutorialStep(
            id = "done",
            targetKey = null,
            title = TutorialStrings.DONE_TITLE,
            body = TutorialStrings.DONE_BODY,
            requiredScreen = TutorialScreen.MAIN
        )
    )
}
