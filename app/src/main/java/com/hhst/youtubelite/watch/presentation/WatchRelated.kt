package com.hhst.youtubelite.watch.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.dearrow.VideoArtwork
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.ui.components.DurationBadge
import com.hhst.youtubelite.ui.components.SectionHeader
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.components.ThumbnailProgress
import com.hhst.youtubelite.ui.components.VideoCardSkeleton
import com.hhst.youtubelite.ui.components.VideoRowSkeleton
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appDescription
import com.hhst.youtubelite.watch.domain.WatchAction
import com.hhst.youtubelite.watch.domain.WatchContent
import com.hhst.youtubelite.watch.domain.WatchLayout
import com.hhst.youtubelite.watch.domain.WatchProvisionalStore
import com.hhst.youtubelite.watch.domain.WatchRelated
import com.hhst.youtubelite.watch.domain.WatchUiState
import com.hhst.youtubelite.watch.domain.toProvisional

/*
 * Related videos. The cards follow the shared Mint card family (16dp thumbnail, duration badge,
 * two-line title, metadata, 48dp overflow) but stay private to this package: the watch page keeps
 * its own per-item tags and a thumbnail that is a separate tap target from the text.
 */

private val RowThumbnailWidth = 168.dp

@Composable
internal fun RelatedHeader(inset: Dp) {
    SectionHeader(stringResource(R.string.watch_up_next), Modifier.padding(horizontal = inset).padding(top = MinSpace.xs))
}

@Composable
internal fun RelatedEmpty() {
    StateMessage(stringResource(R.string.ui_no_videos), tag = "watch-related-empty")
}

/** One row of the main-column feed: list rows in wide-short landscape, otherwise 1-3 cards. */
@Composable
internal fun RelatedFeedRow(items: List<WatchRelated>, columns: Int, listRows: Boolean, inset: Dp, onAction: (WatchAction) -> Unit) {
    if (listRows) {
        RelatedRow(items.first(), onAction, Modifier.padding(start = inset, end = inset - MinSpace.md, bottom = MinSpace.md))
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(start = inset, end = inset, bottom = MinSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.GridGap),
    ) {
        items.forEach { RelatedCard(it, onAction, Modifier.weight(1f)) }
        repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
internal fun RelatedSkeleton(columns: Int, listRows: Boolean, inset: Dp) {
    Column(Modifier.fillMaxWidth().padding(top = MinSpace.lg).testTag("watch-related-skeleton").clearAndSetSemantics {}) {
        repeat(if (listRows) 4 else 2) {
            if (listRows) VideoRowSkeleton(Modifier.padding(horizontal = inset).padding(bottom = MinSpace.md), RowThumbnailWidth)
            else Row(
                Modifier.fillMaxWidth().padding(start = inset, end = inset, bottom = MinSpace.lg),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.GridGap),
            ) {
                repeat(columns) { VideoCardSkeleton(Modifier.weight(1f)) }
            }
        }
    }
}

/** The related pane of the two-pane layout. Its width stays on WatchLayout: the comments rail docks over it. */
@Composable
internal fun WatchSidebarPane(state: WatchUiState, viewportWidth: Float, scroll: LazyListState, onAction: (WatchAction) -> Unit) {
    RelatedPaging(state, scroll, onAction)
    val paneWidth = WatchLayout.sidebarWidth(viewportWidth)
    val wide = paneWidth >= WatchLayout.SidebarWideWidth
    Box(
        Modifier.width(paneWidth.dp).fillMaxHeight()
            .padding(top = WatchLayout.TopBarHeight.dp)
            .testTag("watch-related"),
    ) {
        when (val content = state.content) {
            WatchContent.Loading -> Column(
                Modifier.fillMaxWidth().padding(horizontal = MinSpace.md).padding(top = MinSpace.md).clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(MinSpace.lg),
            ) {
                repeat(if (wide) 5 else 3) {
                    if (wide) VideoRowSkeleton(thumbnailWidth = RowThumbnailWidth) else VideoCardSkeleton()
                }
            }
            WatchContent.Failed -> Unit
            is WatchContent.Ready -> {
                val related = content.page.related
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = scroll,
                    contentPadding = PaddingValues(top = MinSpace.xs, bottom = MinSpace.lg),
                ) {
                    item("header") { RelatedHeader(MinSpace.md) }
                    if (related.isEmpty() && content.page.nextKey == null) item("related-empty") { RelatedEmpty() }
                    items(related, key = { it.id }, contentType = { "related" }) { item ->
                        if (wide) RelatedRow(item, onAction, Modifier.padding(start = MinSpace.md, bottom = MinSpace.md))
                        else RelatedCard(item, onAction, Modifier.padding(horizontal = MinSpace.md).padding(bottom = MinSpace.lg), showAvatar = false)
                    }
                    item("pagination") { RelatedFooter(state, onAction) }
                }
            }
        }
    }
}

