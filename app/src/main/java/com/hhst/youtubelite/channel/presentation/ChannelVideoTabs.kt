package com.hhst.youtubelite.channel.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.channel.domain.ChannelAction
import com.hhst.youtubelite.channel.domain.ChannelHomeShelf
import com.hhst.youtubelite.channel.domain.ChannelPage
import com.hhst.youtubelite.channel.domain.ChannelPlaylistSort
import com.hhst.youtubelite.channel.domain.ChannelSection
import com.hhst.youtubelite.channel.domain.ChannelTab
import com.hhst.youtubelite.channel.domain.ChannelVideo
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.ui.components.FilterChip
import com.hhst.youtubelite.ui.components.PlaylistCard
import com.hhst.youtubelite.ui.components.PlaylistCardSkeleton
import com.hhst.youtubelite.ui.components.ShortCardSkeleton
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.VideoCard
import com.hhst.youtubelite.ui.components.VideoCardSkeleton
import com.hhst.youtubelite.ui.components.VideoRow
import com.hhst.youtubelite.ui.components.VideoRowSkeleton
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.miniDockListPadding

/*
 * Home, Videos and Playlists tabs, plus the pieces every tab shares: the card grid inside the
 * tab's own LazyColumn (index 0 is the header, index 1 the pinned tab row, which the tab
 * continuity in ChannelScreen relies on), per-tab loading / empty / failed states and the paging
 * footer. Grids are rows of cells so each tab keeps its LazyListState.
 */

/** Lays [items] out as rows of [columns] equal cells with the page inset. One column is a plain list. */
internal fun <T> LazyListScope.channelGrid(
    items: List<T>,
    columns: Int,
    width: Float,
    key: (T) -> Any,
    contentType: String,
    rowGap: Dp = MinSpace.xl,
    cell: @Composable (T) -> Unit,
) {
    val inset = ChannelMetrics.inset(width)
    if (columns <= 1) {
        items(items, key = key, contentType = { contentType }) { item ->
            Box(Modifier.fillMaxWidth().padding(horizontal = inset).padding(bottom = rowGap)) { cell(item) }
        }
        return
    }
    val rows = items.chunked(columns)
    items(rows, key = { key(it.first()) }, contentType = { "$contentType-row" }) { row ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = inset).padding(bottom = rowGap),
            horizontalArrangement = Arrangement.spacedBy(ChannelMetrics.GridGap),
        ) {
            row.forEach { item -> Box(Modifier.weight(1f)) { cell(item) } }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * One channel video: a list row on phones, a card in the grid from 600dp. The thumbnail keeps its
 * tag and spoken "title, duration" label; LIVE uses the Danger badge of the card family.
 */
@Composable
internal fun ChannelVideoItem(video: ChannelVideo, width: Float, card: Boolean, onAction: (ChannelAction) -> Unit) {
    val title = rememberDeArrowTitle(video.id, video.title)
    val metadata = listOf(video.views, video.age).filter(String::isNotBlank).joinToString(" · ")
    val badge = if (video.live) stringResource(R.string.player_live) else video.duration
    val thumbTag = "channel-thumb-${video.id}"
    val thumbLabel = stringResource(R.string.a11y_title_duration, video.title, video.duration)
    val menu = stringResource(R.string.a11y_more_actions_for, video.title)
    val open = { onAction(ChannelAction.OpenVideo(video.id)) }
    val openMenu = { onAction(ChannelAction.OpenItemMenu(video.id)) }
    val tag = Modifier.testTag("channel-video-${video.id}")
    if (card) VideoCard(
        title, metadata, video.artwork, open, tag, durationLabel = badge, live = video.live,
        onMenu = openMenu, menuDescription = menu, videoId = video.id, thumbnailTag = thumbTag, thumbnailDescription = thumbLabel,
    ) else VideoRow(
        title, metadata, video.artwork, open, tag, durationLabel = badge, live = video.live,
        onMenu = openMenu, menuDescription = menu, videoId = video.id, thumbnailTag = thumbTag, thumbnailDescription = thumbLabel,
        thumbnailWidth = ChannelMetrics.rowThumbnail(width, LocalDensity.current.fontScale),
    )
}

/** A playlist of the channel on the shared playlist card; the row tag is the one other screens' tests scroll to. */
@Composable
internal fun ChannelPlaylistItem(playlist: PlaylistSummary, onAction: (ChannelAction) -> Unit) {
    PlaylistCard(
        playlist.title, stringResource(R.string.ui_playlist_owner, playlist.owner), playlist.artwork,
        { onAction(ChannelAction.OpenPlaylist(playlist.id)) },
        Modifier.testTag("channel-playlist-${playlist.id}"),
        countLabel = playlist.videoCountText ?: playlist.videoCount?.toString(),
        onMenu = { onAction(ChannelAction.OpenPlaylistMenu(playlist.id)) },
        menuDescription = stringResource(R.string.a11y_more_actions_for, playlist.title),
        titleTag = "playlist-title-${playlist.id}",
    )
}

/** Outline of the first videos, in the layout the loaded tab will have. */
@Composable
internal fun ChannelVideoPlaceholders(width: Float, count: Int, tag: (Int) -> String) {
    val columns = ChannelMetrics.videoColumns(width)
    val inset = ChannelMetrics.inset(width)
    val thumbnail = ChannelMetrics.rowThumbnail(width, LocalDensity.current.fontScale)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = inset).clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(MinSpace.xl),
    ) {
        if (columns == 1) repeat(count) { VideoRowSkeleton(Modifier.testTag(tag(it)), thumbnail) }
        else Row(horizontalArrangement = Arrangement.spacedBy(ChannelMetrics.GridGap)) {
            repeat(columns) { VideoCardSkeleton(Modifier.weight(1f).testTag(tag(it))) }
        }
    }
}

