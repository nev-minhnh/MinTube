package com.hhst.youtubelite.ui.design

import androidx.compose.runtime.Composable
import com.hhst.youtubelite.ui.components.MainTab
import com.hhst.youtubelite.ui.components.MinNavigationBar

enum class BrowseDestination(val label: String) { Home("Home"), Shorts("Shorts"), Subscriptions("Subscriptions"), You("You") }

/**
 * Bottom bar of screens that are not one of the four destinations (channel, playlist, post).
 * Kept for its call sites and tags; the bar itself is [MinNavigationBar] with no selected tab.
 */
@Composable
fun BrowseNavigationBar(tag: String, signedIn: Boolean = false, onSelect: (BrowseDestination) -> Unit) {
    MinNavigationBar(tag, signedIn, selected = null, onSelect = { tab: MainTab -> onSelect(BrowseDestination.valueOf(tab.name)) })
}
