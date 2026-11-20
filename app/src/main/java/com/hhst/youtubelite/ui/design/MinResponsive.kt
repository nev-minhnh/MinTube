package com.hhst.youtubelite.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Device class by `smallestScreenWidthDp`: phones (< 600dp) run portrait-only outside video
 * fullscreen, tablets rotate freely. 0 means the platform did not report a value; it is never
 * treated as a phone so nothing gets locked by mistake.
 */
object DeviceClass {
    const val TabletFromSmallestWidthDp = 600

    fun isPhone(smallestWidthDp: Int): Boolean = smallestWidthDp in 1 until TabletFromSmallestWidthDp
    fun isTablet(smallestWidthDp: Int): Boolean = smallestWidthDp >= TabletFromSmallestWidthDp
}

/**
 * Where the four main tabs live. The one source shared by the tab bar, the rail and the mini-player
 * dock, so they can never disagree: a window of at least medium width that is wider than tall gets
 * the start-edge rail, everything else the bottom bar.
 */
enum class NavigationForm {
    Bar, Rail;

    companion object {
        fun of(widthDp: Int, heightDp: Int): NavigationForm =
            if (WindowClass.of(widthDp) != WindowClass.Compact && widthDp > heightDp) Rail else Bar
    }
}

/** The current window's navigation form; follows rotation, multi-window and fold changes. */
@Composable
fun rememberNavigationForm(): NavigationForm {
    val configuration = LocalConfiguration.current
    return NavigationForm.of(configuration.screenWidthDp, configuration.screenHeightDp)
}

/**
 * Mini-player dock clearance per navigation form, in dp, measured inside the safe-drawing area.
 * Bottom bar: the mini sits above the 56dp bar. Rail: no bottom bar, and the mini keeps off the
 * rail's start-edge column. `MiniPlayerLayout` still keeps its own minimum clearance from the bottom
 * edge ([MiniPlayerFloorDp], pinned by a unit test), so hosts that place the mini in a full-window
 * frame (the watch page) must add that floor themselves to land on the same pixel.
 */
object MiniDock {
    const val BarHeightDp = 56
    const val RailWidthDp = 80
    /** Mirrors `MiniPlayerLayout`'s minimum bottom dock; `MiniDockTest` fails if they drift apart. */
    const val MiniPlayerFloorDp = 56

    fun bottomInsetDp(form: NavigationForm): Int = if (form == NavigationForm.Bar) BarHeightDp else 0
    fun startInsetDp(form: NavigationForm): Int = if (form == NavigationForm.Rail) RailWidthDp else 0

    /** The bottom inset a full-window host passes so its mini matches the browse host's dock. */
    fun windowBottomInsetDp(form: NavigationForm, safeBottomDp: Int): Int =
        safeBottomDp + maxOf(bottomInsetDp(form), MiniPlayerFloorDp)
}
