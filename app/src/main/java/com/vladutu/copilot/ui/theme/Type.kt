package com.vladutu.copilot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vladutu.copilot.R

val PilotTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

private val Saira = FontFamily(
    Font(R.font.saira_regular, FontWeight.Normal),
    Font(R.font.saira_medium, FontWeight.Medium),
)

private val SairaCondensed = FontFamily(
    Font(R.font.saira_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.saira_condensed_bold, FontWeight.Bold),
)

// BMW cockpit typography (docs/img.png): Saira Condensed for headings/titles/caps
// labels, Saira for body. Same metrics as PilotTypography so nothing reflows.
// Styles PilotTypography leaves at Material defaults are pinned here too for every
// consumer in the app: headlineLarge (PermissionGate), headlineMedium (now-playing
// strip, Status), headlineSmall (slider −/+), labelMedium (section titles),
// labelSmall (liked-count badge), bodySmall (version). SairaCondensed only bundles
// SemiBold/Bold, so condensed styles declare SemiBold explicitly rather than
// letting lighter requests silently snap to it.
private val M3Defaults = Typography()

val BmwTypography = Typography(
    titleLarge = PilotTypography.titleLarge.copy(fontFamily = SairaCondensed, fontWeight = FontWeight.Bold),
    titleMedium = PilotTypography.titleMedium.copy(fontFamily = SairaCondensed),
    titleSmall = PilotTypography.titleSmall.copy(fontFamily = SairaCondensed),
    labelLarge = PilotTypography.labelLarge.copy(fontFamily = SairaCondensed, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    bodyLarge = PilotTypography.bodyLarge.copy(fontFamily = Saira),
    bodyMedium = PilotTypography.bodyMedium.copy(fontFamily = Saira),
    headlineLarge = M3Defaults.headlineLarge.copy(fontFamily = SairaCondensed, fontWeight = FontWeight.SemiBold),
    headlineMedium = M3Defaults.headlineMedium.copy(fontFamily = SairaCondensed, fontWeight = FontWeight.SemiBold),
    headlineSmall = M3Defaults.headlineSmall.copy(fontFamily = SairaCondensed, fontWeight = FontWeight.SemiBold),
    labelMedium = M3Defaults.labelMedium.copy(fontFamily = SairaCondensed, fontWeight = FontWeight.SemiBold),
    labelSmall = M3Defaults.labelSmall.copy(fontFamily = SairaCondensed, fontWeight = FontWeight.SemiBold),
    bodySmall = M3Defaults.bodySmall.copy(fontFamily = Saira),
)
