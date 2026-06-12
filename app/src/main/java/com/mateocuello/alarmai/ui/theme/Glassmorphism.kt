package com.mateocuello.alarmai.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

fun Modifier.glassmorphicCard(
    blurRadius: Float = 20f
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    this.graphicsLayer {
        renderEffect = RenderEffect.createBlurEffect(
            blurRadius, blurRadius, Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    }
} else {
    this
}
