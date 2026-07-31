package com.alarmai.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Flat minimalist dark palette.
 *
 * Every token is **opaque**. The previous palette leaned on `Color.White.copy(alpha = …)` at a
 * dozen ad-hoc values, so a surface's real colour depended on whatever happened to be painted
 * behind it. Fixed colours make a card look the same wherever it lands, and there are no
 * gradients anywhere in the app.
 *
 * Elevation is a step up this neutral ramp, not a shadow.
 */

// Neutral ramp — background through raised surfaces.
val Ink = Color(0xFF0A0A0C)        // window background
val Surface1 = Color(0xFF131316)   // cards
val Surface2 = Color(0xFF1B1B20)   // inputs, secondary buttons, chips
val Surface3 = Color(0xFF24242B)   // menus, pressed states

// Hairlines — the only separation device. No shadows, no glows.
val Line = Color(0xFF26262E)       // resting border
val LineStrong = Color(0xFF3A3A45) // selected / focused border

// Text ramp. Three steps is enough; more reads as noise.
val TextPrimary = Color(0xFFF2F2F3)
val TextSecondary = Color(0xFF9B9BA5)
val TextTertiary = Color(0xFF7E7E8A)

// A single accent, reserved for the primary action and the active state — nothing decorative.
val Accent = Color(0xFF6366F1)
val AccentPressed = Color(0xFF4F46E5)
// Accent pre-composited over Ink so selected fills can stay opaque.
val AccentSurface = Color(0xFF1A1B35)
val AccentLine = Color(0xFF36387E)

// Status.
val Danger = Color(0xFFE5484D)
