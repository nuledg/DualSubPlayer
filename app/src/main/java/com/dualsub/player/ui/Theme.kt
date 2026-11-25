package com.dualsub.player.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colors of the app. They follow the icon: violet to blue. Dark only, because it is a video player. */
private val DualSubColors = darkColorScheme(
    primary = Color(0xFF9B8CFF),
    onPrimary = Color(0xFF14102B),
    primaryContainer = Color(0xFF2B2660),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFF63A4FF),
    onSecondary = Color(0xFF06182F),
    background = Color(0xFF0C0D11),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF14161B),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1C1F26),
    onSurfaceVariant = Color(0xFFA9AFBD),
    outline = Color(0xFF3B404C),
    outlineVariant = Color(0xFF292D37),
    error = Color(0xFFFF8A80)
)

private val DualSubShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DualSubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DualSubColors, shapes = DualSubShapes, content = content)
}
