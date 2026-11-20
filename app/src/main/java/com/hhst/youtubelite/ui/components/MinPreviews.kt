package com.hhst.youtubelite.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.SubscribeButton
import com.hhst.youtubelite.ui.theme.AppTheme

/** Light, dark and font scale 2.0, as every Mint component must hold up in all three. */
@Preview(name = "Light", widthDp = 390, showBackground = true)
@Preview(name = "Dark", widthDp = 390, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Font 2.0", widthDp = 390, showBackground = true, fontScale = 2f)
private annotation class MintPreviews

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    AppTheme {
        Column(
            Modifier.fillMaxWidth().background(DesignTokens.Background).verticalScroll(rememberScrollState()).padding(MinSpace.lg),
            verticalArrangement = Arrangement.spacedBy(MinSpace.lg),
        ) { content() }
    }
}

@MintPreviews
@Composable
private fun ButtonsAndChipsPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
        MinButton("Primary", {})
        MinButton("Tonal", {}, kind = MinButtonKind.Tonal)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
        MinButton("Outline", {}, kind = MinButtonKind.Outline, icon = AppIcon.Download)
        MinButton("Text", {}, kind = MinButtonKind.Text)
        MinButton("Delete", {}, danger = true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
        MinButton("Pending", {}, pending = true)
        MinButton("Disabled", {}, enabled = false)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
        SubscribeButton(subscribed = false, onClick = {})
        SubscribeButton(subscribed = true, onClick = {})
    }
    FilterChips(listOf("All", "Music", "Live", "Gaming", "News", "Recently uploaded"), 1, {}, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp))
    PrivateBrowsingBadge(true)
}

@MintPreviews
@Composable
private fun BarsPreview() = PreviewFrame {
    MinTopBar(privateBrowsing = true) {
        AppIconButton(AppIcon.Search, "Search") {}
        AppIconButton(AppIcon.Cast, "Cast") {}
        MinAvatarButton(null, "Account", {})
    }
    MinTopBar(title = "Watch later", onBack = {}) { AppIconButton(AppIcon.More, "More options") {} }
    SectionHeader("Continue watching", actionLabel = "View all", onAction = {})
    MinNavigationBar("preview", signedIn = true, selected = MainTab.Home, onSelect = {})
    MinNavigationBar("preview-signed-out", signedIn = false, selected = null, onSelect = {})
}

@MintPreviews
@Composable
private fun CardsPreview() = PreviewFrame {
    VideoCard(
        "Building a calm video app: the Mint design system in practice", "MinTube · 12K views · 3 days ago", "bunny", {},
        durationLabel = "12:34", progress = 0.4f, avatar = "dream", onMenu = {}, menuDescription = "More actions",
    )
    VideoRow("A compact row for lists and related videos", "Channel name", "sintel", {}, secondaryMetadata = "8.1K views · 1 year ago",
        durationLabel = "LIVE", live = true, onMenu = {}, menuDescription = "More actions")
    Row(horizontalArrangement = Arrangement.spacedBy(MinSpace.md)) {
        ShortCard("A short with a two line title on artwork", "dream", {}, Modifier.width(150.dp), metadata = "1.2M views",
            onMenu = {}, menuDescription = "More actions")
        ShortCardSkeleton(Modifier.width(150.dp))
    }
    ChannelRow("Channel name", "bunny", {}, metadata = "@handle · 120K subscribers") { SubscribeButton(false, {}) }
    PlaylistCard("Favourites", "Private · Updated today", "sintel", {}, countLabel = "24", onMenu = {}, menuDescription = "More playlist actions")
}

@MintPreviews
@Composable
private fun StatesPreview() = PreviewFrame {
    StateMessage("Nothing here yet", message = "Videos you save will show up here.", actionLabel = "Browse", onAction = {})
    StateMessage("Couldn't load this page", message = "Something went wrong.", kind = StateKind.Error, actionLabel = "Retry", onAction = {})
    OfflineMessage({})
    VideoCardSkeleton()
    VideoRowSkeleton()
    ChannelRowSkeleton()
    PlaylistCardSkeleton()
}
