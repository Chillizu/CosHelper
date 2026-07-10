package com.coshelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color(0xFF002200),
    primaryContainer = Color(0xFF005313),
    onPrimaryContainer = Color(0xFF95F990),
    secondary = Color(0xFF2196F3),
    onSecondary = Color(0xFF002D59),
    secondaryContainer = Color(0xFF00467E),
    onSecondaryContainer = Color(0xFFCEE5FF),
    tertiary = Color(0xFF9C27B0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF6A0080),
    onTertiaryContainer = Color(0xFFFFD6FF),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E2DE),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E2DE),
    surfaceVariant = Color(0xFF3F3F3F),
    onSurfaceVariant = Color(0xFFC0C0C0),
    error = Color(0xFFFF5449),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun CosHelperTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
