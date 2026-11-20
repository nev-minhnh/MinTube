package com.hhst.youtubelite.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable

enum class MainTab(val label: String) { Home("Home"), Shorts("Shorts"), Subscriptions("Subscriptions"), You("You") }

object MainTabs {
    fun tabs(signedIn: Boolean, hideShorts: Boolean = false) =
        (if (signedIn) MainTab.entries else listOf(MainTab.Home, MainTab.Shorts, MainTab.You))
            .filterNot { hideShorts && it == MainTab.Shorts }

    /**
     * Reuses an existing destination instance instead of stacking a new one on
     * every tab switch. Each destination retains its saved scroll state.
     */
    fun intent(context: Context, tab: MainTab, signedIn: Boolean): Intent? = when (tab) {
        MainTab.Home -> Intent(context, com.hhst.youtubelite.home.HomeActivity::class.java)
            .putExtra(com.hhst.youtubelite.home.HomeActivity.EXTRA_SIGNED_IN, signedIn)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        MainTab.Subscriptions -> com.hhst.youtubelite.subscriptions.SubscriptionsActivity.intent(context, signedIn)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        MainTab.You -> com.hhst.youtubelite.library.YouActivity.intent(context, signedIn)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        MainTab.Shorts -> com.hhst.youtubelite.shorts.ShortsActivity.intent(context)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }?.putExtra(com.hhst.youtubelite.ui.NativeActivity.EXTRA_TAB_NAVIGATION, true)
        ?.putExtra(com.hhst.youtubelite.ui.NativeActivity.EXTRA_TAB_BACKWARD, backwards(context, tab))

    private fun backwards(context: Context, target: MainTab): Boolean {
        var owner = context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
        val source = when (owner) {
            is com.hhst.youtubelite.home.HomeActivity -> MainTab.Home
            is com.hhst.youtubelite.shorts.ShortsActivity -> MainTab.Shorts
            is com.hhst.youtubelite.subscriptions.SubscriptionsActivity -> MainTab.Subscriptions
            is com.hhst.youtubelite.library.YouActivity -> MainTab.You
            else -> return false
        }
        return target.ordinal < source.ordinal
    }
}

/** Kept for its call sites and tags; the bar itself is [MinNavigationBar]. */
@Composable
fun MainNavigationBar(tag: String, signedIn: Boolean, avatarAsset: String?, selected: MainTab = MainTab.You, onSelect: (MainTab) -> Unit) {
    MinNavigationBar(tag, signedIn, selected, onSelect, avatarAsset = avatarAsset)
}
