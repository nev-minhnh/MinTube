package com.hhst.youtubelite.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowClassTest {
    @Test fun breakpointsAre600And840() {
        assertEquals(WindowClass.Compact, WindowClass.of(0))
        assertEquals(WindowClass.Compact, WindowClass.of(599))
        assertEquals(WindowClass.Medium, WindowClass.of(600))
        assertEquals(WindowClass.Medium, WindowClass.of(839))
        assertEquals(WindowClass.Expanded, WindowClass.of(840))
        assertEquals(WindowClass.Expanded, WindowClass.of(1280))
    }

    @Test fun feedGridGrowsWithTheWindow() {
        assertEquals(listOf(1, 2, 3), WindowClass.entries.map { it.feedColumns })
    }
}
