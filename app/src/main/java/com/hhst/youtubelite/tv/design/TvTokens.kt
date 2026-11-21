package com.hhst.youtubelite.tv.design

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hhst.youtubelite.ui.design.MinFonts
import com.hhst.youtubelite.ui.design.MintColors

/**
 * "Signal" for ten feet: dark only, cyan accent, brand gradient reserved for the seek bar.
 * Values reuse the phone's dark palette so both apps read as one brand.
 */
object TvColors {
    val Background = Color(MintColors.DarkBackground)
    val Panel = Color(MintColors.DarkPanel)
    val Surface = Color(MintColors.DarkSurface)
    val Ghost = Color(MintColors.DarkGhost)
    val Ink = Color(MintColors.DarkInk)
    val Secondary = Color(MintColors.DarkSecondary)
    val Accent = Color(MintColors.DarkAccent)
    val OnAccent = Color(MintColors.DarkOnAccent)
    val AccentContainer = Color(MintColors.DarkAccentContainer)
    val Danger = Color(MintColors.DarkDanger)
    val Live = Color(0xFFFF4E45)
    /** Video overlays: legible text over any frame. */
    val Scrim = Color(0xE60A0A0A)
    val ScrimSoft = Color(0x990A0A0A)
    val GradientStart = Color(MintColors.GradientStart)
    val GradientEnd = Color(MintColors.GradientEnd)
    val BrandGradient: Brush get() = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
}

/** TV type scale (sp): Display 36, Title 24, Body 18, Caption 15. */
object TvType {
    val Display = TextStyle(fontFamily = MinFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp)
    val Title = TextStyle(fontFamily = MinFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp)
    val Body = TextStyle(fontFamily = MinFonts.Body, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 24.sp)
    val BodyStrong = Body.copy(fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontFamily = MinFonts.Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)
    val Label = TextStyle(fontFamily = MinFonts.Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
}

/** Overscan-safe margins and the spacing grid. */
object TvSpacing {
    val OverscanHorizontal = 48.dp
    val OverscanVertical = 27.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Gap between rows on browse screens. */
    val RowGap = 28.dp
    /** Gap between cards in a row; leaves room for the 1.08 focus scale. */
    val CardGap = 20.dp
}

object TvSizes {
    val CardWidth = 256.dp
    val ShortCardWidth = 140.dp
    val RailCollapsed = 72.dp
    val RailExpanded = 232.dp
    val RailItemHeight = 48.dp
    val ButtonHeight = 44.dp
    val ChipHeight = 40.dp
    val Corner = 12.dp
    val SmallCorner = 8.dp
}

/** Focus is the affordance: scale, a 3 dp cyan border, a soft glow, and dimmed neighbours. */
object TvFocus {
    const val Scale = 1.08f
    val BorderWidth = 3.dp
    val GlowElevation = 12.dp
    /** Unfocused card content alpha. */
    const val UnfocusedAlpha = 0.72f
    /** Focus dwell before speculative work (warming a video) starts. */
    const val DwellMs = 600L
}
