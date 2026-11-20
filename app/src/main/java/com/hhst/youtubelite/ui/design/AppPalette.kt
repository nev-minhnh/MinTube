package com.hhst.youtubelite.ui.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * "Signal" palette v2 (tools/tasks/palette.md). This is the single source of truth: the Material3
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
    /** Brand gradient, seek bar and mini-player progress only. */
    const val GradientStart = 0xFF2EC8F0
    const val GradientEnd = 0xFFFF3D86

    const val LightBackground = 0xFFFAFAFB
    const val LightPanel = 0xFFFFFFFF
    const val LightSurface = 0xFFF1F2F4
    const val LightGhost = 0xFFE6E8EB
    const val LightInk = 0xFF0E0F12
    const val LightSecondary = 0xFF5B6068
        const val LightAccent = 0xFF0A7EA4
    const val LightOnAccent = 0xFFFFFFFF
    const val LightAccentContainer = 0xFFD9F3FB
    const val LightAccentText = 0xFF08708F
    const val LightDanger = 0xFFC8321F
    const val LightOnDanger = 0xFFFFFFFF
    const val LightLink = 0xFF0A62C2
    const val LightOutline = 0xFFCDD0D5

    const val DarkBackground = 0xFF0A0A0A
    const val DarkPanel = 0xFF121316
    const val DarkSurface = 0xFF1C1D21
    const val DarkGhost = 0xFF26282D
    const val DarkInk = 0xFFEEF0F3
    const val DarkSecondary = 0xFFA2A7B0
    const val DarkAccent = 0xFF4DD4F5
    const val DarkOnAccent = 0xFF04222C
    const val DarkAccentContainer = 0xFF0E3442
    const val DarkAccentText = 0xFF6FDDF7
    const val DarkDanger = 0xFFFF8A75
    const val DarkOnDanger = 0xFF2A0904
    const val DarkLink = 0xFF7DB8FF
    const val DarkOutline = 0xFF3A3D43
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
