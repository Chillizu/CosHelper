package com.coshelper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3B7D3E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F2B0),
    onPrimaryContainer = Color(0xFF002200),
    secondary = Color(0xFF1E6BB3),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE5FF),
    onSecondaryContainer = Color(0xFF002D59),
    tertiary = Color(0xFF7D4B8C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD6FF),
    onTertiaryContainer = Color(0xFF300041),
    background = Color(0xFFFBFDF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFBFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE4D9),
    onSurfaceVariant = Color(0xFF424940),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun CosHelperTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
