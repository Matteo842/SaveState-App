package com.savestate.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Centralized SaveState palette. Two flavors:
 *  - DarkAppColors  → preserves the original mobile dark palette
 *  - LightAppColors → mirrors the desktop LIGHT_THEME_QSS Fluent / macOS-inspired palette
 *
 * Old top-level token names (DarkBackground, SaveStateRed, …) are retained as
 * @Composable getters that read from [LocalAppColors], so existing screens and
 * widgets become theme-aware without rewriting every call site.
 */

@Immutable
data class AppColors(
    val isDark: Boolean,

    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val tableRowAlt: Color,
    val tableRowSelected: Color,
    val divider: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    // Status
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,

    // Action button accents
    val buttonGreen: Color,
    val buttonBlue: Color,
    val buttonGray: Color,

    // Star / favorite
    val starGold: Color,
    val starEmpty: Color,

    // Brand / accent (was "SaveStateRed" in dark; indigo in light, mirroring desktop)
    val accent: Color,
    val accentDark: Color,
    val accentLight: Color,

    // Subtle accent-tinted surfaces (selection, hover, etc.)
    val accentSoft: Color,

    // Generic hint text used on dialog buttons / disabled text on light bg
    val onAccent: Color,
)

// Keep dark palette identical to the previous mobile design.
val DarkAppColors = AppColors(
    isDark = true,
    background = Color(0xFF1A1A1A),
    surface = Color(0xFF242424),
    surfaceVariant = Color(0xFF2D2D2D),
    tableRowAlt = Color(0xFF353535),
    tableRowSelected = Color(0xFF3A3A3A),
    divider = Color(0xFF353535),

    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB0B0B0),
    textMuted = Color(0xFF808080),

    statusSuccess = Color(0xFF4CAF50),
    statusWarning = Color(0xFFFFC107),
    statusError = Color(0xFFF44336),

    buttonGreen = Color(0xFF2E7D32),
    buttonBlue = Color(0xFF1976D2),
    buttonGray = Color(0xFF424242),

    starGold = Color(0xFFFFD700),
    starEmpty = Color(0xFF555555),

    accent = Color(0xFFD32F2F),
    accentDark = Color(0xFFB71C1C),
    accentLight = Color(0xFFEF5350),
    accentSoft = Color(0x33D32F2F),

    onAccent = Color(0xFFFFFFFF),
)

// Light palette mirrors desktop LIGHT_THEME_QSS (Fluent / macOS-inspired indigo).
// Brand "red" semantics in dark become indigo accent in light, matching the
// desktop migration the user just completed.
val LightAppColors = AppColors(
    isDark = false,
    // QMainWindow bg → window chrome; QWidget bg → content surface
    background = Color(0xFFECEDF0),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF4F5F7),
    tableRowAlt = Color(0xFFF8F9FB),
    tableRowSelected = Color(0xFFEEF2FF),
    divider = Color(0xFFE1E3E8),

    textPrimary = Color(0xFF1F2024),
    textSecondary = Color(0xFF5A5D66),
    textMuted = Color(0xFF8A8D96),

    statusSuccess = Color(0xFF2E7D32),
    statusWarning = Color(0xFFF5A524),
    statusError = Color(0xFFE5484D),

    buttonGreen = Color(0xFF2E7D32),
    buttonBlue = Color(0xFF1565C0),
    buttonGray = Color(0xFF37474F),

    // Slightly desaturated gold so it doesn't burn on white
    starGold = Color(0xFFE0AE00),
    starEmpty = Color(0xFFC9CCD3),

    accent = Color(0xFF4F46E5),
    accentDark = Color(0xFF3730A3),
    accentLight = Color(0xFF818CF8),
    accentSoft = Color(0xFFEEF2FF),

    onAccent = Color(0xFFFFFFFF),
)

// compositionLocalOf (NOT staticCompositionLocalOf) so theme switches trigger
// recomposition for every reader.
val LocalAppColors = compositionLocalOf { DarkAppColors }

// ---------------------------------------------------------------------------
// Backward-compatible token aliases.
// These names date from when the app was dark-only. Each one now resolves to
// the active palette via LocalAppColors, so screens that still import them
// (DarkBackground, SaveStateRed, TextPrimary, …) automatically pick up the
// light theme without code changes.
// ---------------------------------------------------------------------------

val DarkBackground: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.background

val DarkSurface: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface

val DarkSurfaceVariant: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceVariant

val DarkTableRowAlt: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.tableRowAlt

val DarkTableRowSelected: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.tableRowSelected

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary

val TextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textMuted

val StatusSuccess: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusSuccess

val StatusWarning: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusWarning

val StatusError: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.statusError

val ButtonGreen: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.buttonGreen

val ButtonBlue: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.buttonBlue

val ButtonGray: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.buttonGray

val StarGold: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.starGold

val StarEmpty: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.starEmpty

val SaveStateRed: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent

val SaveStateRedDark: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentDark

val SaveStateRedLight: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentLight
