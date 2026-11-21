package com.hhst.youtubelite.tv.watch

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.extractor.Chapter
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager
import com.hhst.youtubelite.player.surface.PlayerUi
import com.hhst.youtubelite.tv.design.TvColors

/**
 * Progress bar: buffered track, played part in the brand gradient (cyan → pink across the whole
 * width), SponsorBlock segments in their category colours, chapter gaps, and a thumb when focused.
 * Positions are read in draw only, so 4 Hz ticks redraw without recomposing the overlay.
 */
@Composable
fun TvSeekBar(
    position: () -> Long,
    buffered: () -> Long,
    durationMs: Long,
    segments: List<SponsorBlockManager.Segment>,
    chapters: List<Chapter>,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackHeight by animateDpAsState(if (focused) 8.dp else 5.dp, label = "tvSeekTrack")
    val thumb by animateDpAsState(if (focused) 18.dp else 0.dp, label = "tvSeekThumb")
    val current = position()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()), 0f..durationMs.coerceAtLeast(1).toFloat())
            },
    ) {
        val h = trackHeight.toPx()
        val top = (size.height - h) / 2
        val radius = CornerRadius(h / 2, h / 2)
        val duration = durationMs.coerceAtLeast(1).toFloat()
        fun x(ms: Long) = (ms / duration).coerceIn(0f, 1f) * size.width
        drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, h), radius)
        val bufferedX = x(buffered())
        if (bufferedX > 0f) drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(0f, top), Size(bufferedX, h), radius)
        val playedX = if (durationMs > 0) x(position()) else 0f
        if (playedX > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(TvColors.GradientStart, TvColors.GradientEnd), startX = 0f, endX = size.width),
                Offset(0f, top), Size(playedX, h), radius,
            )
        }
        if (durationMs > 0) {
            segments.forEach { segment ->
                val start = x(segment.startMs)
                val end = maxOf(x(segment.endMs), start + 2.dp.toPx())
                drawRect(PlayerUi.segmentColor(segment.category), Offset(start, top), Size(end - start, h))
            }
            // Chapter boundaries as small gaps in the track.
            chapters.drop(1).forEach { chapter ->
                val cx = x(chapter.startSeconds * 1000L)
                drawRect(Color.Black.copy(alpha = 0.7f), Offset(cx - 1.dp.toPx(), top), Size(2.dp.toPx(), h))
            }
        }
        val thumbPx = thumb.toPx()
        if (thumbPx > 0f) {
            drawCircle(TvColors.Accent, thumbPx / 2 + 2.dp.toPx(), Offset(playedX, size.height / 2))
            drawCircle(Color.White, thumbPx / 2, Offset(playedX, size.height / 2))
        }
    }
}
