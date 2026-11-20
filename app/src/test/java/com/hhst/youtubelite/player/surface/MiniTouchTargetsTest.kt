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

    @Test fun defaultMiniGivesEveryControlA48dpSquare() {
        // Intent changed (fix-all W8): the restore button moved into the top row, so even the
        // smallest default mini (190dp wide -> 106.9dp tall) has full 48x48 boxes on both rows.
        for (screen in listOf(360, 411, 800)) {
            val height = MiniPlayerLayout.defaultWidthDp(screen) * 9f / 16f
            assertTrue(MiniTouchTargets.stackGapDp(height) >= 48f)
            for (drawn in listOf(side, play)) assertEquals(48f, MiniTouchTargets.touchHeightDp(drawn, height), 0.01f)
        }
    }

    @Test fun transportBoxesEndOnTheBottomEdgeAndRowsDoNotOverlap() {
        // Row centre half a box above the bottom edge: the 48dp boxes stay inside the card.
        assertEquals(24f, MiniTouchTargets.ROW_CENTER_FROM_BOTTOM_DP, 0.01f)
        assertEquals(24f - play / 2f, MiniTouchTargets.rowOffsetFromBottomDp(), 0.01f)
        // A hypothetical shorter card shrinks boxes to the midpoint between the rows, never below drawn size.
        assertEquals(40f, MiniTouchTargets.touchHeightDp(side, 88f), 0.01f)
        assertEquals(play.toFloat(), MiniTouchTargets.touchHeightDp(play, 60f), 0.01f)
    }
}
