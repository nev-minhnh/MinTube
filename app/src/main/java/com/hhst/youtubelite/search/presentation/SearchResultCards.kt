package com.hhst.youtubelite.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.dearrow.VideoArtwork
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.search.domain.SearchAction
import com.hhst.youtubelite.search.domain.SearchChannel
import com.hhst.youtubelite.search.domain.SearchResult
import com.hhst.youtubelite.search.domain.SearchResultType
import com.hhst.youtubelite.search.domain.SearchResultsLayout
import com.hhst.youtubelite.ui.components.DurationBadge
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.PlaylistCard
import com.hhst.youtubelite.ui.components.ShortCard
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.components.ThumbnailProgress
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.LocalMotionScale
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.ShortsLogo
import com.hhst.youtubelite.ui.design.SubscribeButton
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.appDescription
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.watch.domain.WatchProvisionalStore
import com.hhst.youtubelite.watch.domain.toProvisional
import com.hhst.youtubelite.ui.design.CenteredListGutter
import com.hhst.youtubelite.ui.design.miniDockListPadding

/** Geometry shared by the result list and its skeleton, derived from the available window. */
internal data class SearchListSpec(val window: WindowClass, val rows: Boolean) {
    val inset: Dp get() = MinSpace.pageInset(window)
    val thumbnailWidth: Dp get() = if (window == WindowClass.Expanded) 240.dp else 200.dp
    val spacing: Dp get() = if (rows) MinSpace.lg else MinSpace.xl

    companion object {
        /** Thumbnail-beside-text rows from 600dp width and on any landscape phone; stacked cards otherwise. */
        fun of(width: Float, height: Float): SearchListSpec {
            val window = WindowClass.of(width.toInt())
            return SearchListSpec(window, window != WindowClass.Compact || SearchResultsLayout.listLockup(width, height))
        }
    }
}

/** How many leading channel rows the mixed list shows before the first video. */
private const val LeadingChannels = 3
/** Videos shown before the Shorts shelf and before the playlist shelf of the mixed list. */
private const val VideosBeforeShorts = 2
private const val VideosBeforePlaylists = 5

internal const val AllShortsKey = "all-shelf-shorts"
internal const val AllPlaylistsKey = "all-shelf-playlists"

/**
 * The "All" results: videos in server order, with the channels, Shorts and playlists the same
 * page already carries placed around them. Video rows keep their id as the lazy key, which is
 * what the visible-row reporting in [SearchResultsScreen] reads.
 */
@Composable
internal fun SearchAllList(
    results: List<SearchResult>,
    channels: List<SearchChannel>,
    shorts: List<SearchResult>,
    playlists: List<PlaylistSummary>,
    spec: SearchListSpec,
    listState: LazyListState,
    onAction: (SearchAction) -> Unit,
    subscriptions: Map<String, Boolean>,
    subscriptionsPending: Set<String>,
    footer: (@Composable () -> Unit)? = null,
) {
    val leading = channels.take(LeadingChannels)
    val shortsAfter = minOf(VideosBeforeShorts, results.size)
    val playlistsAfter = minOf(VideosBeforePlaylists, results.size)
    CenteredListGutter(SearchResultsLayout.ColumnMaxWidth.dp) { gutter ->
        val dock = miniDockListPadding(MinSpace.xxl)
        LazyColumn(
            Modifier.fillMaxSize().then(dock.modifier).testTag("search-results-list"),
            state = listState,
            contentPadding = PaddingValues(start = gutter.start, end = gutter.end, top = MinSpace.sm, bottom = dock.bottom),
            verticalArrangement = Arrangement.spacedBy(spec.spacing),
        ) {
            items(leading, key = { "all-channel-${it.id}" }, contentType = { "channel" }) { channel ->
                SearchChannelRow(channel, onAction, Modifier.padding(horizontal = spec.inset),
                    subscribed = subscriptions[channel.id] == true, pending = channel.id in subscriptionsPending,
                    enabled = subscriptionsPending.isEmpty())
            }
            fun shelves(position: Int) {
                if (position == shortsAfter && shorts.isNotEmpty()) item(key = AllShortsKey, contentType = "shorts-shelf") {
                    ShortsShelf(shorts, spec, onAction)
                }
                if (position == playlistsAfter && playlists.isNotEmpty()) item(key = AllPlaylistsKey, contentType = "playlist-shelf") {
                    PlaylistShelf(playlists, spec, onAction)
                }
            }
            results.forEachIndexed { index, result ->
                shelves(index)
                videoRow(result, spec, onAction)
            }
            shelves(results.size)
            if (footer != null) item(key = "pagination") { footer() }
        }
    }
}

