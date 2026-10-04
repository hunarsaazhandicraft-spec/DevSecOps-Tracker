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
//   primary   — mint, "passed / on track"   (stage done, mastery L4-L5)
//   secondary — blue, "info / link"
//   tertiary  — amber, "pending / due"      (revisit due, partial)
//   error     — coral, "failing / gap"      (weak spots, redo)
// The same scale is used everywhere so a colour always means the same thing.

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CF0A8),          // mint: passed / on track
    onPrimary = Color(0xFF04140C),
    primaryContainer = Color(0xFF0E2A1D),
    onPrimaryContainer = Color(0xFFB9F5D6),

    secondary = Color(0xFF7DBBFF),        // blue: info / links
    onSecondary = Color(0xFF04182B),
    secondaryContainer = Color(0xFF14253A),
    onSecondaryContainer = Color(0xFFCFE6FF),

    tertiary = Color(0xFFF4BC4E),         // amber: pending / due
    onTertiary = Color(0xFF2B1B00),
    tertiaryContainer = Color(0xFF2B2210),
    onTertiaryContainer = Color(0xFFFFE6B0),

    error = Color(0xFFFF8672),            // coral: failing / gap
    onError = Color(0xFF3A0B04),
    errorContainer = Color(0xFF331A1A),
    onErrorContainer = Color(0xFFFFD9D2),

    background = Color(0xFF0A0C0D),
    onBackground = Color(0xFFEAEFEC),
    surface = Color(0xFF0A0C0D),
    onSurface = Color(0xFFEAEFEC),
    surfaceVariant = Color(0xFF181C1F),
    onSurfaceVariant = Color(0xFF98A39D),
    surfaceContainerLowest = Color(0xFF070909),
    surfaceContainerLow = Color(0xFF0E1112),
    surfaceContainer = Color(0xFF111416),
    surfaceContainerHigh = Color(0xFF181C1F),
    surfaceContainerHighest = Color(0xFF20252A),
    outline = Color(0xFF3B434A),
    outlineVariant = Color(0xFF2A3035)
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
