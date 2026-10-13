package com.rhulhiking.attendance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF1E5B3A)

private val LightColors = lightColorScheme(primary = Forest, onPrimary = Color.White)
private val DarkColors = darkColorScheme(primary = Color(0xFF8FD4A8), onPrimary = Color(0xFF00391C))

@Composable
fun HikingTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
