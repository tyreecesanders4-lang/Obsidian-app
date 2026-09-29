package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ObsidianColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    primaryContainer = NeonPurpleDeep,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = ElectricCyanDeep,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonMagenta,
    onTertiary = Color.White,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = CoralError,
    onError = Color.White,
    outline = GlassBorderPurple
)

@Composable
fun ObsidianPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Reusable dark glassmorphism surface modifier with translucent obsidian fill
 * and a dual-tone neon purple/cyan specular border.
 */
fun Modifier.glassPanel(
    cornerRadius: Dp = 20.dp,
    borderAlpha: Float = 0.32f,
    highlightCyan: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = if (highlightCyan) {
        Brush.linearGradient(
            colors = listOf(
                ElectricCyan.copy(alpha = borderAlpha + 0.15f),
                NeonPurple.copy(alpha = borderAlpha),
                Color.White.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                NeonPurple.copy(alpha = borderAlpha),
                ElectricCyan.copy(alpha = borderAlpha * 0.7f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    }
    val fillBrush = Brush.verticalGradient(
        colors = listOf(
            ObsidianSurfaceElevated.copy(alpha = 0.82f),
            ObsidianSurface.copy(alpha = 0.90f)
        )
    )
    return this
        .clip(shape)
        .background(brush = fillBrush, shape = shape)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
}
