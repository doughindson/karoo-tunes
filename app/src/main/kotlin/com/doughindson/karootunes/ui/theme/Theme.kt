package com.doughindson.karootunes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BrandGreen = Color(0xFF1DB954)
private val BrandGreenDark = Color(0xFF15883E)

private val LightColors = lightColorScheme(
    primary = BrandGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F5D7),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF4F6354),
    background = Color.White,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0E4F25),
    onPrimaryContainer = Color(0xFFC8F5D7),
    secondary = Color(0xFFB5CCB9),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
)

/** Sized for a Karoo: large, legible text that can be read at a glance. */
private val KarooTypography = Typography(
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KarooTypography,
        content = content,
    )
}
