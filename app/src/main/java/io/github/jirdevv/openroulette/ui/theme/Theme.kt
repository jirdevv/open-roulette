// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.fossroulette.openroulette.data.FeltTheme
import org.fossroulette.openroulette.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF1B6B3A), onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0C8), onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF7A5C00), secondaryContainer = Color(0xFFFFE08A),
    background = Color(0xFFF6FBF4), surface = Color(0xFFF6FBF4),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7DD99B), onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF0F5229), onPrimaryContainer = Color(0xFFB7F0C8),
    secondary = Color(0xFFFFD54F), secondaryContainer = Color(0xFF584400),
    background = Color(0xFF101410), surface = Color(0xFF101410),
)
private val Amoled = Dark.copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainer = Color(0xFF0A0A0A), surfaceContainerHigh = Color(0xFF121212),
)

@Composable
fun RouletteTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val scheme = when {
        mode == ThemeMode.AMOLED -> Amoled
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Colours used by the betting table and wheel. */
data class FeltPalette(
    val felt: Color, val line: Color, val red: Color, val black: Color,
    val zero: Color, val text: Color, val highlight: Color,
)

fun FeltTheme.palette(): FeltPalette = when (this) {
    FeltTheme.CLASSIC_GREEN -> FeltPalette(Color(0xFF0B6B3A), Color(0xFFF5F5DC), Color(0xFFC62828), Color(0xFF1B1B1B), Color(0xFF2E7D32), Color.White, Color(0xFFFFD54F))
    FeltTheme.MODERN_DARK -> FeltPalette(Color(0xFF1E2227), Color(0xFF8A94A0), Color(0xFFD64550), Color(0xFF0E1013), Color(0xFF2F9E6B), Color(0xFFECEFF1), Color(0xFF4FC3F7))
    FeltTheme.LUXURY_GOLD -> FeltPalette(Color(0xFF2B0A10), Color(0xFFD4AF37), Color(0xFF9B1C2E), Color(0xFF0B0B0B), Color(0xFF1F5F46), Color(0xFFF3E5AB), Color(0xFFFFF176))
    FeltTheme.ROYAL_BLUE -> FeltPalette(Color(0xFF0D3B66), Color(0xFFE3F2FD), Color(0xFFC62828), Color(0xFF14213D), Color(0xFF2E7D32), Color.White, Color(0xFFFFD54F))
}
