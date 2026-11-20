package com.hhst.youtubelite.watch.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.core.YoutubeThumbnail
import com.hhst.youtubelite.ui.design.DesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * The glow behind the embedded player: three colours taken once per video from its thumbnail
 * (left edge, centre, right edge), spread as a soft light above and below the player slot.
 * Pure presentation; it never reads the player surface and never changes a measured size.
 */
@Immutable
data class GlowColors(val left: Int, val center: Int, val right: Int)

object GlowPalette {
    /** Sample size the thumbnail is scaled to before [extract]. */
    const val SAMPLE_WIDTH = 32
    const val SAMPLE_HEIGHT = 18

    /** Below this HSL saturation a colour counts as grey and stays grey. */
    const val GREY_SATURATION = 0.08f
    /** Vibrance: any real hue lands in [MIN_SATURATION, MAX_SATURATION]. */
    const val MIN_SATURATION = 0.55f
    const val MAX_SATURATION = 0.75f
    const val MIN_LIGHTNESS = 0.45f
    const val MAX_LIGHTNESS = 0.60f
    /** Peak glow alpha right under the player. */
    const val DARK_PEAK = 0.60f
    const val LIGHT_PEAK = 0.28f
    /** WCAG ratio the page's Ink must keep on the glow at its peak (title text sits on it). */
    const val MIN_CONTRAST = 4.5

    private val DARK_BACKGROUND = com.hhst.youtubelite.ui.design.MintColors.DarkBackground.toInt()
    private val DARK_INK = com.hhst.youtubelite.ui.design.MintColors.DarkInk.toInt()
    private val LIGHT_BACKGROUND = com.hhst.youtubelite.ui.design.MintColors.LightBackground.toInt()
    private val LIGHT_INK = com.hhst.youtubelite.ui.design.MintColors.LightInk.toInt()

    fun background(dark: Boolean) = if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND
    fun ink(dark: Boolean) = if (dark) DARK_INK else LIGHT_INK
    fun peak(dark: Boolean) = if (dark) DARK_PEAK else LIGHT_PEAK

    /**
     * Raw left-edge (first quarter), centre (middle quarter) and right-edge (last quarter) averages
     * of an ARGB pixel array; [forTheme] turns them into glow colours. Null for an empty/short array.
     */
    fun extract(pixels: IntArray, width: Int, height: Int): GlowColors? {
        if (width < 4 || height < 1 || pixels.size < width * height) return null
        val quarter = max(1, width / 4)
        val centerStart = (width - quarter) / 2
        fun average(from: Int, until: Int): Int {
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (y in 0 until height) for (x in from until until) {
                val p = pixels[y * width + x]
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
            }
            return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
        return GlowColors(average(0, quarter), average(centerStart, centerStart + quarter), average(width - quarter, width))
    }

    fun forTheme(raw: GlowColors, dark: Boolean) = GlowColors(tune(raw.left, dark), tune(raw.center, dark), tune(raw.right, dark))

    /**
     * Vibrance boost (any hue to 0.55-0.75 saturation, greys stay grey), lightness clamped to
     * 0.45-0.6, then lightness nudged (darker on a dark page, lighter on a light one) until the
     * page's Ink keeps [MIN_CONTRAST] on the glow at its peak alpha.
     */
    fun tune(rgb: Int, dark: Boolean): Int {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val hi = max(r, max(g, b)); val lo = min(r, min(g, b))
        val l = (hi + lo) / 2f
        val d = hi - lo
        var h = 0f
        var s = 0f
        if (d > 0f) {
            s = if (l > 0.5f) d / (2f - hi - lo) else d / (hi + lo)
            h = when (hi) {
                r -> ((g - b) / d + (if (g < b) 6f else 0f))
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } / 6f
        }
        val sat = if (s < GREY_SATURATION) s else min(MAX_SATURATION, MIN_SATURATION + 0.4f * s)
        var light = l.coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS)
        var out = hsl(h, sat, light)
        repeat(60) {
            if (inkContrast(out, dark) >= MIN_CONTRAST) return out
            light = (light + if (dark) -0.01f else 0.01f).coerceIn(0f, 1f)
            out = hsl(h, sat, light)
        }
        return out
    }

    /** Contrast of the page Ink over the glow colour composited at its peak alpha on the page background. */
    fun inkContrast(glow: Int, dark: Boolean): Double = contrast(ink(dark), blend(background(dark), glow, peak(dark)))

