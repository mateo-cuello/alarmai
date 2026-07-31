package com.alarmai.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The shared vocabulary for both screens.
 *
 * This file replaces `Glassmorphism.kt`, whose `glassmorphicCard()` was a no-op that returned the
 * receiver unchanged — the "glass" look was really `containerColor = white 7%` plus a 1dp white
 * border, hand-repeated at every call site. Repeating a recipe by hand is how five cards drift
 * apart; defining it once is how they stay in step.
 */

/** 4dp-based spacing scale. Use these instead of typing dp values into layouts. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Standard control height — buttons, pills, inputs all line up on this. */
val ControlHeight = 48.dp

/**
 * A flat section container: opaque surface, hairline border, no shadow and no elevation.
 *
 * @param title header text; the header row is omitted entirely when null.
 * @param icon optional leading glyph, drawn in [TextSecondary] rather than a colour of its own —
 *   coloured icons were the main reason the old settings screen read as busy.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface1, MaterialTheme.shapes.large)
            .border(1.dp, Line, MaterialTheme.shapes.large)
            .padding(Spacing.lg)
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        content()
    }
}

/** Label above a control. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = TextSecondary,
        modifier = modifier
    )
}

/** Explanatory line under a label. */
@Composable
fun FieldHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = modifier
    )
}

/**
 * A toggleable pill — day-of-week squares, language choices, quick replies.
 *
 * Selection is signalled by an accent-tinted fill and a brighter border, never by a colour
 * change that carries meaning on its own, so it still reads for colour-blind users.
 */
@Composable
fun SelectablePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp)
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (selected) AccentSurface else Surface2, shape)
            .border(1.dp, if (selected) AccentLine else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Filled accent button for the one primary action on a screen.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(ControlHeight)
            .background(Accent, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Neutral button for everything else: pickers, dropdown triggers, dismissive actions.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(ControlHeight)
            .background(Surface2, MaterialTheme.shapes.medium)
            .border(1.dp, Line, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * One set of text-field colours for the whole app. Previously each of the four fields declared
 * its own eight-slot block, and they had already drifted to different container alphas.
 */
@Composable
fun appTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Accent,
    focusedBorderColor = LineStrong,
    unfocusedBorderColor = Line,
    focusedContainerColor = Surface2,
    unfocusedContainerColor = Surface2,
    focusedLabelColor = TextSecondary,
    unfocusedLabelColor = TextSecondary,
    focusedPlaceholderColor = TextTertiary,
    unfocusedPlaceholderColor = TextTertiary,
    focusedTrailingIconColor = TextSecondary,
    unfocusedTrailingIconColor = TextTertiary
)