/**
 * A deferred tab's own loading, empty or failed state under the header, which stays usable.
 * Loading draws the tab's cards in outline; Retry is the ViewModel's section retry.
 */
@Composable
internal fun ChannelTabStatus(tab: ChannelTab, section: ChannelSection<*>, width: Float, onAction: (ChannelAction) -> Unit) {
    val key = tab.name.lowercase()
    when (section) {
        ChannelSection.Loading -> Box(Modifier.fillMaxWidth().padding(top = MinSpace.sm).testTag("channel-$key-loading")) {
            ChannelTabPlaceholder(tab, width)
        }
        ChannelSection.Empty -> SectionState(
            stringResource(emptyTitle(tab)), tag = "channel-$key-empty", icon = emptyIcon(tab),
        )
        ChannelSection.Failed -> SectionState(
            stringResource(failedTitle(tab)), tag = "channel-$key-failed",
            message = stringResource(R.string.ui_please_try_again), kind = StateKind.Error,
            actionLabel = stringResource(R.string.retry), actionTag = "channel-$key-retry",
            onAction = { onAction(ChannelAction.RetrySection(tab)) },
        )
        is ChannelSection.Ready -> Unit
    }
}

@Composable
private fun ChannelTabPlaceholder(tab: ChannelTab, width: Float) {
    val inset = ChannelMetrics.inset(width)
    when (tab) {
        ChannelTab.Shorts -> Row(
            Modifier.fillMaxWidth().padding(horizontal = inset).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(ChannelMetrics.GridGap),
        ) { repeat(ChannelMetrics.shortsColumns(width)) { ShortCardSkeleton(Modifier.weight(1f)) } }
        ChannelTab.Playlists -> Row(
            Modifier.fillMaxWidth().padding(horizontal = inset).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(ChannelMetrics.GridGap),
        ) { repeat(ChannelMetrics.playlistColumns(width)) { PlaylistCardSkeleton(Modifier.weight(1f)) } }
        ChannelTab.Posts -> Column(
            Modifier.fillMaxWidth().padding(horizontal = inset).clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(MinSpace.sm),
        ) {
            repeat(2) {
                SkeletonLine(fraction = 0.4f)
                SkeletonLine(fraction = 0.95f)
                SkeletonLine(fraction = 0.8f)
                Spacer(Modifier.padding(top = MinSpace.lg))
            }
        }
        else -> ChannelVideoPlaceholders(width, count = 3) { "channel-$tab-placeholder-$it" }
    }
}

private fun emptyTitle(tab: ChannelTab): Int = when (tab) {
    ChannelTab.Shorts -> R.string.ui_no_shorts_yet
    ChannelTab.Live -> R.string.ui_no_live_streams_yet
    ChannelTab.Posts -> R.string.ui_no_posts_yet
    ChannelTab.Playlists -> R.string.ui_this_channel_has_no_playlists
    else -> R.string.ui_this_channel_has_no_videos
}

