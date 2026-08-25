package ru.maxx52.daric.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF67507A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DCF3),
    onPrimaryContainer = Color(0xFF241F2E),
    secondary = Color(0xFF776B84),
    background = Color(0xFFF9F6FC),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFECE6F0),
    onSurface = Color(0xFF241F2E),
    onSurfaceVariant = Color(0xFF5A4E67)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD5B7E8),
    onPrimary = Color(0xFF392449),
    primaryContainer = Color(0xFF503963),
    onPrimaryContainer = Color(0xFFF1DAFF),
    secondary = Color(0xFFCFC3D8),
    background = Color(0xFF1D1A20),
    surface = Color(0xFF252128),
    surfaceVariant = Color(0xFF49454E),
    onSurface = Color(0xFFEAE0EC),
    onSurfaceVariant = Color(0xFFCFC3D8)
)

@Composable
fun DaricTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
