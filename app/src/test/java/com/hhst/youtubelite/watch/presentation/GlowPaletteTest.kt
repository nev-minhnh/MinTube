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

    @Test fun takesLeftCentreAndRightAveragesSeparately() {
        val colors = GlowPalette.extract(frame(0xFFC02020.toInt(), 0xFF20A020.toInt(), 0xFF2040C0.toInt()),
            GlowPalette.SAMPLE_WIDTH, GlowPalette.SAMPLE_HEIGHT)!!
        assertTrue(red(colors.left) > green(colors.left) && red(colors.left) > blue(colors.left))
        assertTrue(green(colors.center) > red(colors.center) && green(colors.center) > blue(colors.center))
        assertTrue(blue(colors.right) > red(colors.right) && blue(colors.right) > green(colors.right))
        assertEquals(0xFF, colors.left ushr 24)
    }

    @Test fun clampsLightnessSoBlackStaysVisibleAndWhiteIsNotNeon() {
        val colors = GlowPalette.extract(frame(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF101010.toInt()),
            GlowPalette.SAMPLE_WIDTH, GlowPalette.SAMPLE_HEIGHT)!!
        assertTrue(lightness(colors.left) >= 0.29f)
        assertTrue(lightness(colors.center) <= 0.63f)
        assertTrue(lightness(colors.right) in 0.29f..0.63f)
    }

    @Test fun boostsSaturationSlightlyAndKeepsHue() {
        val muted = 0xFF806060.toInt()
        val tuned = GlowPalette.tune(muted)
        fun saturation(c: Int): Float {
            val hi = maxOf(red(c), green(c), blue(c)) / 255f
            val lo = minOf(red(c), green(c), blue(c)) / 255f
            val l = (hi + lo) / 2f
            return if (hi == lo) 0f else if (l > .5f) (hi - lo) / (2f - hi - lo) else (hi - lo) / (hi + lo)
        }
        assertTrue(saturation(tuned) > saturation(muted))
        assertTrue(saturation(tuned) <= 0.86f)
        assertTrue(red(tuned) > green(tuned) && green(tuned) == blue(tuned))
    }

    @Test fun greyStaysGrey() {
        val tuned = GlowPalette.tune(0xFF777777.toInt())
        assertEquals(red(tuned), green(tuned))
        assertEquals(green(tuned), blue(tuned))
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
