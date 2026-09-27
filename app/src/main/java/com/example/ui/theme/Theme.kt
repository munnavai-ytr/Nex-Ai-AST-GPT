package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexDarkColorScheme = darkColorScheme(
    primary = NexIndigoLight,
    onPrimary = Color(0xFF0F1221),
    primaryContainer = NexIndigoDark,
    onPrimaryContainer = Color(0xFFE0E7FF),
    
    secondary = NexCyanLight,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = NexCyanDark,
    onSecondaryContainer = Color(0xFFCFFAFE),
    
    tertiary = NexEmeraldGlow,
    onTertiary = Color(0xFF003822),
    tertiaryContainer = Color(0xFF065F46),
    onTertiaryContainer = Color(0xFFD1FAE5),
    
    background = NexObsidian,
    onBackground = NexTextPrimary,
    surface = NexSurface,
    onSurface = NexTextPrimary,
    surfaceVariant = NexSurfaceElevated,
    onSurfaceVariant = NexTextSecondary,
    
    outline = NexBorder,
    outlineVariant = NexBorderLight,
    
    error = NexRose,
    onError = Color.White
)

@Composable
fun NexTheme(
    darkTheme: Boolean = true, // Dark-first default for premium AI experience
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NexDarkColorScheme,
        typography = Typography,
        content = content
    )
}
