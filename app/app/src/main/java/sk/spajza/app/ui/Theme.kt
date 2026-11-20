package sk.spajza.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Pal {
    val Bg = Color(0xFF121411)
    val Surface = Color(0xFF1B1E19)
    val SurfaceHi = Color(0xFF252922)
    val Outline = Color(0xFF3A4036)
    val Green = Color(0xFF7BC47F)
    val GreenDark = Color(0xFF1F3B22)
    val Amber = Color(0xFFF2B84B)
    val Orange = Color(0xFFEE8A3C)
    val Red = Color(0xFFEF6B5E)
    val Blue = Color(0xFF6FB1E8)
    val Text = Color(0xFFE6E8E1)
    val Muted = Color(0xFF9AA093)
}

private val scheme = darkColorScheme(
    primary = Pal.Green,
    onPrimary = Color(0xFF0B2A0E),
    primaryContainer = Pal.GreenDark,
    onPrimaryContainer = Color(0xFFCDEBCF),
    secondary = Pal.Amber,
    onSecondary = Color(0xFF3A2A00),
    tertiary = Pal.Blue,
    background = Pal.Bg,
    onBackground = Pal.Text,
    surface = Pal.Surface,
    onSurface = Pal.Text,
    surfaceVariant = Pal.SurfaceHi,
    onSurfaceVariant = Pal.Muted,
    surfaceContainer = Pal.Surface,
    surfaceContainerHigh = Pal.SurfaceHi,
    surfaceContainerHighest = Pal.SurfaceHi,
    outline = Pal.Outline,
    outlineVariant = Pal.Outline,
    error = Pal.Red
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun SpajzaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
}
