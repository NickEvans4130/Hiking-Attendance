package com.rhulhiking.attendance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.times

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBD6),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4F6354),
    onSecondary = Color.White,
    background = Color(0xFFF7F9F4),
    onBackground = Color(0xFF181D19),
    surface = Color(0xFFF7F9F4),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF3F4941),
    outlineVariant = Color(0xFFBFC9BF),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD4A8),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF0E4A2A),
    onPrimaryContainer = Color(0xFFCDEBD6),
    secondary = Color(0xFFB6CCB9),
    onSecondary = Color(0xFF213527),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE1E4DE),
    surface = Color(0xFF111411),
    onSurface = Color(0xFFE1E4DE),
    surfaceVariant = Color(0xFF3F4941),
    onSurfaceVariant = Color(0xFFBFC9BF),
    outlineVariant = Color(0xFF3F4941),
    error = Color(0xFFFFB4AB),
)

/** Default Material type scale enlarged ~15% for reading at arm's length outdoors. */
private val OutdoorTypography = Typography().let { t ->
    fun TextStyle.big(f: Float = 1.15f) = copy(fontSize = f * fontSize, lineHeight = scale(lineHeight, f))
    t.copy(
        displaySmall = t.displaySmall.big(1.05f),
        headlineLarge = t.headlineLarge.big(),
        headlineMedium = t.headlineMedium.big(),
        headlineSmall = t.headlineSmall.big(),
        titleLarge = t.titleLarge.big(),
        titleMedium = t.titleMedium.big(),
        titleSmall = t.titleSmall.big(),
        bodyLarge = t.bodyLarge.big(),
        bodyMedium = t.bodyMedium.big(),
        labelLarge = t.labelLarge.big(),
    )
}

private fun scale(unit: TextUnit, f: Float): TextUnit = if (unit == TextUnit.Unspecified) unit else f * unit

@Composable
fun HikingTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OutdoorTypography,
        content = content,
    )
}
