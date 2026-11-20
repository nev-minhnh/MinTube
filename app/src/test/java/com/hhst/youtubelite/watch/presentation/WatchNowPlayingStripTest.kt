package com.hhst.youtubelite.watch.presentation

import androidx.compose.foundation.lazy.LazyListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNowPlayingStripTest {
    @Test fun stripStaysHiddenUntilTheWholeTitleBlockIsUnderThePlayer() {
        assertFalse(LazyListState().titleBlockScrolledAway(300))
        assertFalse(LazyListState(0, 299).titleBlockScrolledAway(300))
        assertTrue(LazyListState(0, 300).titleBlockScrolledAway(300))
        assertTrue(LazyListState(1, 0).titleBlockScrolledAway(300))
        // Not measured yet: only a fully scrolled-away first item counts.
        assertFalse(LazyListState(0, 900).titleBlockScrolledAway(0))
        assertTrue(LazyListState(2, 0).titleBlockScrolledAway(0))
    }

    @Test fun stickyChipsStackUnderTheStripInsteadOfSlidingBeneathIt() {
        // Pinned header (placed at 0) moves down by the full strip height.
        assertEquals(54f, stickyUnderStripOffset(0, 54f), 0f)
        // Approaching header is only nudged once it would enter the strip.
        assertEquals(14f, stickyUnderStripOffset(40, 54f), 0f)
        assertEquals(0f, stickyUnderStripOffset(200, 54f), 0f)
        // No strip (hidden or mid-fade at 0) or header off screen: no shift.
        assertEquals(0f, stickyUnderStripOffset(0, 0f), 0f)
        assertEquals(0f, stickyUnderStripOffset(null, 54f), 0f)
    }
}
