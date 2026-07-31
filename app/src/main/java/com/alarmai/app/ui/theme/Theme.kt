package com.alarmai.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * One scheme, always dark.
 *
 * The light scheme that used to live here was unreachable — `darkTheme` defaulted to true and no
 * caller ever passed false — so it was 12 tokens of dead weight pinning the old template colours
 * in place. Dynamic colour is also gone on purpose: letting the system recolour the app would
 * undo the flat neutral palette this design is built on.
 */
private val AppColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = TextPrimary,
    primaryContainer = AccentSurface,
    onPrimaryContainer = TextPrimary,

    // Secondary and tertiary intentionally resolve to neutrals: this design has one accent, and
    // any Material component reaching for them should come out grey rather than inventing a hue.
    secondary = Surface2,
    onSecondary = TextPrimary,
    tertiary = Accent,
    onTertiary = TextPrimary,

    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,

    outline = Line,
    outlineVariant = Line,

    error = Danger,
    onError = TextPrimary
)

/**
 * Modest, consistent radii. The old screens mixed 30/24/20/16/12/2.dp with no rule behind it.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),  // buttons, inputs
    large = RoundedCornerShape(14.dp),   // cards
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun AlarmAITheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // No statusBarColor here: it is deprecated and a no-op from API 35, and
            // enableEdgeToEdge() in the activity owns the system bar treatment now.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
