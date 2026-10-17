package com.ali.formapic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Luxury DSLR Camera Color Palette
val ObsidianBlack = Color(0xFF0A0A0C)
val CharcoalSurface = Color(0xFF141519)
val CharcoalElevated = Color(0xFF1F2128)
val ChampagneGold = Color(0xFFD4AF37)
val SoftGold = Color(0xFFF3E5AB)
val TitaniumSilver = Color(0xFFC0C5CE)
val MutedSilver = Color(0xFF8A919E)
val PureWhite = Color(0xFFFAFAFC)
val CrimsonRecord = Color(0xFFE53935)
val EmeraldPeak = Color(0xFF00E676)

private val FormaDarkColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = ObsidianBlack,
    primaryContainer = CharcoalElevated,
    onPrimaryContainer = SoftGold,
    secondary = TitaniumSilver,
    onSecondary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = PureWhite,
    surface = CharcoalSurface,
    onSurface = PureWhite,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = TitaniumSilver,
    error = CrimsonRecord
)

val FormaTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 1.5.sp,
        color = PureWhite
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        letterSpacing = 0.8.sp,
        color = PureWhite
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 0.5.sp,
        color = PureWhite
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = TitaniumSilver
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        color = ChampagneGold
    )
)

@Composable
fun FormaPicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FormaDarkColorScheme,
        typography = FormaTypography,
        content = content
    )
}
