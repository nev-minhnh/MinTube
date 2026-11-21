package com.hhst.youtubelite.ui.design

import com.hhst.youtubelite.player.surface.MiniPlayerLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/** QA pass 2 N3: lists under the docked mini get enough bottom padding to scroll every row above it. */
class MiniDockClearanceTest {
    private val density = 2.625f // 420 dpi phone
    private val safeBottomPx = 2316f // dock area bottom: window minus the gesture bar

    private fun miniTop(screenWidthDp: Int, form: NavigationForm): Float {
        val spec = MiniPlayerLayout.computeSpec(screenWidthDp, MiniDock.bottomInsetDp(form),
            MiniPlayerLayout.NO_WIDTH_OVERRIDE_DP, MiniDock.bottomFloorDp(form))
        return MiniDock.miniTopInWindowPx(safeBottomPx, spec.bottomMarginDp, spec.heightDp, density)
    }

    @Test fun listAboveTheTabBarClearsTheMiniHeightPlusItsMargin() {
        val spec = MiniPlayerLayout.computeSpec(411, MiniDock.bottomInsetDp(NavigationForm.Bar),
            MiniPlayerLayout.NO_WIDTH_OVERRIDE_DP, MiniDock.bottomFloorDp(NavigationForm.Bar))
        val listBottom = safeBottomPx - MiniDock.BarHeightDp * density
        val extra = MiniDock.listClearancePx(listBottom, miniTop(411, NavigationForm.Bar))
        assertEquals((spec.heightDp + MiniPlayerLayout.outerMarginDp(411)) * density, extra, 0.01f)
    }

    @Test fun listToTheScreenBottomAlsoClearsTheBarFloor() {
        val spec = MiniPlayerLayout.computeSpec(411, MiniDock.bottomInsetDp(NavigationForm.Bar),
            MiniPlayerLayout.NO_WIDTH_OVERRIDE_DP, MiniDock.bottomFloorDp(NavigationForm.Bar))
        val extra = MiniDock.listClearancePx(safeBottomPx, miniTop(411, NavigationForm.Bar))
        assertEquals((spec.heightDp + spec.bottomMarginDp) * density, extra, 0.01f)
    }

    @Test fun railWindowDocksAtTheBottomWithItsMargin() {
        val spec = MiniPlayerLayout.computeSpec(1200, 0, MiniPlayerLayout.NO_WIDTH_OVERRIDE_DP, 0)
        val extra = MiniDock.listClearancePx(safeBottomPx, miniTop(1200, NavigationForm.Rail))
        assertEquals((spec.heightDp + MiniPlayerLayout.outerMarginDp(1200)) * density, extra, 0.01f)
    }

    @Test fun noMiniOrUnmeasuredListAddsNothing() {
        assertEquals(0f, MiniDock.listClearancePx(safeBottomPx, null), 0f)
        assertEquals(0f, MiniDock.listClearancePx(null, 1000f), 0f)
    }

    @Test fun listEndingAboveTheMiniAddsNothing() {
        assertEquals(0f, MiniDock.listClearancePx(900f, 1000f), 0f)
    }
}
