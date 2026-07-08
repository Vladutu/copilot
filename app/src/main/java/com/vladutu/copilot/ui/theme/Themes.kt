package com.vladutu.copilot.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Decorative extras a theme may carry beyond colors/typography. Screens render them
 * only when present, so themes without accents (Default) cost nothing.
 */
data class ThemeAccents(
    /** BMW M tricolor, drawn by [MTricolor][com.vladutu.copilot.ui.MTricolor]; null = no stripes. */
    val stripes: List<Color>? = null,
    /**
     * Full-screen faded background wash, drawn by
     * [TricolorSweep][com.vladutu.copilot.ui.TricolorSweep]; null = flat background.
     * Separate from [stripes] so a theme can carry the badge without the wash.
     */
    val sweep: List<Color>? = null,
    /**
     * Top-center radial glow tint behind everything, drawn by
     * [BackgroundGlow][com.vladutu.copilot.ui.BackgroundGlow]; null = flat background.
     */
    val glow: Color? = null,
)

/**
 * A self-contained visual theme. Adding a theme = one new spec + an [AllThemes] entry;
 * the Settings dropdown, persistence fallback, and accent rendering all derive from
 * the list. Screens stay theme-agnostic and keep reading MaterialTheme.
 */
data class ThemeSpec(
    /** Persisted in DataStore — lowercase, stable across releases. */
    val id: String,
    /** Shown in the Settings dropdown. */
    val label: String,
    val colorScheme: ColorScheme,
    val typography: Typography,
    val accents: ThemeAccents = ThemeAccents(),
)

val DefaultTheme = ThemeSpec(
    id = "default",
    label = "Default",
    colorScheme = darkColorScheme(
        primary = PilotPrimary,
        onPrimary = PilotOnPrimary,
        secondary = PilotPrimary,
        onSecondary = PilotOnPrimary,
        tertiary = PilotPrimary,
        onTertiary = PilotOnPrimary,
        background = PilotBackground,
        onBackground = PilotOnSurface,
        surface = PilotSurface,
        onSurface = PilotOnSurface,
        surfaceVariant = PilotSurfaceVariant,
        onSurfaceVariant = PilotOnSurfaceVariant,
        outline = PilotOutline,
        outlineVariant = PilotOutline,
        error = PilotError,
        onError = PilotOnPrimary,
    ),
    typography = PilotTypography,
)

val BmwTheme = ThemeSpec(
    id = "bmw",
    label = "BMW",
    colorScheme = darkColorScheme(
        primary = BmwPrimary,
        onPrimary = BmwOnPrimary,
        secondary = BmwPrimary,
        onSecondary = BmwOnPrimary,
        tertiary = BmwPrimary,
        onTertiary = BmwOnPrimary,
        background = BmwBackground,
        onBackground = BmwOnSurface,
        surface = BmwSurface,
        onSurface = BmwOnSurface,
        surfaceVariant = BmwSurfaceVariant,
        onSurfaceVariant = BmwOnSurfaceVariant,
        outline = BmwOutline,
        outlineVariant = BmwOutline,
        error = PilotError,
        onError = BmwOnPrimary,
    ),
    typography = BmwTypography,
    accents = ThemeAccents(
        stripes = listOf(BmwStripeLightBlue, BmwStripeDarkBlue, BmwStripeRed),
        sweep = listOf(BmwStripeLightBlue, BmwStripeDarkBlue, BmwStripeRed),
        glow = BmwGlow,
    ),
)

val AllThemes = listOf(DefaultTheme, BmwTheme)

/** Unknown/legacy ids fall back to Default, so downgrades never crash. */
fun themeById(id: String?): ThemeSpec = AllThemes.firstOrNull { it.id == id } ?: DefaultTheme

val LocalThemeSpec = staticCompositionLocalOf { DefaultTheme }
