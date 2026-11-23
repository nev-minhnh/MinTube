package com.hhst.youtubelite.ui.design

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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
 * Bottom bar: the mini sits directly above the 56dp bar. Rail: no bottom bar, so the mini docks at
 * the bottom (navigation-bar inset plus its margin) and keeps off the rail's start-edge column.
 * `MiniPlayerLayout` keeps its own minimum clearance from the bottom edge ([MiniPlayerFloorDp],
 * pinned by a unit test); hosts pass [bottomFloorDp] so that floor only applies with a bottom bar.
 */
object MiniDock {
    const val BarHeightDp = 56
    const val RailWidthDp = 80
    /** Mirrors `MiniPlayerLayout`'s minimum bottom dock; `MiniDockTest` fails if they drift apart. */
    const val MiniPlayerFloorDp = 56

    fun bottomInsetDp(form: NavigationForm): Int = if (form == NavigationForm.Bar) BarHeightDp else 0
    fun startInsetDp(form: NavigationForm): Int = if (form == NavigationForm.Rail) RailWidthDp else 0

    /** The mini-player's minimum bottom clearance: none with the rail (no bar to clear). */
    fun bottomFloorDp(form: NavigationForm): Int = if (form == NavigationForm.Bar) MiniPlayerFloorDp else 0

    /** The bottom inset a full-window host passes so its mini matches the browse host's dock. */
    fun windowBottomInsetDp(form: NavigationForm, safeBottomDp: Int): Int =
        safeBottomDp + maxOf(bottomInsetDp(form), bottomFloorDp(form))

    /**
     * Top edge, in window px, of the space the docked mini occupies: its height plus its bottom
     * margin (which already includes the bar clearance) above the dock area's bottom edge.
     */
    fun miniTopInWindowPx(dockBottomPx: Float, miniBottomMarginDp: Int, miniHeightDp: Int, density: Float): Float =
        dockBottomPx - (miniBottomMarginDp + miniHeightDp) * density

    /**
     * Extra bottom content padding, in px, that lets a list's last rows scroll clear of the mini:
     * the part of the list the mini covers. A list ending at the tab bar gets the mini's height
     * plus its margin; one ending at the screen bottom also gets the bar clearance. 0 without a mini.
     */
    fun listClearancePx(listBottomPx: Float?, miniTopPx: Float?): Float =
        if (listBottomPx == null || miniTopPx == null) 0f else (listBottomPx - miniTopPx).coerceAtLeast(0f)
}

/**
 * The docked in-app mini-player as seen by the screen under it. Its host (`ContentActivity`, the
 * one place the mini is drawn) publishes [topInWindowPx]; null while no mini is docked.
 */
@Stable
class MiniDockArea {
    var topInWindowPx: Float? by mutableStateOf(null)
}

/** An Activity that docks the in-app mini over its content; `setAppContent` provides its area. */
interface MiniDockHost {
    val miniDockArea: MiniDockArea
}

/** The mini dock of the current window; null where no mini is ever docked (Settings, Downloads...). */
val LocalMiniDockArea = staticCompositionLocalOf<MiniDockArea?> { null }

/** Bottom content padding for a scrolling list, and the [modifier] that measures that list. */
class MiniDockListPadding(val bottom: Dp, val modifier: Modifier)

/**
 * [base] bottom content padding plus the part of the list the docked mini covers, so every row can
 * scroll above the mini and stay fully tappable. Put [MiniDockListPadding.modifier] on the list.
 */
@Composable
fun miniDockListPadding(base: Dp): MiniDockListPadding {
    val area = LocalMiniDockArea.current
    val listBottom = remember { mutableStateOf<Float?>(null) }
    val modifier = remember(area) {
        if (area == null) Modifier else Modifier.onGloballyPositioned { listBottom.value = it.boundsInWindow().bottom }
    }
    val extraPx = MiniDock.listClearancePx(listBottom.value, area?.topInWindowPx)
    return MiniDockListPadding(base + with(LocalDensity.current) { extraPx.toDp() }, modifier)
}

/** Side gutters, in whole pixels, that centre a reading column inside a full-width list. */
class ListGutter(val start: Dp, val end: Dp)

/**
 * Hosts a full-width scrolling list whose rows stay in a [columnMax] reading column. [content] gets the
 * side gutters to add to the list's horizontal content padding: the list itself spans the window, so a
 * swipe in the empty side strips still scrolls it (a centred, width-capped list leaves them dead).
 * The gutters split the spare width the way centring does, in whole pixels, so rows keep exact sizes.
 */
@Composable
fun CenteredListGutter(columnMax: Dp, content: @Composable (gutter: ListGutter) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val spare = (constraints.maxWidth - with(density) { columnMax.roundToPx() }).coerceAtLeast(0)
        val start = (spare / 2f).roundToInt()
        content(with(density) { ListGutter(start.toDp(), (spare - start).toDp()) })
    }
}