private fun LazyListScope.videoRow(result: SearchResult, spec: SearchListSpec, onAction: (SearchAction) -> Unit) {
    item(key = result.id, contentType = "video") {
        Box(if (LocalMotionScale.current > 0f) Modifier.animateItem() else Modifier) {
            if (spec.rows) ListResultRow(result, spec, onAction) else StackedResultRow(result, spec, onAction)
        }
    }
}

@Composable
private fun ShelfHeader(title: String, type: SearchResultType, tag: String, onAction: (SearchAction) -> Unit, leading: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(MinSpace.sm))
        }
        AppText(title, MinType.Title, Modifier.weight(1f).semantics { heading() }, maxLines = 1)
        MinButton(stringResource(R.string.ui_view_all), { onAction(SearchAction.SelectResultType(type)) },
            kind = MinButtonKind.Text, testTag = tag)
    }
}

@Composable
private fun ShortsShelf(shorts: List<SearchResult>, spec: SearchListSpec, onAction: (SearchAction) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("search-all-shorts")) {
        Box(Modifier.padding(horizontal = spec.inset)) {
            ShelfHeader(stringResource(SearchResultType.Shorts.labelResource), SearchResultType.Shorts, "search-all-shorts-more", onAction) {
                ShortsLogo(Modifier.size(24.dp))
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = spec.inset), horizontalArrangement = Arrangement.spacedBy(MinSpace.md)) {
            items(shorts) { short ->
                ShortCard(
                    title = rememberDeArrowTitle(short.id, short.title),
                    artwork = short.artwork,
                    // In the mixed list a Short opens like any other result of this page.
                    onClick = { onAction(SearchAction.OpenResult(short.id)) },
                    modifier = Modifier.width(148.dp).testTag("search-all-short-${short.id}"),
                    metadata = short.views.takeIf(String::isNotBlank),
                    videoId = short.id,
                )
            }
        }
    }
}

@Composable
private fun PlaylistShelf(playlists: List<PlaylistSummary>, spec: SearchListSpec, onAction: (SearchAction) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("search-all-playlists")) {
        Box(Modifier.padding(horizontal = spec.inset)) {
            ShelfHeader(stringResource(SearchResultType.Playlists.labelResource), SearchResultType.Playlists, "search-all-playlists-more", onAction)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = spec.inset), horizontalArrangement = Arrangement.spacedBy(MinSpace.md)) {
            items(playlists) { playlist ->
                PlaylistCard(
                    title = playlist.title, metadata = playlist.owner, artwork = playlist.artwork,
                    onClick = { onAction(SearchAction.OpenPlaylist(playlist.id)) },
                    modifier = Modifier.width(240.dp).testTag("search-all-playlist-${playlist.id}"),
                    countLabel = playlist.countLabel,
                )
            }
        }
    }
}

