package com.hhst.youtubelite.player.surface

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager

/**
 * Position and buffered position arrive as [State]s and are read inside the
 * draw scope, so a 4 Hz playback tick only redraws the bar instead of
 * recomposing the caller's tree.
 *
 * The 48 dp hit area stays centered for the finger, but the visible track is
 * drawn halfway down the hit canvas. BottomBar overlaps the controls row with
 * the lower half, keeping the track close to the labels without a tall spacer.
 */
@Composable
fun PlayerTimeBar(
    positionState: State<Long>,
    bufferedPositionState: State<Long>,
    durationMs: Long,
    segments: List<SponsorBlockManager.Segment>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Target-time preview while dragging: non-null shows, null clears. */
    onPreview: (Long?) -> Unit = {},
    /** Drag released (gesture overlay hides even when the seek is a no-op). */
    onCommit: () -> Unit = {},
    chapterStarts: List<Long> = emptyList(),
    /**
     * Horizontal inset of the visible track inside the full-width hit canvas, so the bar lines up
     * with the control row instead of running edge to edge. Touches in the inset still scrub
     * (clamped to the track ends); the 48 dp hit height is unchanged.
     */
    trackInsetLeft: Dp = 0.dp,
    trackInsetRight: Dp = 0.dp,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val timeBarDescription = stringResource(R.string.player_time_bar)
    val announcedFraction = PlayerUi.progressFraction(positionState.value, durationMs)
    val announcedTime = PlayerUi.formatTime(positionState.value) + " / " + PlayerUi.formatTime(durationMs)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerUi.TIME_BAR_HIT_DP.dp)
            .testTag("player-time-bar")
            .semantics {
                contentDescription = timeBarDescription
                progressBarRangeInfo = ProgressBarRangeInfo(announcedFraction, 0f..1f)
                // Spoken value of the slider: the time, not a bare percentage.
                if (durationMs > 0) stateDescription = announcedTime
                // TalkBack and switch access adjust the bar through SetProgress; the
                // seek lands directly, the drag preview stays a pointer-only path.
                if (durationMs > 0) setProgress { target ->
                    onSeek((target.coerceIn(0f, 1f) * durationMs).toLong())
                    true
                }
            }
            .pointerInput(durationMs, trackInsetLeft, trackInsetRight) {
                if (durationMs <= 0L) return@pointerInput
                awaitEachGesture {
                    try {
                        val down = awaitFirstDown()
                        val left = trackInsetLeft.toPx()
                        val width = (size.width - left - trackInsetRight.toPx()).coerceAtLeast(1f)
                        fun fractionOf(x: Float) = ((x - left) / width).coerceIn(0f, 1f)
                        dragging = true
                        dragFraction = fractionOf(down.position.x)
                        // Preview follows the finger; the seek lands once on
                        // release — seeking per move event re-buffers.
                        onPreview((dragFraction * durationMs).toLong())
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.positionChanged()) {
                                dragFraction = fractionOf(change.position.x)
                                onPreview((dragFraction * durationMs).toLong())
                                change.consume()
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                onSeek((dragFraction * durationMs).toLong())
                                break
                            }
                        }
                    } finally {
                        dragging = false
                        onPreview(null)
                        onCommit()
                    }
                }
            },
    ) {
        // Snapshot reads here are draw-scoped: a position tick invalidates
        // only this draw, not the surrounding composition.
        val positionMs = positionState.value
        val played = if (dragging) dragFraction else PlayerUi.progressFraction(positionMs, durationMs)
        val buffered = PlayerUi.bufferedFraction(bufferedPositionState.value, durationMs)

        val thickness = PlayerUi.TIME_BAR_THICKNESS_DP.dp.toPx()
        val centerY = size.height / 2f
        val y = centerY - thickness / 2f
        val x0 = trackInsetLeft.toPx().coerceAtMost(size.width)
        val trackWidth = (size.width - x0 - trackInsetRight.toPx()).coerceAtLeast(0f)
        val barSize = Size(trackWidth, thickness)

        drawRect(PlayerUi.Unplayed, topLeft = Offset(x0, y), size = barSize)
        drawRect(
            PlayerUi.Buffered,
            topLeft = Offset(x0, y),
            size = Size(trackWidth * buffered, thickness),
        )
        // The brand gradient spans the inset track, not the canvas.
        translate(left = x0) {
            drawRect(
                PlayerUi.brandBrush(trackWidth),
                topLeft = Offset(0f, y),
                size = Size(trackWidth * played, thickness),
            )
        }
        // Markers last so they stay visible over played and unplayed regions.
        segments.forEach { segment ->
            val range = PlayerUi.segmentRange(segment.startMs, segment.endMs, durationMs,
                point = segment.category == "poi_highlight") ?: return@forEach
            val point = range.first == range.second
            val width = (if (point) 4.dp.toPx() else
                (trackWidth * (range.second - range.first)).coerceAtLeast(2f)).coerceAtMost(trackWidth)
            val startX = x0 + (trackWidth * range.first - if (point) width / 2f else 0f)
                .coerceIn(0f, (trackWidth - width).coerceAtLeast(0f))
            drawRect(
                PlayerUi.segmentColor(segment.category),
                topLeft = Offset(startX, y),
                size = Size(width, thickness),
            )
        }
        if (durationMs > 0) chapterStarts.distinct().filter { it > 0 && it < durationMs }.forEach { start ->
            val x = x0 + trackWidth * (start.toFloat() / durationMs)
            drawRect(androidx.compose.ui.graphics.Color.Black, Offset(x - 1.dp.toPx(), y), Size(2.dp.toPx(), thickness))
        }
        val thumbR = if (dragging) 7.dp.toPx() else 5.dp.toPx()
        drawCircle(
            color = PlayerUi.Accent,
            radius = thumbR,
            center = Offset(x0 + trackWidth * played, centerY),
        )
    }
}
