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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appStateLayer
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.rememberAppInteractions
import com.hhst.youtubelite.ui.design.rememberHapticAction

/** Geometry shared with the mini-player dock, which sits directly above the bar. */
object MinNavigation {
    /** Height of the tab row. `ContentActivity` passes the same 56 to the mini-player window. */
    val BarHeight = 56.dp
    val RailWidth = 80.dp
    /** Labels follow the system font scale up to this factor so two-line growth cannot clip the 56dp row. */
    const val MaxLabelFontScale = 1.3f
}

/**
 * The one bottom bar. Home, Shorts, Subscriptions (signed in only) and You; Shorts disappears with
 * the "hide Shorts" preference.
 *
 * - Root tag `<tag>-navigation`; tab tags come from [tabTag], `<tag>-nav-<Tab>` by default.
 * - Every tab has `Role.Tab` and `selected` semantics. Pass `selected = null` on screens that are
 *   not one of the four destinations (channel, playlist, search results...).
 * - [avatarAsset] is the signed-in account's avatar. Without one the You tab shows the generic icon.
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
 * Start-edge rail with the same tabs, tags and semantics as [MinNavigationBar], for wide landscape
 * windows (`rememberWideLandscape()`). Not wired into any screen yet: a host that shows it must
 * also drop its bottom bar and tell the mini-player overlay that the bar height is 0.
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
            Modifier.width(MinNavigation.RailWidth).fillMaxHeight().background(DesignTokens.Panel).padding(vertical = 12.dp),
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