/** Channel result: avatar, name, handle and subscriber line, subscribe button. */
@Composable
internal fun SearchChannelRow(channel: SearchChannel, onAction: (SearchAction) -> Unit, modifier: Modifier = Modifier,
    subscribed: Boolean = false, pending: Boolean = false, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("search-channel-result-${channel.id}")
        .clickable(role = Role.Button) { onAction(SearchAction.OpenChannel(channel.id)) },
        verticalAlignment = Alignment.CenterVertically) {
        Artwork(channel.artwork, Modifier.size(56.dp).clip(CircleShape).testTag("search-channel-avatar-${channel.id}"))
        Spacer(Modifier.width(MinSpace.md))
        Column(Modifier.weight(1f)) {
            AppText(channel.name, MinType.Subtitle, Modifier.testTag("search-channel-name-${channel.id}"), maxLines = 2)
            // Blank fields (production mapping may omit them) render no line.
            val metadata = listOf(channel.handle, channel.subscribers).filter(String::isNotBlank).joinToString(" · ")
            if (metadata.isNotBlank()) AppText(metadata, MinType.Caption, Modifier.padding(top = MinSpace.xxs),
                color = DesignTokens.Secondary, maxLines = 2)
        }
        Spacer(Modifier.width(MinSpace.sm))
        SubscribeButton(subscribed, { onAction(SearchAction.Subscribe(channel.id)) },
            pending = pending, enabled = enabled, testTag = "search-channel-subscribe-${channel.id}", channelId = channel.id)
    }
}

@Composable
private fun ListResultRow(result: SearchResult, spec: SearchListSpec, onAction: (SearchAction) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("search-result-${result.id}").padding(horizontal = spec.inset), verticalAlignment = Alignment.Top) {
        ResultThumbnail(result, Modifier.width(spec.thumbnailWidth).aspectRatio(16f / 9f), MinRadius.Field, onAction)
        Spacer(Modifier.width(MinSpace.md))
        Column(Modifier.weight(1f)) {
            ResultText(result, Modifier.fillMaxWidth().padding(top = MinSpace.xxs), onAction)
            ChannelAvatar(result, onAction)
        }
        ResultMenu(result, onAction)
    }
}

@Composable
private fun StackedResultRow(result: SearchResult, spec: SearchListSpec, onAction: (SearchAction) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("search-result-${result.id}").padding(horizontal = spec.inset)) {
        ResultThumbnail(result, Modifier.fillMaxWidth().aspectRatio(16f / 9f), MinRadius.Card, onAction)
        Row(Modifier.fillMaxWidth().padding(top = MinSpace.sm), verticalAlignment = Alignment.Top) {
            ChannelAvatar(result, onAction)
            Spacer(Modifier.width(MinSpace.xs))
            ResultText(result, Modifier.weight(1f).padding(top = MinSpace.xs), onAction)
            ResultMenu(result, onAction)
        }
    }
}

@Composable
private fun ResultThumbnail(result: SearchResult, modifier: Modifier, shape: Shape, onAction: (SearchAction) -> Unit) {
    Box(modifier.testTag("search-thumb-${result.id}").clip(shape)
        .clickable(role = Role.Button) {
            WatchProvisionalStore.put(result.toProvisional())
            onAction(SearchAction.OpenResult(result.id))
        }
        .appDescription(R.string.a11y_title_duration, result.title, result.duration)) {
        VideoArtwork(result.id, result.artwork, Modifier.fillMaxSize())
        result.progress?.let { ThumbnailProgress(it) }
        if (result.duration.isNotBlank()) DurationBadge(result.duration, modifier = Modifier.testTag("search-badge-${result.id}"))
    }
}

