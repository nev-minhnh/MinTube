package com.hhst.youtubelite.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG 2.1 contrast of the Signal palette, both themes (tools/tasks/palette.md). */
class MintContrastTest {
    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground.compositeOver(background))
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrast(foreground, background)
        assertTrue("$name is %.2f:1, needs $minimum:1".format(ratio), ratio >= minimum)
    }

    private val palettes = mapOf("light" to LightAppPalette, "dark" to DarkAppPalette)

    @Test
    fun textOnPageAndPanelMeetsBodyContrast() = palettes.forEach { (theme, p) ->
        assertContrast("$theme ink/background", p.ink, p.background, 4.5)
        assertContrast("$theme ink/panel", p.ink, p.panel, 4.5)
        assertContrast("$theme secondary/background", p.secondary, p.background, 4.5)
        assertContrast("$theme secondary/panel", p.secondary, p.panel, 4.5)
    }

    @Test
    fun textOnContainersMeetsBodyContrast() = palettes.forEach { (theme, p) ->
        assertContrast("$theme ink/surface", p.ink, p.surface, 4.5)
        assertContrast("$theme secondary/surface", p.secondary, p.surface, 4.5)
        assertContrast("$theme secondary/ghost", p.secondary, p.ghost, 4.5)
        assertContrast("$theme ink/accentContainer", p.ink, p.accentContainer, 4.5)
    }

    @Test
    fun accentMeetsItsTargets() = palettes.forEach { (theme, p) ->
        assertContrast("$theme onAccent/accent", p.onAccent, p.accent, 4.5)
        assertContrast("$theme accent/panel", p.accent, p.panel, 3.0)
        assertContrast("$theme accent/background", p.accent, p.background, 3.0)
        assertContrast("$theme accentText/panel", p.accentText, p.panel, 4.5)
        assertContrast("$theme accentText/background", p.accentText, p.background, 4.5)
        assertContrast("$theme accentText/surface", p.accentText, p.surface, 4.5)
        assertContrast("$theme accentText/accentContainer", p.accentText, p.accentContainer, 4.5)
    }

    @Test
    fun dangerAndLinkMeetBodyContrast() = palettes.forEach { (theme, p) ->
        assertContrast("$theme danger/panel", p.danger, p.panel, 4.5)
        assertContrast("$theme danger/background", p.danger, p.background, 4.5)
        assertContrast("$theme onDanger/danger", p.onDanger, p.danger, 4.5)
        assertContrast("$theme link/panel", p.link, p.panel, 4.5)
        assertContrast("$theme link/background", p.link, p.background, 4.5)
    }

    @Test
    fun accentIsNotYoutubeRed() = palettes.values.forEach { p ->
        val banned = listOf(Color(0xFFFF0000), Color(0xFFFF0033), Color(0xFFBB202D))
        listOf(p.accent, p.accentText, p.accentContainer, p.danger).forEach { assertTrue(it !in banned) }
    }

    @Test
    fun deprecatedRedAliasesAccent() = palettes.values.forEach { p ->
        @Suppress("DEPRECATION")
        assertEquals(p.accent, p.red)
    }
}
