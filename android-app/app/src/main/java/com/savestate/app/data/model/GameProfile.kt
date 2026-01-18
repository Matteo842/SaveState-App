package com.savestate.app.data.model

/**
 * Represents a game profile in SaveState
 * Matches the structure from the desktop application
 */
data class GameProfile(
    val id: String,
    val name: String,
    val emulator: String,
    val savePath: String,
    val backupCount: Int = 0,
    val lastBackup: String? = null, // Format: "DD/MM/YYYY HH:mm"
    val isFavorite: Boolean = false,
    val iconResId: Int? = null // Resource ID for emulator icon
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
    PIZZA_BOY("Pizza Boy"),
    AETHERSX2("AetherSX2"),
    VITA3K("Vita3K"),
    SKYLINE("Skyline"),
    YUZU("Yuzu"),
    CITRON("Citron"),
    OTHER("Other")
}
