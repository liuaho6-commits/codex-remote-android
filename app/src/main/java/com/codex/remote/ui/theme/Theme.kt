package com.codex.remote.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val CodexGreen = Color(0xFF0E7A53)
val CodexRed = Color(0xFFB93A32)
val DiffGreen = Color(0xFFE5F4EA)
val DiffRed = Color(0xFFFBE8E7)
val Ink = Color(0xFF171716)
val WarmBackground = Color(0xFFF7F7F5)
val Panel = Color(0xFFF0F0ED)
val Stroke = Color(0xFFD9D9D4)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E2),
    onPrimaryContainer = Ink,
    secondary = CodexGreen,
    onSecondary = Color.White,
    error = CodexRed,
    background = WarmBackground,
    onBackground = Ink,
    surface = Color(0xFFFCFCFA),
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = Color(0xFF565650),
    outline = Stroke,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF1F1ED),
    onPrimary = Color(0xFF181817),
    primaryContainer = Color(0xFF353532),
    onPrimaryContainer = Color(0xFFF1F1ED),
    secondary = Color(0xFF67C99B),
    onSecondary = Color(0xFF072A1C),
    error = Color(0xFFFFB4AC),
    background = Color(0xFF181817),
    onBackground = Color(0xFFF0F0EC),
    surface = Color(0xFF20201E),
    onSurface = Color(0xFFF0F0EC),
    surfaceVariant = Color(0xFF2A2A27),
    onSurfaceVariant = Color(0xFFB9B9B1),
    outline = Color(0xFF464641),
)

private val CodexTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
)

val MonoText = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
)

@Composable
fun CodexRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = CodexTypography, content = content)
}
