package com.hhst.youtubelite.tv

import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvFocusRow
import com.hhst.youtubelite.tv.design.TvFocusTarget
import com.hhst.youtubelite.tv.shell.TvBackStep
import com.hhst.youtubelite.tv.shell.TvDestination
import com.hhst.youtubelite.tv.shell.TvShellNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvFocusMemoryTest {
    private val rows = listOf(
        TvFocusRow("main", listOf("a", "b", "c", "d")),
        TvFocusRow("shorts", listOf("s1", "s2")),
        TvFocusRow("chip:Music", listOf("m1", "m2", "m3")),
    )

    @Test fun nothingRememberedEntersTheFirstCardOfTheFirstRow() {
        val memory = TvFocusMemory()
        assertEquals(0, memory.indexIn("main", rows[0].itemKeys))
        assertEquals(TvFocusTarget(0, 0), memory.restoreTarget(rows))
    }

    @Test fun returnsToTheLastFocusedCard() {
        val memory = TvFocusMemory()
        memory.record("main", 0, "b", 1)
        memory.record("chip:Music", 2, "m3", 2)
        assertEquals(TvFocusTarget(2, 2), memory.restoreTarget(rows))
        // Every row keeps its own position for re-entry.
        assertEquals(1, memory.indexIn("main", rows[0].itemKeys))
        assertEquals(0, memory.indexIn("shorts", rows[1].itemKeys))
    }

    @Test fun followsTheCardWhenARefreshMovesIt() {
        val memory = TvFocusMemory()
        memory.record("main", 0, "c", 2)
        val refreshed = listOf(TvFocusRow("main", listOf("new", "a", "b", "c", "d")))
        assertEquals(TvFocusTarget(0, 3), memory.restoreTarget(refreshed))
    }

    @Test fun aCardThatLeftFallsBackToItsSlotClamped() {
        val memory = TvFocusMemory()
        memory.record("main", 0, "d", 3)
        assertEquals(3, memory.indexIn("main", listOf("w", "x", "y", "z", "v")))
        assertEquals(1, memory.indexIn("main", listOf("w", "x")))
        assertEquals(0, memory.indexIn("main", emptyList()))
    }

    @Test fun aRowThatLeftFallsBackToTheNearestRowWithCards() {
        val memory = TvFocusMemory()
        memory.record("shorts", 1, "s2", 1)
        val withoutShorts = listOf(
            TvFocusRow("main", listOf("a", "b")),
            TvFocusRow("loading", emptyList()),
            TvFocusRow("chip:Music", listOf("m1")),
        )
        // Row 1 now has no cards; rows 0 and 2 are equally near, the first wins.
        assertEquals(TvFocusTarget(0, 0), memory.restoreTarget(withoutShorts))
        assertNull(memory.restoreTarget(listOf(TvFocusRow("loading", emptyList()))))
    }

    @Test fun survivesSaveAndRestore() {
        val memory = TvFocusMemory()
        memory.record("main", 0, "b", 1)
        memory.record("chip:Music", 2, "m2", 1)
        val restored = TvFocusMemory.restore(memory.save())
        assertEquals("chip:Music", restored.lastRowKey)
        assertEquals(TvFocusTarget(2, 1), restored.restoreTarget(rows))
        assertEquals(1, restored.indexIn("main", rows[0].itemKeys))
    }

    @Test fun clearForgetsEverything() {
        val memory = TvFocusMemory()
        memory.record("main", 0, "c", 2)
        memory.forget("main")
        assertEquals(0, memory.indexIn("main", rows[0].itemKeys))
        memory.record("main", 0, "c", 2)
        memory.clear()
        assertNull(memory.lastRowKey)
        assertEquals(TvFocusTarget(0, 0), memory.restoreTarget(rows))
    }

    @Test fun shellBackGoesContentRailHomeExit() {
        assertEquals(TvBackStep.FocusRail, TvShellNavigation.back(contentFocused = true, selected = TvDestination.Home))
        assertEquals(TvBackStep.FocusRail, TvShellNavigation.back(contentFocused = true, selected = TvDestination.Library))
        assertEquals(TvBackStep.ShowHome, TvShellNavigation.back(contentFocused = false, selected = TvDestination.Search))
        assertEquals(TvBackStep.Exit, TvShellNavigation.back(contentFocused = false, selected = TvDestination.Home))
    }
}