private fun emptyIcon(tab: ChannelTab): AppIcon = when (tab) {
    ChannelTab.Shorts -> AppIcon.Shorts
    ChannelTab.Live -> AppIcon.Live
    ChannelTab.Posts -> AppIcon.Comment
    ChannelTab.Playlists -> AppIcon.Playlist
    else -> AppIcon.VideoOutline
}

private fun failedTitle(tab: ChannelTab): Int = when (tab) {
    ChannelTab.Shorts -> R.string.ui_shorts_could_not_be_loaded
    ChannelTab.Live -> R.string.ui_live_streams_could_not_be_loaded
    ChannelTab.Posts -> R.string.ui_posts_could_not_be_loaded
    else -> R.string.ui_couldn_t_load_this_tab
}

/** Paging footer of a tab, shown while more pages exist or an append is running or failed. */
internal fun LazyListScope.channelAppend(tag: String, canPage: Boolean, append: AppendState, failedMessage: Int, onRetry: () -> Unit) {
    if (canPage || append != AppendState.Idle) item(key = tag) {
        AppendFooter(append, stringResource(failedMessage), onRetry, tag = tag)
    }
}

/** A channel with no videos at all (Home without shelves, an empty Videos tab). */
@Composable
internal fun ChannelNoVideos() {
    SectionState(stringResource(R.string.ui_this_channel_has_no_videos), tag = "channel-empty")
}

@Composable
internal fun ChannelHomeTab(
    page: ChannelPage,
    width: Float,
    state: LazyListState,
    onAction: (ChannelAction) -> Unit,
    subscribePending: Boolean = false,
) {
    val shelves = remember(page.homeShelves, page.videos) {
        page.homeShelves.ifEmpty {
            page.videos.filter { it.homeShelf }.takeIf { it.isNotEmpty() }
                ?.let { listOf(ChannelHomeShelf("", videos = it)) }.orEmpty()
        }
    }
    val viewport by rememberUpdatedState(width)
    val columns = ChannelMetrics.videoColumns(width)
    val inset = ChannelMetrics.inset(width)
    val dock = miniDockListPadding(MinSpace.xxl)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().then(dock.modifier).testTag("channel-home-list"),
        contentPadding = PaddingValues(bottom = dock.bottom),
    ) {
        item { ChannelHeader(page, viewport, onAction, subscribePending) }
        // The tab row scrolls with the header and pins once the header is gone.
        stickyHeader("channel-tabbar") { ChannelTabBar(ChannelTab.Home, onAction) }
        if (shelves.isEmpty()) {
            item { ChannelNoVideos() }
        } else shelves.forEachIndexed { index, shelf ->
            if (shelf.title.isNotBlank() || shelf.description.isNotBlank()) item("shelf-$index") {
                Column(Modifier.fillMaxWidth().padding(start = inset, end = inset, top = MinSpace.lg, bottom = MinSpace.md)) {
                    if (shelf.title.isNotBlank()) AppText(shelf.title, MinType.Title, Modifier.semantics { heading() }, maxLines = 2)
                    if (shelf.description.isNotBlank()) AppText(shelf.description, MinType.Caption, Modifier.padding(top = MinSpace.xxs),
                        color = DesignTokens.Secondary, maxLines = 2)
                }
            } else item("shelf-$index") { Spacer(Modifier.padding(top = MinSpace.md)) }
            channelGrid(shelf.videos, columns, width, key = { "$index:${it.id}" }, contentType = "video") { video ->
                ChannelVideoItem(video, viewport, card = columns > 1, onAction)
            }
            channelGrid(shelf.playlists, ChannelMetrics.playlistColumns(width), width,
                key = { "$index:playlist:${it.id}" }, contentType = "playlist") { playlist ->
                ChannelPlaylistItem(playlist, onAction)
            }
        }
    }
}

