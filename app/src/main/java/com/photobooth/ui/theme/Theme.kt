package com.photobooth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    secondary = Color(0xFFFF6584),
    onSecondary = Color.White,
    background = Color(0xFF121218),
    onBackground = Color.White,
    surface = Color(0xFF1E1E2E),
    onSurface = Color.White,
    error = Color(0xFFFF6B6B),
    onError = Color.White,
)

private val PhotoboothTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 120.sp,
        letterSpacing = (-2).sp,
        color = Color.White,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        color = Color.White,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        color = Color.White,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        color = Color.White,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        color = Color(0xFFB0B0C0),
    ),
)

@Composable
fun PhotoboothTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = PhotoboothTypography,
        content = content,
    )
}
