package com.hhst.youtubelite.ui.design

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.hhst.youtubelite.R

/** Bundled families. Scripts a family lacks fall back to the system font. */
object MinFonts {
    /** Be Vietnam Pro (OFL): screen titles, section headers, the wordmark. */
    val Display = FontFamily(
        Font(R.font.be_vietnam_pro_medium, FontWeight.Normal),
        Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
        Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
        Font(R.font.be_vietnam_pro_semibold, FontWeight.Bold),
        Font(R.font.be_vietnam_pro_semibold, FontWeight.Black),
    )
    /** Roboto: body text and metadata. */
    val Body = FontFamily(
        Font(R.font.roboto_regular, FontWeight.Normal), Font(R.font.roboto_medium, FontWeight.Medium),
        Font(R.font.roboto_bold, FontWeight.Bold),
        Font(R.font.roboto_bold, FontWeight.Black),
    )

    /** Sizes from which a medium-or-heavier text is treated as a title. */
    const val TitleFromSp = 18

    fun familyFor(size: Int, weight: FontWeight): FontFamily =
        if (size >= TitleFromSp && weight.weight >= FontWeight.Medium.weight) Display else Body
}

/**
 * The named type scale (docs/UI_PLAN.md 2.2). Sizes are `sp` and follow the system font scale.
 * Styles carry no color; pass one to `AppText(text, style, color = ...)`.
 */
object MinType {
    private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    /** 24/30 SemiBold: screen titles, empty-state headline. */
    val Display = style(MinFonts.Display, 24, 30, FontWeight.SemiBold)
    /** 18/24 SemiBold: section headers, watch title. */
    val Title = style(MinFonts.Display, 18, 24, FontWeight.SemiBold)
    /** 16/22 Medium: large card title, row title. */
    val Subtitle = style(MinFonts.Display, 16, 22, FontWeight.Medium)
    /** 14/20 Regular: default text, compact card title. */
    val Body = style(MinFonts.Body, 14, 20, FontWeight.Normal)
    /** 13/18 Medium: buttons, chips. */
    val Label = style(MinFonts.Body, 13, 18, FontWeight.Medium)
    /** 12/16 Regular: metadata, timestamps. */
    val Caption = style(MinFonts.Body, 12, 16, FontWeight.Normal)
    /** 11/14 Medium: navigation labels, badges. */
    val Micro = style(MinFonts.Body, 11, 14, FontWeight.Medium)
    /** 22/28 SemiBold: the brand wordmark. */
    val Wordmark = style(MinFonts.Display, 22, 28, FontWeight.SemiBold)
}