@Composable
private fun RelatedCard(item: WatchRelated, onAction: (WatchAction) -> Unit, modifier: Modifier = Modifier, showAvatar: Boolean = true) {
    Column(modifier.testTag("watch-related-${item.id}")) {
        RelatedThumb(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f), MinRadius.Card, onAction)
        Row(Modifier.fillMaxWidth().padding(top = MinSpace.sm), verticalAlignment = Alignment.Top) {
            if (showAvatar) {
                RelatedAvatar(item, onAction)
                Spacer(Modifier.width(MinSpace.xs))
            }
            RelatedText(item, Modifier.weight(1f).padding(top = MinSpace.xs), large = true, onAction = onAction)
            RelatedKebab(item, onAction)
        }
    }
}

@Composable
private fun RelatedRow(item: WatchRelated, onAction: (WatchAction) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().testTag("watch-related-${item.id}"), verticalAlignment = Alignment.Top) {
        RelatedThumb(item, Modifier.width(RowThumbnailWidth).aspectRatio(16f / 9f), MinRadius.Field, onAction)
        Spacer(Modifier.width(MinSpace.md))
        RelatedText(item, Modifier.weight(1f).padding(top = MinSpace.xxs), large = false, onAction = onAction)
        RelatedKebab(item, onAction)
    }
}

private fun openRelated(item: WatchRelated, onAction: (WatchAction) -> Unit) {
    WatchProvisionalStore.put(item.toProvisional())
    onAction(WatchAction.OpenRelated(item.id))
}

@Composable
private fun RelatedThumb(item: WatchRelated, modifier: Modifier, shape: Shape, onAction: (WatchAction) -> Unit) {
    Box(
        modifier.clip(shape)
            .clickable(role = Role.Button) { openRelated(item, onAction) }
            .appDescription(R.string.a11y_title_duration, item.title, item.duration)
            .testTag("watch-thumb-${item.id}"),
    ) {
        VideoArtwork(item.id, item.artwork, Modifier.fillMaxSize())
        if (item.duration.isNotBlank()) DurationBadge(item.duration, modifier = Modifier.testTag("watch-badge-${item.id}"))
        item.progress?.let { ThumbnailProgress(it) }
    }
}

@Composable
private fun RelatedText(item: WatchRelated, modifier: Modifier, large: Boolean, onAction: (WatchAction) -> Unit) {
    Column(modifier.clickable(role = Role.Button) { openRelated(item, onAction) }) {
        AppText(
            rememberDeArrowTitle(item.id, item.title), if (large) MinType.Subtitle else MinType.Body,
            Modifier.fillMaxWidth().testTag("watch-related-title-${item.id}"), maxLines = 2,
        )
        if (item.channel.isNotBlank()) AppText(item.channel, MinType.Caption, Modifier.padding(top = MinSpace.xxs),
            color = DesignTokens.Secondary, maxLines = 1)
        val stats = listOf(item.views, item.age).filter { it.isNotBlank() }.joinToString(" • ")
        if (stats.isNotBlank()) AppText(stats, MinType.Caption, color = DesignTokens.Secondary, maxLines = 1)
        item.badge?.let { badge ->
            AppText(badge, MinType.Micro, Modifier.padding(top = MinSpace.xs), color = DesignTokens.Secondary, maxLines = 1)
        }
    }
}

@Composable
private fun RelatedAvatar(item: WatchRelated, onAction: (WatchAction) -> Unit) {
    Box(
        Modifier.size(MinSpace.Touch).clip(CircleShape)
            .testTag("watch-avatar-${item.id}")
            .clickable(enabled = item.channelId.isNotBlank(), role = Role.Button) { onAction(WatchAction.OpenChannel(item.channelId)) }
            .appDescription(R.string.a11y_go_to_channel, item.channel),
        contentAlignment = Alignment.Center,
    ) {
        Artwork(item.avatar.orEmpty(), Modifier.size(36.dp).clip(CircleShape))
    }
}

@Composable
private fun RelatedKebab(item: WatchRelated, onAction: (WatchAction) -> Unit) {
    AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_actions_for, item.title), diameter = 48.dp) {
        onAction(WatchAction.OpenItemMenu(item.id))
    }
}
