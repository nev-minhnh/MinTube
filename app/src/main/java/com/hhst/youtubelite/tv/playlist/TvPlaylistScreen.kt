package com.hhst.youtubelite.tv.playlist

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import com.hhst.youtubelite.tv.design.tvFocusRestorer
import com.hhst.youtubelite.tv.design.tvAnchor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocus
import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.rememberTvFocusMemory
import com.hhst.youtubelite.tv.grid.TvListPagingEffect
import com.hhst.youtubelite.tv.grid.TvPagingFooter
import com.hhst.youtubelite.tv.grid.TvRestoreFocusOnResume
import com.hhst.youtubelite.tv.grid.requestWhenReady
import com.hhst.youtubelite.ui.YoutubeThumb
import com.hhst.youtubelite.ui.design.AppIcon

/**
 * Full-screen TV playlist: a header panel on the left (cover, title, owner, count, Play all,
 * Shuffle) and the video rows on the right with paging. Initial focus is Play all; the last
 * focused row comes back after Back from Watch. State-hoisted.
 */
@Composable
fun TvPlaylistScreen(
    status: TvPlaylistStatus,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onOpenOwner: () -> Unit,
    onOpen: (TvPlaylistRow) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    focusMemory: TvFocusMemory = rememberTvFocusMemory(),
) {
    Box(modifier.fillMaxSize().background(TvColors.Background).testTag(TAG_PLAYLIST)) {
        when (status) {
            TvPlaylistStatus.Loading -> PlaylistSkeleton()
            TvPlaylistStatus.Failed, TvPlaylistStatus.Unavailable -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TvStateMessage(
                    title = stringResource(if (status == TvPlaylistStatus.Failed) R.string.tv_playlist_failed_title
                        else R.string.tv_playlist_unavailable),
                    message = if (status == TvPlaylistStatus.Failed) stringResource(R.string.tv_home_failed_message) else null,
                    icon = if (status == TvPlaylistStatus.Failed) AppIcon.WifiOff else AppIcon.Playlist,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_PLAYLIST_RETRY),
                    requestFocus = true,
                )
            }
            is TvPlaylistStatus.Ready -> PlaylistPage(status, onPlayAll, onShuffle, onOpenOwner, onOpen, onLoadMore, onRetryMore,
                onRetry, focusMemory)
        }
    }
}

@Composable
private fun PlaylistSkeleton() {
    Row(Modifier.fillMaxSize().padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical)
        .testTag(TAG_PLAYLIST_SKELETON), horizontalArrangement = Arrangement.spacedBy(TvSpacing.xxl)) {
        Column(Modifier.width(PanelWidth), verticalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(TvSizes.Corner)).background(TvColors.Surface))
            Box(Modifier.fillMaxWidth(0.8f).height(24.dp).clip(RoundedCornerShape(6.dp)).background(TvColors.Ghost))
            Box(Modifier.fillMaxWidth(0.5f).height(16.dp).clip(RoundedCornerShape(6.dp)).background(TvColors.Ghost))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            repeat(5) {
                Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.lg)) {
                    Box(Modifier.width(RowThumbWidth).aspectRatio(16f / 9f).clip(RoundedCornerShape(TvSizes.SmallCorner))
                        .background(TvColors.Surface))
                    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.sm)) {
                        Box(Modifier.width(320.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(TvColors.Ghost))
                        Box(Modifier.width(180.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(TvColors.Ghost))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPage(
    status: TvPlaylistStatus.Ready,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onOpenOwner: () -> Unit,
    onOpen: (TvPlaylistRow) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onRetry: () -> Unit,
    memory: TvFocusMemory,
) {
    val playAll = remember { FocusRequester() }
    val rowRequester = remember { FocusRequester() }
    val list = rememberLazyListState()
    var pageFocused by remember { mutableStateOf(false) }
    val keys = remember(status.rows) { status.rows.map(TvPlaylistRow::entryId) }
    val anchor = memory.indexIn(ROWS, keys)
    LaunchedEffect(Unit) { playAll.requestWhenReady() }
    TvRestoreFocusOnResume(keys.size, hasFocus = { pageFocused }) {
        if (memory.lastRowKey == ROWS && keys.isNotEmpty()) {
            list.scrollToItem(anchor)
            if (rowRequester.requestWhenReady(attempts = 6)) return@TvRestoreFocusOnResume
        }
        playAll.requestWhenReady(attempts = 6)
    }
    TvListPagingEffect(list, status.paging, status.rows.size, onLoadMore)
    Row(
        Modifier.fillMaxSize().onFocusChanged { pageFocused = it.hasFocus }
            .padding(start = TvSpacing.OverscanHorizontal, top = TvSpacing.OverscanVertical),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.xxl),
    ) {
        // Left from the list returns to the last header control, else Play all (not whichever control
        // happens to sit at the row's height).
        Column(Modifier.width(PanelWidth).fillMaxHeight().focusRestorer(playAll).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            YoutubeThumb(status.header.artwork, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(TvSizes.Corner)),
                placeholderColor = TvColors.Surface)
            Text(status.header.title, style = TvType.Title, color = TvColors.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(TAG_PLAYLIST_TITLE))
            val details = listOf(status.header.count).filter(String::isNotBlank).joinToString(" · ")
            if (status.header.owner.isNotBlank()) {
                if (status.header.channelId.isNotBlank()) {
                    TvButton(status.header.owner, onOpenOwner, Modifier.testTag(TAG_PLAYLIST_OWNER), icon = AppIcon.Person)
                } else Text(status.header.owner, style = TvType.BodyStrong, color = TvColors.Secondary, maxLines = 1)
            }
            if (details.isNotBlank()) Text(details, style = TvType.Caption, color = TvColors.Secondary)
            status.enumerating?.let { loaded ->
                Text(stringResource(R.string.tv_playlist_loading_all, loaded), style = TvType.Caption, color = TvColors.Accent,
                    modifier = Modifier.testTag(TAG_PLAYLIST_ENUMERATING))
            }
            Spacer(Modifier.height(TvSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
                TvButton(stringResource(R.string.ui_play_all), onPlayAll, Modifier.focusRequester(playAll).testTag(TAG_PLAYLIST_PLAY_ALL),
                    icon = AppIcon.Play, primary = true)
                TvButton(stringResource(R.string.playback_end_shuffle), onShuffle, Modifier.testTag(TAG_PLAYLIST_SHUFFLE),
                    icon = AppIcon.Shuffle)
            }
        }
        if (status.rows.isEmpty() && !status.paging.hasMore) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(end = TvSpacing.OverscanHorizontal), contentAlignment = Alignment.Center) {
                TvStateMessage(
                    title = stringResource(R.string.tv_playlist_empty),
                    icon = AppIcon.Playlist,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_PLAYLIST_RETRY),
                )
            }
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxHeight().tvFocusRestorer(rowRequester).testTag(TAG_PLAYLIST_ROWS),
                contentPadding = PaddingValues(end = TvSpacing.OverscanHorizontal, top = TvSpacing.xs,
                    bottom = TvSpacing.OverscanVertical + 48.dp),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
            ) {
                itemsIndexed(status.rows, key = { _, row -> row.entryId }) { index, row ->
                    PlaylistRow(
                        index = index,
                        row = row,
                        onClick = { onOpen(row) },
                        modifier = (if (index == anchor) Modifier.tvAnchor(rowRequester) else Modifier)
                            .onFocusChanged { if (it.isFocused) memory.record(ROWS, 0, row.entryId, index) }
                            .testTag(playlistRowTag(index)),
                    )
                }
                item(key = "footer") { TvPagingFooter(status.paging.footer, onRetryMore, onRetryMore, tag = TAG_PLAYLIST_ROWS) }
            }
        }
    }
}

