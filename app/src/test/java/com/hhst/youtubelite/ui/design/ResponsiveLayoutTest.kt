package com.hhst.youtubelite.ui.design

import com.hhst.youtubelite.player.surface.MiniPlayerLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device classes, the navigation form and the mini-player dock derived from it. */
class ResponsiveLayoutTest {
    @Test fun smallestWidthSplitsPhonesFromTablets() {
        assertTrue(DeviceClass.isPhone(360))
        assertTrue(DeviceClass.isPhone(599))
        assertFalse(DeviceClass.isPhone(600))
        assertTrue(DeviceClass.isTablet(600))
        assertTrue(DeviceClass.isTablet(800))
        // An unreported value never locks anything.
        assertFalse(DeviceClass.isPhone(0))
    }

    @Test fun wideLandscapeWindowsGetTheRail() {
        // Phone portrait, tablet portrait: bottom bar.
        assertEquals(NavigationForm.Bar, NavigationForm.of(411, 914))
        assertEquals(NavigationForm.Bar, NavigationForm.of(800, 1280))
        // Tablet landscape, and any medium-or-wider window that is wider than tall: rail.
        assertEquals(NavigationForm.Rail, NavigationForm.of(1280, 800))
        assertEquals(NavigationForm.Rail, NavigationForm.of(960, 600))
        assertEquals(NavigationForm.Rail, NavigationForm.of(600, 400))
        // Compact width never gets the rail, square windows keep the bar.
        assertEquals(NavigationForm.Bar, NavigationForm.of(599, 300))
        assertEquals(NavigationForm.Bar, NavigationForm.of(900, 900))
    }

    @Test fun miniDockFollowsTheNavigationForm() {
        assertEquals(56, MiniDock.bottomInsetDp(NavigationForm.Bar))
        assertEquals(0, MiniDock.startInsetDp(NavigationForm.Bar))
        assertEquals(0, MiniDock.bottomInsetDp(NavigationForm.Rail))
        assertEquals(80, MiniDock.startInsetDp(NavigationForm.Rail))
    }

    @Test fun fullWindowHostLandsOnTheBrowseDock() {
        // The browse host pads safeDrawing and passes the form's inset; the watch host spans the
        // window and passes one number. Both must give MiniPlayerLayout the same bottom margin.
        for (form in NavigationForm.entries) for (safeBottom in listOf(0, 24, 48)) {
            val browse = safeBottom + MiniPlayerLayout.computeSpec(1280, MiniDock.bottomInsetDp(form),
                MiniPlayerLayout.NO_WIDTH_OVERRIDE_DP).bottomMarginDp
            val watch = MiniPlayerLayout.computeSpec(1280, MiniDock.windowBottomInsetDp(form, safeBottom),
                MiniPlayerLayout.NO_WIDTH_OVERRIDE_DP).bottomMarginDp
            assertEquals("$form / $safeBottom", browse, watch)
        }
        // The floor MiniDock mirrors is MiniPlayerLayout's own minimum bottom clearance.
        assertEquals(
            MiniPlayerLayout.computeSpec(360, MiniDock.MiniPlayerFloorDp, MiniPlayerLayout.NO_WIDTH_OVERRIDE_DP).bottomMarginDp,
            MiniPlayerLayout.computeSpec(360, 0, MiniPlayerLayout.NO_WIDTH_OVERRIDE_DP).bottomMarginDp,
        )
    }
}
