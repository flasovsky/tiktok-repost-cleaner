package com.tiktok.repostcleaner.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// TikTok-inspired color palette
val TikTokRed = Color(0xFFFF0050)
val TikTokCyan = Color(0xFF00F2EA)
val TikTokBlack = Color(0xFF010101)
val TikTokDarkGray = Color(0xFF121212)
val TikTokGray = Color(0xFF1F1F1F)
val TikTokLightGray = Color(0xFF2A2A2A)
val TikTokWhite = Color(0xFFFFFFFF)
val TikTokTextSecondary = Color(0xFF8A8A8A)
val SuccessGreen = Color(0xFF4CAF50)
val ErrorRed = Color(0xFFFF5252)
val WarningOrange = Color(0xFFFF9800)

private val DarkColorScheme = darkColorScheme(
    primary = TikTokRed,
    secondary = TikTokCyan,
    tertiary = TikTokCyan,
    background = TikTokBlack,
    surface = TikTokDarkGray,
    surfaceVariant = TikTokGray,
    onPrimary = TikTokWhite,
    onSecondary = TikTokBlack,
    onBackground = TikTokWhite,
    onSurface = TikTokWhite,
    onSurfaceVariant = TikTokTextSecondary,
    error = ErrorRed,
    onError = TikTokWhite,
    outline = TikTokLightGray
)

@Composable
fun TikTokRepostCleanerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
