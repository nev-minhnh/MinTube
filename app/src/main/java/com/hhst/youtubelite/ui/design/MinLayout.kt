package com.hhst.youtubelite.ui.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale (docs/UI_PLAN.md 2.3). */
object MinSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp
    /** Smallest touch target, including chips and overflow buttons on cards. */
    val Touch = 48.dp

    /** Horizontal page inset: 16dp on compact windows, 24dp from 600dp width. */
    fun pageInset(window: WindowClass): Dp = if (window == WindowClass.Compact) lg else xxl
}

/** Corner radii (docs/UI_PLAN.md 2.3). */
object MinRadius {
    /** Chips inside cards, badges. */
    val xs = 8.dp
    /** Fields, small cards. */
    val sm = 12.dp
    /** Thumbnails, cards. */
    val md = 16.dp
    /** Sheet tops, dialogs. */
    val lg = 24.dp

    val Badge = RoundedCornerShape(xs)
    val Field = RoundedCornerShape(sm)
    val Card = RoundedCornerShape(md)
    val Dialog = RoundedCornerShape(lg)
    val Sheet = RoundedCornerShape(topStart = lg, topEnd = lg)
    /** Pills, avatars, FAB-like buttons. */
    val Full = CircleShape
}

/** Width class of the window: compact < 600dp, medium 600-839dp, expanded >= 840dp. */
enum class WindowClass {
    Compact, Medium, Expanded;

    /** Feed grid: 1, 2 or 3 columns. */
    val feedColumns: Int get() = ordinal + 1

    companion object {
        const val MediumFromDp = 600
        const val ExpandedFromDp = 840

        fun of(widthDp: Int): WindowClass = when {
            widthDp >= ExpandedFromDp -> Expanded
            widthDp >= MediumFromDp -> Medium
            else -> Compact
        }
    }
}

/** The current window's class; follows rotation, multi-window and fold changes. */
@Composable
fun rememberWindowClass(): WindowClass = WindowClass.of(LocalConfiguration.current.screenWidthDp)

/** True when a wide window is also wider than tall: the layout uses the start-edge rail ([NavigationForm.Rail]). */
@Composable
fun rememberWideLandscape(): Boolean = rememberNavigationForm() == NavigationForm.Rail
