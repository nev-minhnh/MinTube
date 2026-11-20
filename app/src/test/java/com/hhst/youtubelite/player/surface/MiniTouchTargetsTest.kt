package com.hhst.youtubelite.player.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniTouchTargetsTest {
    private val side = MiniPlayerLayout.CONTROL_SIDE_DP
    private val play = MiniPlayerLayout.CONTROL_PLAY_DP
    private val restore = MiniTouchTargets.RESTORE_DP

    @Test fun everyControlIsAtLeast48dpWide() {
        for (drawn in listOf(side, play, restore, 48)) assertEquals(48, MiniTouchTargets.touchWidthDp(drawn))
        // The transport row's centres are far enough apart at the narrowest mini for 48dp boxes.
        val widthDp = MiniPlayerLayout.defaultWidthDp(360)
        val gap = MiniPlayerLayout.controlGap(widthDp, 360)
        assertTrue(side / 2f + gap + play / 2f >= 48f)
    }

    @Test fun tallMinisGet48dpTouchHeight() {
        // 320dp wide mini -> 180dp tall: room for 48dp boxes on both stacked controls.
        for (drawn in listOf(side, play, restore)) assertEquals(48f, MiniTouchTargets.touchHeightDp(drawn, 180f), 0.01f)
    }

    @Test fun smallMinisNeverLetStackedControlsOverlapMoreThanTheyDraw() {
        // Default compact mini: 190dp wide -> 106.9dp tall. The centre gap is 32.4dp, below the drawn
        // sizes of play/restore, so those keep their drawn height and the side buttons grow to the gap.
        val height = 190f * 9f / 16f
        val gap = MiniTouchTargets.stackGapDp(height)
        assertEquals(gap, MiniTouchTargets.touchHeightDp(side, height), 0.01f)
        assertEquals(play.toFloat(), MiniTouchTargets.touchHeightDp(play, height), 0.01f)
        assertEquals(restore.toFloat(), MiniTouchTargets.touchHeightDp(restore, height), 0.01f)
        // Mid size: each box stops at the midpoint between the two centres.
        val mid = 240f * 9f / 16f
        val midGap = MiniTouchTargets.stackGapDp(mid)
        assertTrue(midGap in 40f..48f)
        assertEquals(midGap, MiniTouchTargets.touchHeightDp(play, mid), 0.01f)
        assertEquals(midGap, MiniTouchTargets.touchHeightDp(restore, mid), 0.01f)
    }
}