/** One video row: position, thumbnail, title and channel line. Focus scales and outlines the row. */
@Composable
private fun PlaylistRow(index: Int, row: TvPlaylistRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val alpha by animateFloatAsState(if (focused) 1f else TvFocus.UnfocusedAlpha, label = "tvPlaylistRowAlpha")
    val shape = RoundedCornerShape(TvSizes.Corner)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = row.video.title },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = TvColors.Ink,
            focusedContainerColor = TvColors.Surface, focusedContentColor = TvColors.Ink,
            pressedContainerColor = TvColors.Surface, pressedContentColor = TvColors.Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(TvFocus.BorderWidth, TvColors.Accent), shape = shape),
        ),
        interactionSource = source,
    ) {
        Row(Modifier.fillMaxWidth().padding(TvSpacing.sm).graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TvSpacing.lg)) {
            Text((index + 1).toString(), style = TvType.Caption, color = TvColors.Secondary, modifier = Modifier.width(32.dp))
            Box(Modifier.width(RowThumbWidth).aspectRatio(16f / 9f).clip(RoundedCornerShape(TvSizes.SmallCorner))) {
                YoutubeThumb(row.video.artwork, Modifier.fillMaxSize(), placeholderColor = TvColors.Surface)
                if (row.video.duration.isNotBlank()) {
                    Text(row.video.duration, style = TvType.Label, color = Color.White, maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                            .background(TvColors.Scrim).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(row.video.title, style = TvType.BodyStrong, color = TvColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val caption = row.video.captionText()
                if (caption.isNotBlank()) {
                    Text(caption, style = TvType.Caption, color = TvColors.Secondary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            if (focused) AppIcon(AppIcon.Play, Modifier.size(24.dp), color = TvColors.Accent)
        }
    }
}

private val PanelWidth = 340.dp
private val RowThumbWidth = 176.dp
private const val ROWS = "rows"

const val TAG_PLAYLIST = "tv-playlist"
const val TAG_PLAYLIST_SKELETON = "tv-playlist-skeleton"
const val TAG_PLAYLIST_RETRY = "tv-playlist-retry"
const val TAG_PLAYLIST_TITLE = "tv-playlist-title"
const val TAG_PLAYLIST_OWNER = "tv-playlist-owner"
const val TAG_PLAYLIST_PLAY_ALL = "tv-playlist-play-all"
const val TAG_PLAYLIST_SHUFFLE = "tv-playlist-shuffle"
const val TAG_PLAYLIST_ROWS = "tv-playlist-rows"
const val TAG_PLAYLIST_ENUMERATING = "tv-playlist-enumerating"
fun playlistRowTag(index: Int) = "tv-playlist-row-$index"
