package com.hhst.youtubelite.ui.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * "Mint" palette (docs/UI_PLAN.md 2.1). This is the single source of truth: the Material3
 * scheme in `ui/theme` and the XML window colors mirror these values.
 */
data class AppPalette(
    val background: Color,
    /** Bars, sheets, cards, dialogs. */
    val panel: Color,
    val ink: Color,
    val secondary: Color,
    /** Secondary container: chips, fields, skeleton base. */
    val surface: Color,
    /** Stronger state container: selected rows, skeleton highlight, banners. */
    val ghost: Color,
    val border: Color,
    val navigationDivider: Color,
    val scrim: Color,
    val menuScrim: Color,
    /** Immediate press response under icons and rows. */
    val stateLayer: Color,
    /** Brand accent for fills, icons and text of 18sp or more. */
    val accent: Color,
    val onAccent: Color,
    /** Selected chip, tonal button, navigation indicator. */
    val accentContainer: Color,
    /** Accent that keeps 4.5:1 on Background, Panel, Surface and AccentContainer; for small text. */
    val accentText: Color,
    /** Errors, destructive actions, live badge. */
    val danger: Color,
    val onDanger: Color,
    val link: Color,
) {
    @Deprecated("Renamed: the brand accent is no longer red.", ReplaceWith("accent"))
    val red: Color get() = accent
}

/** Raw ARGB values, shared with [com.hhst.youtubelite.ui.theme] and with non-Compose window code. */
object MintColors {
    const val LightBackground = 0xFFFAFBFA
    const val LightPanel = 0xFFFFFFFF
    const val LightSurface = 0xFFEEF2F0
    const val LightGhost = 0xFFE2E8E5
    const val LightInk = 0xFF101514
    const val LightSecondary = 0xFF56615E
    /** UI_PLAN lists #0B8F6A; darkened one step so white text on it reaches 4.5:1 (was 4.08:1). */
    const val LightAccent = 0xFF0A8663
    const val LightOnAccent = 0xFFFFFFFF
    const val LightAccentContainer = 0xFFD6F3E8
    const val LightAccentText = 0xFF087456
    const val LightDanger = 0xFFC4321F
    const val LightOnDanger = 0xFFFFFFFF
    const val LightLink = 0xFF0B62C4
    const val LightOutline = 0xFFC9D2CE

    const val DarkBackground = 0xFF0D1110
    const val DarkPanel = 0xFF141918
    const val DarkSurface = 0xFF1D2423
    const val DarkGhost = 0xFF27302E
    const val DarkInk = 0xFFEDF2F0
    const val DarkSecondary = 0xFFA3AFAB
    const val DarkAccent = 0xFF4FD8AE
    const val DarkOnAccent = 0xFF06231A
    const val DarkAccentContainer = 0xFF123A2E
    const val DarkAccentText = 0xFF4FD8AE
    const val DarkDanger = 0xFFFF8A75
    const val DarkOnDanger = 0xFF3A0D05
    const val DarkLink = 0xFF7DB8FF
    const val DarkOutline = 0xFF3A4543
}

val LightAppPalette = AppPalette(
    background = Color(MintColors.LightBackground),
    panel = Color(MintColors.LightPanel),
    ink = Color(MintColors.LightInk),
    secondary = Color(MintColors.LightSecondary),
    surface = Color(MintColors.LightSurface),
    ghost = Color(MintColors.LightGhost),
    border = Color(MintColors.LightInk).copy(alpha = 0.10f),
    navigationDivider = Color(MintColors.LightInk).copy(alpha = 0.10f),
    scrim = Color.Black.copy(alpha = 0.3f),
    menuScrim = Color.Black.copy(alpha = 0.6f),
    stateLayer = Color.Black.copy(alpha = 0.08f),
    accent = Color(MintColors.LightAccent),
    onAccent = Color(MintColors.LightOnAccent),
    accentContainer = Color(MintColors.LightAccentContainer),
    accentText = Color(MintColors.LightAccentText),
    danger = Color(MintColors.LightDanger),
    onDanger = Color(MintColors.LightOnDanger),
    link = Color(MintColors.LightLink),
)

val DarkAppPalette = AppPalette(
    background = Color(MintColors.DarkBackground),
    panel = Color(MintColors.DarkPanel),
    ink = Color(MintColors.DarkInk),
    secondary = Color(MintColors.DarkSecondary),
    surface = Color(MintColors.DarkSurface),
    ghost = Color(MintColors.DarkGhost),
    border = Color(MintColors.DarkInk).copy(alpha = 0.12f),
    navigationDivider = Color(MintColors.DarkInk).copy(alpha = 0.12f),
    scrim = Color.Black.copy(alpha = 0.5f),
    menuScrim = Color.Black.copy(alpha = 0.7f),
    stateLayer = Color.White.copy(alpha = 0.08f),
    accent = Color(MintColors.DarkAccent),
    onAccent = Color(MintColors.DarkOnAccent),
    accentContainer = Color(MintColors.DarkAccentContainer),
    accentText = Color(MintColors.DarkAccentText),
    danger = Color(MintColors.DarkDanger),
    onDanger = Color(MintColors.DarkOnDanger),
    link = Color(MintColors.DarkLink),
)

val LocalAppColors = staticCompositionLocalOf { LightAppPalette }
