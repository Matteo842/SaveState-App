package com.savestate.app.data.model

/**
 * Represents a game profile in SaveState
 * Matches the structure from the desktop application
 */
data class GameProfile(
    val id: String,
    val name: String,
    val emulator: String,
    val savePath: String,           // URI (SAF) or file path (root) to save folder
    val parentPath: String? = null, // URI/path to parent folder - for restore
    val backupCount: Int = 0,
    val lastBackup: String? = null, // Format: "DD/MM/YYYY HH:mm"
    val isFavorite: Boolean = false,
    val iconResId: Int? = null,
    val gameFilePrefix: String? = null, // Base ROM name for file-level filtering (RetroArch)
    val requiresRoot: Boolean = false   // When true, savePath is a file path accessed via root
)

/**
 * Supported emulators for Android
 * Based on desktop app's emulator list, adapted for mobile
 */
enum class Emulator(val displayName: String) {
    RETROARCH("RetroArch"),
    DOLPHIN("Dolphin"),
    DUCKSTATION("DuckStation"),
    PPSSPP("PPSSPP"),
    CITRA("Citra"),
    AZAHAR("Azahar"),
    DRASTIC("DraStic"),
    FLYCAST("Flycast"),
    MGBA("mGBA"),
    LEMUROID("Lemuroid"),
    M64PLUS_FZ("M64Plus FZ"),
    PIZZA_BOY("Pizza Boy"),
    AETHERSX2("AetherSX2"),
    VITA3K("Vita3K"),
    SKYLINE("Skyline"),
    YUZU("Yuzu"),
    CITRON("Citron"),
    OTHER("Other")
}