@Composable
private fun ResultText(result: SearchResult, modifier: Modifier, onAction: (SearchAction) -> Unit) {
    Column(modifier.testTag("search-text-${result.id}").clickable(role = Role.Button) {
        WatchProvisionalStore.put(result.toProvisional())
        onAction(SearchAction.OpenResult(result.id))
    }) {
        AppText(rememberDeArrowTitle(result.id, result.title), MinType.Subtitle,
            Modifier.fillMaxWidth().testTag("search-title-${result.id}"), maxLines = 2)
        // Only non-empty fields join: a missing view count or date must not leave
        // orphan separators. All fields blank renders nothing.
        val byline = listOf(result.channel, result.views, result.age).filter(String::isNotBlank).joinToString(" · ")
        if (byline.isNotBlank()) AppText(byline, MinType.Caption,
            Modifier.fillMaxWidth().padding(top = MinSpace.xxs).testTag("search-byline-${result.id}"),
            color = DesignTokens.Secondary, maxLines = 2)
        result.badge?.takeIf(String::isNotBlank)?.let { badge ->
            Box(Modifier.padding(top = MinSpace.xs).background(DesignTokens.Surface, MinRadius.Badge)
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                AppText(badge, MinType.Micro, color = DesignTokens.Secondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChannelAvatar(result: SearchResult, onAction: (SearchAction) -> Unit) {
    Box(Modifier.size(MinSpace.Touch).clip(CircleShape).testTag("search-channel-${result.id}")
        .clickable(role = Role.Button) { onAction(SearchAction.OpenChannel(result.channelId)) }
        .appDescription(R.string.a11y_go_to_channel, result.channel), contentAlignment = Alignment.Center) {
        com.hhst.youtubelite.ui.components.MinAvatar(result.avatar, result.channel, size = 36.dp)
    }
}

@Composable
private fun ResultMenu(result: SearchResult, onAction: (SearchAction) -> Unit) {
    AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_actions_for, result.title)) {
        onAction(SearchAction.OpenItemMenu(result.id))
    }
}

@Composable
internal fun SearchSkeleton(spec: SearchListSpec) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = SearchResultsLayout.ColumnMaxWidth.dp).fillMaxSize().testTag("search-skeleton").clearAndSetSemantics {},
            contentPadding = PaddingValues(start = spec.inset, end = spec.inset, top = MinSpace.sm, bottom = MinSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(spec.spacing),
            userScrollEnabled = false,
        ) {
            items(6) { index ->
                val text: @Composable () -> Unit = {
                    Column(Modifier.fillMaxWidth()) {
                        SkeletonLine(Modifier.widthIn(max = 300.dp).testTag("skeleton-title-$index"), height = 16.dp)
                        Spacer(Modifier.height(MinSpace.sm))
                        SkeletonLine(Modifier.width(120.dp).testTag("skeleton-byline-$index"), height = 12.dp)
                    }
                }
                if (spec.rows) Row(Modifier.fillMaxWidth().testTag("skeleton-card-$index")) {
                    SkeletonBox(Modifier.width(spec.thumbnailWidth).aspectRatio(16f / 9f).testTag("skeleton-thumb-$index"), MinRadius.Field)
                    Spacer(Modifier.width(MinSpace.md))
                    Box(Modifier.weight(1f).padding(top = MinSpace.xs)) { text() }
                } else Column(Modifier.fillMaxWidth().testTag("skeleton-card-$index")) {
                    SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("skeleton-thumb-$index"), MinRadius.Card)
                    Box(Modifier.fillMaxWidth().padding(top = MinSpace.md)) { text() }
                }
            }
        }
    }
}

@Composable
internal fun SearchNoResults(query: String, type: SearchResultType, onAction: (SearchAction) -> Unit) {
    val message = when (type) {
        SearchResultType.Playlists -> R.string.ui_search_no_playlists
        SearchResultType.Shorts -> R.string.ui_search_no_shorts
        SearchResultType.Channels -> R.string.ui_search_no_channels
        SearchResultType.All -> R.string.ui_search_no_videos
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("search-empty"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        StateMessage(
            title = stringResource(R.string.ui_no_results_found),
            message = stringResource(message, query),
            kind = StateKind.Empty,
            icon = AppIcon.Search,
            actionLabel = stringResource(R.string.browse_search_edit),
            onAction = { onAction(SearchAction.OpenSearch) },
            tag = "search-empty-message",
        )
    }
}

@Composable
internal fun SearchFailed(onAction: (SearchAction) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("search-error"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        StateMessage(
            title = stringResource(R.string.ui_something_went_wrong),
            message = stringResource(R.string.ui_please_try_again),
            kind = StateKind.Error,
            actionLabel = stringResource(R.string.retry),
            onAction = { onAction(SearchAction.Retry) },
            tag = "search-error-message",
        )
    }
}
