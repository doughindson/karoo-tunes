package com.doughindson.karootunes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spotify's current brand green. Used as a fill only: green text on white fails contrast. */
val SpotifyGreen = Color(0xFF1ED760)

/** Gradient of Spotify's Liked Songs tile. */
val LikedSongsGradient = listOf(Color(0xFF450AF5), Color(0xFF8E8EE5))

/** Spotify's colour for saved episodes ("Your Episodes"). */
val SavedEpisodesColor = Color(0xFF056952)

// Every role is set so nothing falls back to Material's default purple.
private val DarkColors = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreen,
    onPrimaryContainer = Color.Black,
    inversePrimary = Color(0xFF1DB954),
    secondary = Color(0xFFB3B3B3),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF282828),
    onSecondaryContainer = Color.White,
    tertiary = SpotifyGreen,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF282828),
    onTertiaryContainer = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFB3B3B3),
    surfaceTint = Color.Transparent,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    error = Color(0xFFF15E6C),
    onError = Color.Black,
    errorContainer = Color(0xFF3B1418),
    onErrorContainer = Color(0xFFF15E6C),
    outline = Color(0xFF727272),
    outlineVariant = Color(0xFF3E3E3E),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF121212),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF282828),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF333333),
)

private val LightColors = lightColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreen,
    onPrimaryContainer = Color.Black,
    inversePrimary = SpotifyGreen,
    secondary = Color(0xFF6A6A6A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDED),
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDEDED),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF6A6A6A),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF121212),
    inverseOnSurface = Color.White,
    error = Color(0xFFE91429),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E9),
    onErrorContainer = Color(0xFFB00E1E),
    outline = Color(0xFFB3B3B3),
    outlineVariant = Color(0xFFDEDEDE),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFEDEDED),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F6F6),
    surfaceContainer = Color(0xFFEDEDED),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE2E2E2),
)

/** Sized for a 256 x 427 dp screen read at arm's length on a bike. System sans (Spotify's Circular isn't available). */
private val KarooTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
)

private val KarooShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** Follows the Karoo's day/night mode, which switches Android's dark theme. */
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KarooTypography,
        shapes = KarooShapes,
        content = content,
    )
}
