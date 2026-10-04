package com.devsecops.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devsecops.tracker.R

// Three accents doing real work, not decoration:
//   primary   — emerald, "good / done / on track"  (mastery L4-L5, studied days)
//   secondary — sky, "action available"            (revise, links)
//   tertiary  — amber, "partial / in progress"     (mastery L3)
//   error     — red, "needs attention"             (mastery L1-L2, weak spots)
// The same scale is used everywhere so a colour always means the same thing.

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DFFA6),          // neon mint: done / on track
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF12382A),
    onPrimaryContainer = Color(0xFFB8FFDD),

    secondary = Color(0xFF5CC8FF),        // sky: actions
    onSecondary = Color(0xFF00202E),
    secondaryContainer = Color(0xFF0E3550),
    onSecondaryContainer = Color(0xFFC6EBFF),

    tertiary = Color(0xFFFFC44D),         // amber: partial
    onTertiary = Color(0xFF3A2500),
    tertiaryContainer = Color(0xFF4A3410),
    onTertiaryContainer = Color(0xFFFFE6B0),

    error = Color(0xFFFF6B6B),            // coral: needs attention
    onError = Color(0xFF450A0A),

    background = Color(0xFF070708),
    onBackground = Color(0xFFEDEFF2),
    surface = Color(0xFF0D0D0F),
    onSurface = Color(0xFFEDEFF2),
    surfaceVariant = Color(0xFF1B1C20),
    onSurfaceVariant = Color(0xFF9EA3AD),
    surfaceContainer = Color(0xFF131316),
    surfaceContainerHigh = Color(0xFF1B1C21),
    outline = Color(0xFF3A3C44),
    outlineVariant = Color(0xFF26272D)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF022C22),

    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF082F49),

    tertiary = Color(0xFFD97706),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),

    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),

    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF5B6674),
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerHigh = Color(0xFFE8EDF4),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

// Two bundled fonts (both OFL): Bricolage Grotesque for headings and numbers,
// Figtree for everything you read. Variable fonts, so one file gives every weight.
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int) = Font(
    R.font.bricolage, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    R.font.figtree, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val HeadingFont = FontFamily(bricolage(600), bricolage(700), bricolage(800))
val BodyFont = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700))

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = HeadingFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        // Designed for OLED: always dark, regardless of system setting.
        colorScheme = DarkColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
