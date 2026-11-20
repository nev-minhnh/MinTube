package com.hhst.youtubelite.player.surface

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Touch boxes for the mini-player's small controls. The drawn icons, their 30/34/40dp layout slots
 * and every [MiniPlayerLayout] constant stay as they are: each control only answers touches (and
 * reports its accessibility bounds) over a larger box centred on the same spot.
 *
 * Width is always at least 48dp (the transport row's centres are >= 50dp apart, so neighbours never
 * overlap). Height reaches 48dp only when the mini is tall enough: the restore button and the
 * transport row are stacked, and neither box may reach past the midpoint between their centres,
 * so a small mini keeps the drawn heights rather than letting one control steal the other's taps.
 */
internal object MiniTouchTargets {
    const val MIN_TOUCH_DP = 48
    /** Drawn size of the centre restore button. */
    const val RESTORE_DP = 40
    /** The transport row sits 4dp above the bottom edge; its centre is half the 34dp play slot above that. */
    const val ROW_CENTER_FROM_BOTTOM_DP = 4f + MiniPlayerLayout.CONTROL_PLAY_DP / 2f

    fun touchWidthDp(drawnDp: Int): Int = maxOf(drawnDp, MIN_TOUCH_DP)

    /** Distance between the restore button's centre and the transport row's centre. */
    fun stackGapDp(miniHeightDp: Float): Float = miniHeightDp / 2f - ROW_CENTER_FROM_BOTTOM_DP

    /** Never below the drawn size, never above 48dp, never past the stacked neighbour's midpoint. */
    fun touchHeightDp(drawnDp: Int, miniHeightDp: Float): Float =
        stackGapDp(miniHeightDp).coerceIn(drawnDp.toFloat(), maxOf(drawnDp, MIN_TOUCH_DP).toFloat())
}

/**
 * Lays the control out at [drawn] (what its parent row sees) while measuring it, and so its
 * clickable and semantics bounds, at the larger touch box centred on the same point.
 */
internal fun Modifier.miniTouchBox(drawn: Dp, touchHeight: Dp): Modifier = layout { measurable, _ ->
    val slot = drawn.roundToPx()
    val width = maxOf(MiniTouchTargets.MIN_TOUCH_DP.dp.roundToPx(), slot)
    val height = maxOf(touchHeight.roundToPx(), slot)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(slot, slot) {
        placeable.place(((slot - width) / 2f).roundToInt(), ((slot - height) / 2f).roundToInt())
    }
}