@Composable
internal fun ChannelVideosTab(
    page: ChannelPage,
    width: Float,
    state: LazyListState,
    onAction: (ChannelAction) -> Unit,
    append: AppendState = AppendState.Idle,
    videosTab: ChannelSection<Unit> = ChannelSection.Ready(Unit),
    subscribePending: Boolean = false,
) {
    val viewport by rememberUpdatedState(width)
    val canPage = page.videosNextKey != null
    val columns = ChannelMetrics.videoColumns(width)
    ObserveChannelPagination(state, canPage, append) { onAction(ChannelAction.LoadVideosNext) }
    val dock = miniDockListPadding(MinSpace.xxl)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().then(dock.modifier).testTag("channel-videos-list"),
        contentPadding = PaddingValues(bottom = dock.bottom),
    ) {
        item { ChannelHeader(page, viewport, onAction, subscribePending) }
        stickyHeader("channel-tabbar") { ChannelTabBar(ChannelTab.Videos, onAction) }
        item { Spacer(Modifier.padding(top = MinSpace.md)) }
        if (videosTab !is ChannelSection.Ready) {
            // A deferred first read reports here; the header above is already usable.
            item { ChannelTabStatus(ChannelTab.Videos, videosTab, viewport, onAction) }
        } else if (page.videos.isEmpty()) {
            item { ChannelNoVideos() }
        } else {
            channelGrid(page.videos, columns, width, key = { it.id }, contentType = "video") { video ->
                ChannelVideoItem(video, viewport, card = columns > 1, onAction)
            }
            channelAppend("channel-videos-append", canPage, append, R.string.ui_couldn_t_load_more_videos_tap_to_retry) {
                onAction(ChannelAction.LoadVideosNext)
            }
        }
    }
}

@Composable
internal fun ChannelPlaylistsTab(
    page: ChannelPage,
    width: Float,
    height: Float,
    state: LazyListState,
    sort: ChannelPlaylistSort,
    playlists: ChannelSection<List<PlaylistSummary>>,
    onAction: (ChannelAction) -> Unit,
    canPage: Boolean = false,
    append: AppendState = AppendState.Idle,
    subscribePending: Boolean = false,
) {
    val viewport by rememberUpdatedState(width)
    // "Last video added" only exists when the source carries update dates;
    // otherwise the control would present server order as a sort it cannot perform.
    val rows = (playlists as? ChannelSection.Ready)?.value.orEmpty()
    val canSortByUpdate = remember(rows) { rows.any { it.updatedDays != null } }
    val sorted = remember(rows, sort) {
        if (sort == ChannelPlaylistSort.LastVideo) rows.sortedBy { it.updatedDays ?: Int.MAX_VALUE } else rows
    }
    ObserveChannelPagination(state, canPage, append) { onAction(ChannelAction.LoadPlaylistsNext) }
    val dock = miniDockListPadding(MinSpace.xxl)
    LazyColumn(Modifier.fillMaxSize().then(dock.modifier).testTag("channel-playlists-list"), state = state, contentPadding = PaddingValues(bottom = dock.bottom)) {
        item { ChannelHeader(page, viewport, onAction, subscribePending) }
        stickyHeader("channel-tabbar") { ChannelTabBar(ChannelTab.Playlists, onAction) }
        if (canSortByUpdate) item {
            ChannelChipRow("channel-playlist-sort", width) {
                ChannelPlaylistSort.entries.forEach { option ->
                    FilterChip(stringResource(option.labelResource), option == sort, { onAction(ChannelAction.SortPlaylists(option)) },
                        Modifier.testTag("channel-playlist-sort-${option.name.lowercase()}"))
                }
            }
        } else item { Spacer(Modifier.padding(top = MinSpace.md)) }
        when (playlists) {
            is ChannelSection.Ready -> {
                if (rows.isEmpty()) item { ChannelTabStatus(ChannelTab.Playlists, ChannelSection.Empty, viewport, onAction) }
                channelGrid(sorted, ChannelMetrics.playlistColumns(width), width, key = { it.id }, contentType = "playlist") { playlist ->
                    ChannelPlaylistItem(playlist, onAction)
                }
            }
            else -> item { ChannelTabStatus(ChannelTab.Playlists, playlists, viewport, onAction) }
        }
        channelAppend("channel-playlists-append", canPage, append, R.string.ui_couldn_t_load_more_playlists_tap_to_retry) {
            onAction(ChannelAction.LoadPlaylistsNext)
        }
    }
}

/** A horizontally scrolling row of chips under the tab row, with the page inset. */
@Composable
internal fun ChannelChipRow(tag: String, width: Float, content: @Composable () -> Unit) {
    val inset = ChannelMetrics.inset(width)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(tag).padding(horizontal = inset - MinSpace.xxs, vertical = MinSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm),
    ) { content() }
}
