package com.savestate.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun darkScheme(c: AppColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = c.onAccent,
    primaryContainer = c.accentDark,
    onPrimaryContainer = c.textPrimary,
    secondary = c.accentLight,
    onSecondary = c.onAccent,
    background = c.background,
    onBackground = c.textPrimary,
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.textSecondary,
    error = c.statusError,
    onError = c.onAccent,
)

private fun lightScheme(c: AppColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = c.onAccent,
    primaryContainer = c.accentSoft,
    onPrimaryContainer = c.accentDark,
    secondary = c.accentLight,
    onSecondary = c.onAccent,
    background = c.background,
    onBackground = c.textPrimary,
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.textSecondary,
    error = c.statusError,
    onError = c.onAccent,
)

/**
 * SaveState theme wrapper.
 *
 * Picks the dark or light [AppColors] palette and exposes it via
 * [LocalAppColors] so every screen / widget can read theme-aware colors
 * through the existing token names (DarkBackground, SaveStateRed, …).
 */
@Composable
fun SaveStateTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (darkTheme) darkScheme(appColors) else lightScheme(appColors)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = appColors.background.toArgb()
            // In light mode we want dark icons on the (light) status bar.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