    fun blend(under: Int, over: Int, alpha: Float): Int {
        fun ch(shift: Int) = ((((under shr shift) and 0xFF) * (1f - alpha) + ((over shr shift) and 0xFF) * alpha) + 0.5f).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun luminance(rgb: Int): Double {
        fun lin(v: Int): Double { val c = v / 255.0; return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin((rgb shr 16) and 0xFF) + 0.7152 * lin((rgb shr 8) and 0xFF) + 0.0722 * lin(rgb and 0xFF)
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun hsl(h: Float, s: Float, l: Float): Int {
        fun channel(t0: Float, p: Float, q: Float): Float {
            var t = t0
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 0.5f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        val (r, g, b) = if (s == 0f) Triple(l, l, l) else {
            val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            Triple(channel(h + 1f / 3f, p, q), channel(h, p, q), channel(h - 1f / 3f, p, q))
        }
        fun byte(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }
}

/** One extraction per video id; the last few are kept so going back to a video reuses them. */
internal object GlowCache {
    private val entries = object : LinkedHashMap<String, GlowColors>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GlowColors>) = size > 16
    }
    fun get(videoId: String): GlowColors? = synchronized(entries) { entries[videoId] }
    fun put(videoId: String, colors: GlowColors) = synchronized(entries) { entries[videoId] = colors }
}

private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

/** Thumbnail candidates for a video: page/card artwork first, then the standard ytimg one. */
internal fun glowThumbnailUrls(videoId: String, artwork: List<String?>): List<String> = buildList {
    artwork.forEach { if (!it.isNullOrBlank() && it !in this) add(it) }
    if (VIDEO_ID.matches(videoId)) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg".let { if (it !in this) add(it) }
}

/**
 * Colours for [videoId], computed once from the thumbnail the app already loads (reused from the
 * memory cache when the page or transition decoded it). Null until known, or when it cannot load.
 */
@Composable
fun rememberGlowColors(videoId: String, artwork: List<String?>): State<GlowColors?> {
    // Not keyed by video: the previous colours stay until the next video's are known (crossfade).
    val result = remember { mutableStateOf(GlowCache.get(videoId)) }
    val urls = glowThumbnailUrls(videoId, artwork)
    LaunchedEffect(videoId, urls.isNotEmpty()) {
        GlowCache.get(videoId)?.let { result.value = it; return@LaunchedEffect }
        if (urls.isEmpty()) return@LaunchedEffect
        val colors = withContext(Dispatchers.Default) {
            val bitmap = urls.firstNotNullOfOrNull { YoutubeThumbnail.cachedForDisplay(it) }
                ?: urls.firstNotNullOfOrNull { YoutubeThumbnail.await(it, 320) }
                ?: return@withContext null
            if (bitmap.isRecycled) return@withContext null
            val small = android.graphics.Bitmap.createScaledBitmap(bitmap,
                GlowPalette.SAMPLE_WIDTH, GlowPalette.SAMPLE_HEIGHT, true)
            val pixels = IntArray(GlowPalette.SAMPLE_WIDTH * GlowPalette.SAMPLE_HEIGHT)
            small.getPixels(pixels, 0, GlowPalette.SAMPLE_WIDTH, 0, 0, GlowPalette.SAMPLE_WIDTH, GlowPalette.SAMPLE_HEIGHT)
            if (small !== bitmap) small.recycle()
            GlowPalette.extract(pixels, GlowPalette.SAMPLE_WIDTH, GlowPalette.SAMPLE_HEIGHT)
        }
        // A thumbnail that cannot load means no glow for this video.
        colors?.let { GlowCache.put(videoId, it) }
        result.value = colors
    }
    return result
}

/**
 * What the page needs to paint the glow: the player slot in root coordinates, the (animated)
 * colours and the visible strength. Read only inside draw lambdas, so scrolling and crossfades
 * redraw without recomposing the page.
 */
@Stable
class WatchGlowPaint internal constructor(
    private val slot: () -> Rect?,
    private val left: State<Color>,
    private val center: State<Color>,
    private val right: State<Color>,
    private val strength: () -> Float,
) {
    /**
     * Paints the glow into this draw scope, whose top-left sits at [origin] in root coordinates:
     * one soft layer around the slot, coloured left -> centre -> right across the player's width,
     * fading smoothly over [GlowBelowDp] below, [GlowAboveDp] above and [GlowSideDp] to the start
     * side. It never spreads past the player's right edge, so in the tablet two-column layout it
     * stays inside the player's column.
     */
    fun draw(scope: DrawScope, origin: Offset) = with(scope) {
        val rect = slot()?.translate(-origin.x, -origin.y) ?: return@with
        val alpha = strength()
        if (alpha <= 0.001f || rect.width <= 0f || rect.height <= 0f) return@with
        val above = min(GlowAboveDp.dp.toPx(), max(0f, rect.top + origin.y))
        val below = GlowBelowDp.dp.toPx()
        val side = min(GlowSideDp.dp.toPx(), max(0f, rect.left + origin.x))
        val y0 = rect.top - above
        val y1 = rect.bottom + below
        val x0 = rect.left - side
        val bounds = Rect(max(x0, 0f), max(y0, 0f), min(rect.right, size.width), min(y1, size.height))
        if (bounds.width <= 0f || bounds.height <= 0f) return@with
        val total = y1 - y0
        fun at(y: Float) = ((y - y0) / total).coerceIn(0f, 1f)
        fun ink(a: Float) = Color.Black.copy(alpha = a)
        // Eased (roughly quadratic) fall-off: no visible band edge anywhere.
        val vertical = Brush.verticalGradient(
            0f to Color.Transparent,
            at(rect.top) to ink(0.55f),
            at(rect.bottom) to ink(1f),
            at(rect.bottom + below * 0.10f) to ink(0.86f),
            at(rect.bottom + below * 0.25f) to ink(0.62f),
            at(rect.bottom + below * 0.45f) to ink(0.34f),
            at(rect.bottom + below * 0.65f) to ink(0.14f),
            at(rect.bottom + below * 0.85f) to ink(0.035f),
            1f to Color.Transparent,
            startY = y0, endY = y1,
        )
        val colors = Brush.horizontalGradient(listOf(left.value, this@WatchGlowPaint.center.value, right.value),
            startX = rect.left, endX = rect.right)
        val canvas = drawContext.canvas
        canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
        drawRect(colors, bounds.topLeft, bounds.size)
        drawRect(vertical, bounds.topLeft, bounds.size, blendMode = BlendMode.DstIn)
        if (side > 0f) drawRect(Brush.horizontalGradient(
            0f to Color.Transparent, 0.6f to ink(0.45f), 1f to ink(1f), startX = x0, endX = rect.left),
            bounds.topLeft, bounds.size, blendMode = BlendMode.DstIn)
        canvas.restore()
    }
}

/**
 * Provides [LocalWatchGlow] and paints the glow behind the page. [visible] is false in fullscreen,
 * mini and PiP; [strength] follows the page's own enter/fullscreen progress.
 */
@Composable
fun WatchGlowHost(
    colors: GlowColors?,
    enabled: Boolean,
    visible: Boolean,
    slot: () -> Rect?,
    strength: () -> Float,
    content: @Composable () -> Unit,
) {
    val dark = DesignTokens.Background.luminance() < 0.5f
    val peak = GlowPalette.peak(dark)
    // Raw thumbnail averages are cached per video; the theme decides boost, clamp and contrast guard.
    val tuned = remember(colors, dark) { colors?.let { GlowPalette.forTheme(it, dark) } }
    // Opening another video recomputes once and crossfades old -> new (no fade at animator scale 0).
    val fade = tween<Color>(600)
    val clear = Color.Transparent
    val left = animateColorAsState(tuned?.left?.let(::Color) ?: clear, fade, label = "glow-left")
    val center = animateColorAsState(tuned?.center?.let(::Color) ?: clear, fade, label = "glow-center")
    val right = animateColorAsState(tuned?.right?.let(::Color) ?: clear, fade, label = "glow-right")
    val on = enabled && visible
    val currentOn = androidx.compose.runtime.rememberUpdatedState(on)
    val currentPeak = androidx.compose.runtime.rememberUpdatedState(peak)
    val currentSlot = androidx.compose.runtime.rememberUpdatedState(slot)
    val currentStrength = androidx.compose.runtime.rememberUpdatedState(strength)
    // One paint for the page's lifetime: the sticky row reads it through a static local.
    val paint = remember {
        WatchGlowPaint({ currentSlot.value() }, left, center, right) {
            if (currentOn.value) currentPeak.value * currentStrength.value() else 0f
        }
    }
    val origin = remember { mutableStateOf(Offset.Zero) }
    Canvas(Modifier.fillMaxSize().testTag("watch-glow")
        .onGloballyPositioned { origin.value = it.positionInRoot() }) {
        paint.draw(this, origin.value)
    }
    CompositionLocalProvider(LocalWatchGlow provides paint, content = content)
}

/** The page's glow, for surfaces that must stay opaque yet keep the light continuous (the sticky chip row). */
val LocalWatchGlow = staticCompositionLocalOf<WatchGlowPaint?> { null }

/** Opaque page background plus the glow under it, positioned in root coordinates. */
@Composable
fun Modifier.watchGlowBackground(): Modifier {
    val glow = LocalWatchGlow.current
    val background = DesignTokens.Background
    val origin = remember { mutableStateOf(Offset.Zero) }
    return this.onGloballyPositioned { origin.value = it.positionInRoot() }
        .drawBehind {
            drawRect(background)
            glow?.draw(this, origin.value)
        }
}

const val GlowAboveDp = 48
const val GlowBelowDp = 160
const val GlowSideDp = 40
