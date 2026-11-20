package com.hhst.youtubelite.player.surface

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Touch boxes for the mini-player's small controls. The drawn transport icons keep their 30/34dp
 * layout slots and every [MiniPlayerLayout] constant stays as it is: each control answers touches
 * (and reports its accessibility bounds) over a larger box centred on the same spot.
 *
 * Two rows: queue, restore and close are 48x48 buttons along the top edge; the transport row
 * (previous, play, next) has its centre [ROW_CENTER_FROM_BOTTOM_DP] above the bottom edge, so its
 * 48dp boxes end exactly on that edge. The rows never overlap while the mini is at least
 * 2 x 48dp tall; the smallest mini (190dp wide -> 106dp tall) is taller than that.
 * Width is always at least 48dp (the transport row's centres are >= 50dp apart).
 */
internal object MiniTouchTargets {
    const val MIN_TOUCH_DP = 48
    /** Drawn size of the restore button's icon slot (now a full 48dp button in the top row). */
    const val RESTORE_DP = 40
    /** The transport row's centre sits half a touch box above the bottom edge. */
    const val ROW_CENTER_FROM_BOTTOM_DP = MIN_TOUCH_DP / 2f
    /** Centre of the top row's 48dp buttons, measured from the top edge. */
    const val TOP_ROW_CENTER_DP = MIN_TOUCH_DP / 2f

    fun touchWidthDp(drawnDp: Int): Int = maxOf(drawnDp, MIN_TOUCH_DP)

    /** Offset that puts the transport row (laid out at the 34dp play slot) on [ROW_CENTER_FROM_BOTTOM_DP]. */
    fun rowOffsetFromBottomDp(): Float = ROW_CENTER_FROM_BOTTOM_DP - MiniPlayerLayout.CONTROL_PLAY_DP / 2f

    /** Distance between the top row's centre and the transport row's centre. */
    fun stackGapDp(miniHeightDp: Float): Float = miniHeightDp - TOP_ROW_CENTER_DP - ROW_CENTER_FROM_BOTTOM_DP

    /** Never below the drawn size, never above 48dp, never past the other row's midpoint. */
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
