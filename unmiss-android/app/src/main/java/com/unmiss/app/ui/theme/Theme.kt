package com.unmiss.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0B326D),
    secondary = Color(0xFF56647B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5ECF8),
    onSecondaryContainer = Color(0xFF253246),
    tertiary = Color(0xFF247A73),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6F4EC),
    onTertiaryContainer = Color(0xFF0A3B37),
    background = Color(0xFFF2F3F7),
    onBackground = Color(0xFF111318),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFE9EBF0),
    onSurfaceVariant = Color(0xFF636872),
    outline = Color(0xFF7A879C),
    outlineVariant = Color(0xFFC9D2E1),
)

@Composable
fun UnmissTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
