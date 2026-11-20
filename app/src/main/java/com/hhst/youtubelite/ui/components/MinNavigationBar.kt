package com.hhst.youtubelite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.core.HapticsController
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.LocalHideShorts
import com.hhst.youtubelite.ui.design.MiniDock
import com.hhst.youtubelite.ui.design.NavigationForm
import com.hhst.youtubelite.ui.design.rememberNavigationForm
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appStateLayer
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.rememberAppInteractions
import com.hhst.youtubelite.ui.design.rememberHapticAction

/** Geometry shared with the mini-player dock ([MiniDock]), which sits above the bar or beside the rail. */
object MinNavigation {
    /** Height of the tab row; the mini-player dock clears the same [MiniDock.BarHeightDp]. */
    val BarHeight = MiniDock.BarHeightDp.dp
    /** Full rail width including its 1dp divider; the mini-player keeps off the same [MiniDock.RailWidthDp]. */
    val RailWidth = MiniDock.RailWidthDp.dp
    /** Labels follow the system font scale up to this factor so two-line growth cannot clip the 56dp row. */
    const val MaxLabelFontScale = 1.3f
}

/**
 * The one tab bar. Home, Shorts, Subscriptions (signed in only) and You; Shorts disappears with
 * the "hide Shorts" preference.
 *
 * - Root tag `<tag>-navigation`; tab tags come from [tabTag], `<tag>-nav-<Tab>` by default.
 * - Every tab has `Role.Tab` and `selected` semantics. Pass `selected = null` on screens that are
 *   not one of the four destinations (channel, playlist, search results...).
 * - [avatarAsset] is the signed-in account's avatar. Without one the You tab shows the generic icon.
 * - In a [NavigationForm.Rail] window inside a [NavigationFrame] (every Activity using `setAppContent`)
 *   the bar draws nothing in place and hands the same tabs, tags and semantics to the frame, which
 *   shows them as a [MinNavigationRail] on the start edge with the screen to its right.
 */
@Composable
fun MinNavigationBar(
    tag: String,
    signedIn: Boolean,
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    avatarAsset: String? = null,
    tabTag: (MainTab) -> String = { "$tag-nav-${it.name}" },
) {
    val host = LocalNavigationRailHost.current
    if (host != null && rememberNavigationForm() == NavigationForm.Rail) {
        val owner = remember { Any() }
        val request = NavigationRailRequest(tag, signedIn, selected, onSelect, avatarAsset, tabTag)
        SideEffect { host.offer(owner, request) }
        DisposableEffect(host, owner) { onDispose { host.withdraw(owner) } }
        return
    }
    Column(modifier.fillMaxWidth().background(DesignTokens.Panel).testTag("$tag-navigation")) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.NavigationDivider))
        Row(Modifier.fillMaxWidth().height(MinNavigation.BarHeight)) {
            MainTabs.tabs(signedIn, LocalHideShorts.current).forEach { tab ->
                NavigationTab(tab, tab == selected, avatarAsset.takeIf { signedIn }, onSelect,
                    Modifier.weight(1f).fillMaxHeight().testTag(tabTag(tab)))
            }
        }
    }
}

/**
 * Start-edge rail with the same tabs, tags and semantics as [MinNavigationBar], for
 * [NavigationForm.Rail] windows. Screens do not call it: [NavigationFrame] draws it for the bar the
 * screen declares, and the mini-player overlay reads the same [NavigationForm] to dock beside it.
 */
