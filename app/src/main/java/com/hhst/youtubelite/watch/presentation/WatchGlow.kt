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

    private const val SATURATION_BOOST = 1.2f
    private const val MAX_SATURATION = 0.85f
    private const val MIN_LIGHTNESS = 0.30f
    private const val MAX_LIGHTNESS = 0.62f

    /**
     * Left-edge (first quarter), centre (middle quarter) and right-edge (last quarter) averages of
     * an ARGB pixel array, with saturation boosted slightly and lightness clamped so the glow shows
     * on a near-black page without turning neon on a light one. Null for an empty/short array.
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
            return tune(((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt())
        }
        return GlowColors(average(0, quarter), average(centerStart, centerStart + quarter), average(width - quarter, width))
    }

    /** Opaque RGB -> boosted, clamped opaque RGB. */
    fun tune(rgb: Int): Int {
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
        return hsl(h, min(MAX_SATURATION, s * SATURATION_BOOST), l.coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS))
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
     * Paints the glow into this draw scope, whose top-left sits at [origin] in root coordinates.
     * Horizontally it stays inside the player's own column (tablet two-column layout included);
     * vertically it eases out over [GlowAboveDp] above and [GlowBelowDp] below the slot.
     */
    fun draw(scope: DrawScope, origin: Offset) = with(scope) {
        val rect = slot() ?: return@with
        val alpha = strength()
        if (alpha <= 0.001f || rect.width <= 0f || rect.height <= 0f) return@with
        val top = rect.top - origin.y
        val bottom = rect.bottom - origin.y
        val l = (rect.left - origin.x).coerceAtLeast(0f)
        val r = (rect.right - origin.x).coerceAtMost(size.width)
        if (r <= l) return@with
        val colors = Brush.horizontalGradient(listOf(left.value, this@WatchGlowPaint.center.value, right.value),
            startX = rect.left - origin.x, endX = rect.right - origin.x)
        val above = min(GlowAboveDp.dp.toPx(), max(0f, top))
        if (above > 0f) band(l, r, top - above, top, colors, alpha, Brush.verticalGradient(
            0f to Color.Transparent, 0.5f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.6f),
            startY = top - above, endY = top))
        val below = GlowBelowDp.dp.toPx()
        band(l, r, bottom, bottom + below, colors, alpha, Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.85f), 0.25f to Color.Black.copy(alpha = 0.5f),
            0.5f to Color.Black.copy(alpha = 0.22f), 0.75f to Color.Black.copy(alpha = 0.07f), 1f to Color.Transparent,
            startY = bottom, endY = bottom + below))
    }

    /** Colour band masked by a vertical alpha ramp ([BlendMode.DstIn]) in its own small layer. */
    private fun DrawScope.band(l: Float, r: Float, t: Float, b: Float, colors: Brush, alpha: Float, mask: Brush) {
        val top = max(t, 0f); val bottom = min(b, size.height)
        if (bottom <= top) return
        val bounds = Rect(l, top, r, bottom)
        val canvas = drawContext.canvas
        canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
        drawRect(colors, bounds.topLeft, bounds.size)
        drawRect(mask, bounds.topLeft, bounds.size, blendMode = BlendMode.DstIn)
        canvas.restore()
    }
}

/** Peak strength: full effect on a dark page, a subtle tint on a light one. */
internal const val GlowDarkAlpha = 0.45f
internal const val GlowLightAlpha = 0.18f

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
    val peak = if (dark) GlowDarkAlpha else GlowLightAlpha
    // Opening another video recomputes once and crossfades old -> new (no fade at animator scale 0).
    val fade = tween<Color>(600)
    val clear = Color.Transparent
    val left = animateColorAsState(colors?.left?.let(::Color) ?: clear, fade, label = "glow-left")
    val center = animateColorAsState(colors?.center?.let(::Color) ?: clear, fade, label = "glow-center")
    val right = animateColorAsState(colors?.right?.let(::Color) ?: clear, fade, label = "glow-right")
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

const val GlowAboveDp = 40
const val GlowBelowDp = 96
