package com.hhst.youtubelite.watch.presentation

import com.hhst.youtubelite.extension.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowPaletteTest {

    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF
    private fun lightness(c: Int): Float {
        val hi = maxOf(red(c), green(c), blue(c)) / 255f
        val lo = minOf(red(c), green(c), blue(c)) / 255f
        return (hi + lo) / 2f
    }

    /** 32x18 frame: left quarter [left], middle quarter [center], right quarter [right], grey elsewhere. */
    private fun frame(left: Int, center: Int, right: Int): IntArray {
        val w = GlowPalette.SAMPLE_WIDTH
        val h = GlowPalette.SAMPLE_HEIGHT
        return IntArray(w * h) { i ->
            when (i % w) {
                in 0 until 8 -> left
                in 12 until 20 -> center
                in 24 until 32 -> right
                else -> 0xFF808080.toInt()
            }
        }
    }

    private fun hslSaturation(c: Int): Float {
        val hi = maxOf(red(c), green(c), blue(c)) / 255f
        val lo = minOf(red(c), green(c), blue(c)) / 255f
        val l = (hi + lo) / 2f
        return if (hi == lo) 0f else if (l > .5f) (hi - lo) / (2f - hi - lo) else (hi - lo) / (hi + lo)
    }

    @Test fun takesLeftCentreAndRightAveragesSeparately() {
        val raw = GlowPalette.extract(frame(0xFFC02020.toInt(), 0xFF20A020.toInt(), 0xFF2040C0.toInt()),
            GlowPalette.SAMPLE_WIDTH, GlowPalette.SAMPLE_HEIGHT)!!
        assertEquals(0xFFC02020.toInt(), raw.left)
        assertEquals(0xFF20A020.toInt(), raw.center)
        assertEquals(0xFF2040C0.toInt(), raw.right)
        for (dark in listOf(true, false)) {
            val colors = GlowPalette.forTheme(raw, dark)
            assertTrue(red(colors.left) > green(colors.left) && red(colors.left) > blue(colors.left))
            assertTrue(green(colors.center) > red(colors.center) && green(colors.center) > blue(colors.center))
            assertTrue(blue(colors.right) > red(colors.right) && blue(colors.right) > green(colors.right))
        }
    }

    @Test fun vibranceLiftsAnyHueIntoTheColouredBand() {
        // A muted green (saturation ~0.11) and a vivid pink both end up clearly coloured.
        for (rgb in listOf(0xFF6E8A6E.toInt(), 0xFFFF40A0.toInt(), 0xFF406080.toInt())) for (dark in listOf(true, false)) {
            val tuned = GlowPalette.tune(rgb, dark)
            assertTrue("saturation of ${Integer.toHexString(tuned)}", hslSaturation(tuned) in 0.53f..0.77f)
        }
    }

    @Test fun clampsLightnessToTheGlowBand() {
        // Blue keeps the full band on dark (its luminance is low enough for the Ink check).
        val darkBlue = GlowPalette.tune(0xFF000040.toInt(), dark = true)
        assertTrue(lightness(darkBlue) in 0.44f..0.61f)
        val paleBlue = GlowPalette.tune(0xFFC0D0FF.toInt(), dark = true)
        assertTrue(lightness(paleBlue) <= 0.61f)
        val light = GlowPalette.tune(0xFF101820.toInt(), dark = false)
        assertTrue(lightness(light) >= 0.44f)
    }

    @Test fun greyStaysGrey() {
        for (dark in listOf(true, false)) {
            val tuned = GlowPalette.tune(0xFF777777.toInt(), dark)
            assertEquals(red(tuned), green(tuned))
            assertEquals(green(tuned), blue(tuned))
        }
    }

    /** Title text sits on the glow: the page Ink keeps WCAG 4.5:1 at the peak alpha for every hue. */
    @Test fun inkKeepsContrastOnTheGlowAtPeakForEveryHue() {
        val inputs = buildList {
            for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) add((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            add(0xFFFFFF00.toInt()); add(0xFF00FFFF.toInt()); add(0xFF80FF00.toInt()); add(0xFFFFFFFF.toInt())
        }
        for (dark in listOf(true, false)) for (rgb in inputs) {
            val glow = GlowPalette.tune(rgb, dark)
            val ratio = GlowPalette.contrast(GlowPalette.ink(dark),
                GlowPalette.blend(GlowPalette.background(dark), glow, GlowPalette.peak(dark)))
            assertTrue("dark=$dark ${Integer.toHexString(rgb)} -> ${Integer.toHexString(glow)}: $ratio", ratio >= 4.5)
        }
        assertEquals(0.60f, GlowPalette.peak(true), 0.001f)
        assertEquals(0.28f, GlowPalette.peak(false), 0.001f)
    }

    @Test fun contrastMathMatchesWcag() {
        assertEquals(21.0, GlowPalette.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, GlowPalette.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.0001)
    }

    @Test fun rejectsTooSmallInput() {
        assertNull(GlowPalette.extract(IntArray(0), 0, 0))
        assertNull(GlowPalette.extract(IntArray(4), 32, 18))
    }

    @Test fun thumbnailCandidatesPreferArtworkThenStandardThumbnail() {
        assertEquals(listOf("https://x/a.jpg", "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"),
            glowThumbnailUrls("abcdefghijk", listOf(null, "", "https://x/a.jpg", "https://x/a.jpg")))
        assertEquals(emptyList<String>(), glowThumbnailUrls("fixture", listOf(null)))
    }

    @Test fun glowSwitchDefaultsOn() {
        assertEquals(true, PreferenceKeys.DEFAULTS[PreferenceKeys.PLAYER_GLOW])
    }
}