@Composable
fun MinNavigationRail(
    tag: String,
    signedIn: Boolean,
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    avatarAsset: String? = null,
    tabTag: (MainTab) -> String = { "$tag-nav-${it.name}" },
) {
    Row(modifier.fillMaxHeight().testTag("$tag-navigation")) {
        Column(
            Modifier.width(MinNavigation.RailWidth - 1.dp).fillMaxHeight().background(DesignTokens.Panel).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MainTabs.tabs(signedIn, LocalHideShorts.current).forEach { tab ->
                NavigationTab(tab, tab == selected, avatarAsset.takeIf { signedIn }, onSelect,
                    Modifier.fillMaxWidth().heightIn(min = MinNavigation.BarHeight).testTag(tabTag(tab)))
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(DesignTokens.NavigationDivider))
    }
}

/** The tabs a screen's [MinNavigationBar] asks its [NavigationFrame] to show as a rail. */
internal data class NavigationRailRequest(
    val tag: String,
    val signedIn: Boolean,
    val selected: MainTab?,
    val onSelect: (MainTab) -> Unit,
    val avatarAsset: String?,
    val tabTag: (MainTab) -> String,
)

/** Receives the bar of the screen inside a [NavigationFrame]; the last bar composed wins. */
class NavigationRailHost internal constructor() {
    private var owner: Any? = null
    internal var request by mutableStateOf<NavigationRailRequest?>(null)
        private set

    internal fun offer(owner: Any, request: NavigationRailRequest) {
        this.owner = owner
        if (this.request != request) this.request = request
    }

    internal fun withdraw(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        request = null
    }
}

val LocalNavigationRailHost = staticCompositionLocalOf<NavigationRailHost?> { null }

/**
 * Hosts a whole screen. In a [NavigationForm.Rail] window, the tabs of the screen's
 * [MinNavigationBar] move to a start-edge rail and the screen sits to its right; the screen keeps
 * its composition (and scroll state) when the rail appears or goes. Screens without a bar are drawn
 * edge to edge as before.
 */
@Composable
fun NavigationFrame(content: @Composable () -> Unit) {
    val host = remember { NavigationRailHost() }
    val rail = host.request.takeIf { rememberNavigationForm() == NavigationForm.Rail }
    CompositionLocalProvider(LocalNavigationRailHost provides host) {
        Row(Modifier.fillMaxSize()) {
            if (rail != null) Box(Modifier.fillMaxHeight().background(DesignTokens.Panel)) {
                MinNavigationRail(rail.tag, rail.signedIn, rail.selected, rail.onSelect,
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)),
                    avatarAsset = rail.avatarAsset, tabTag = rail.tabTag)
            }
            Box(Modifier.weight(1f).fillMaxHeight().then(
                if (rail != null) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier,
            )) { content() }
        }
    }
}

@Composable
private fun NavigationTab(tab: MainTab, selected: Boolean, avatarAsset: String?, onSelect: (MainTab) -> Unit, modifier: Modifier) {
    val haptic = rememberHapticAction(HapticsController.Event.TAB)
    val interactions = rememberAppInteractions()
    Column(
        modifier.selectable(selected = selected, interactionSource = interactions, indication = null, role = Role.Tab) {
            haptic(); onSelect(tab)
        },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        // Pill indicator behind the icon; it also carries the press feedback.
        Box(
            Modifier.size(width = 56.dp, height = 28.dp).clip(CircleShape)
                .background(if (selected) DesignTokens.AccentContainer else androidx.compose.ui.graphics.Color.Transparent)
                .appStateLayer(interactions, 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (tab == MainTab.You && !avatarAsset.isNullOrBlank()) {
                Artwork(avatarAsset, Modifier.size(24.dp).clip(CircleShape)
                    .then(if (selected) Modifier.border(1.5.dp, DesignTokens.Accent, CircleShape) else Modifier))
            } else AppIcon(
                when (tab) {
                    MainTab.Home -> if (selected) AppIcon.HomeFilled else AppIcon.HomeOutline
                    MainTab.Shorts -> if (selected) AppIcon.ShortsFilled else AppIcon.Shorts
                    MainTab.Subscriptions -> if (selected) AppIcon.SubscriptionsFilled else AppIcon.Subscriptions
                    MainTab.You -> if (selected) AppIcon.YouFilled else AppIcon.You
                },
                color = if (selected) DesignTokens.AccentText else DesignTokens.Ink,
            )
        }
        Spacer(Modifier.height(2.dp))
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(MinNavigation.MaxLabelFontScale)),
        ) {
            AppText(
                stringResource(tab.labelResource),
                if (selected) MinType.Micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else MinType.Micro,
                color = if (selected) DesignTokens.AccentText else DesignTokens.Secondary, maxLines = 1,
            )
        }
    }
}
