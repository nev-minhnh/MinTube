package com.hhst.youtubelite.tv.subscriptions

import com.hhst.youtubelite.tv.design.tvFocusRestorer
import com.hhst.youtubelite.tv.design.tvAnchor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvCard
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvFocusRequesters
import com.hhst.youtubelite.tv.design.TvFocusRow
import com.hhst.youtubelite.tv.design.TvRowHeader
import com.hhst.youtubelite.tv.design.TvSkeletonRow
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.design.rememberTvFocusMemory
import com.hhst.youtubelite.tv.library.TvAvatarSkeleton
import com.hhst.youtubelite.tv.library.TvCentered
import com.hhst.youtubelite.tv.library.TvChannelAvatar
import com.hhst.youtubelite.tv.library.TvFocusKeeper
import com.hhst.youtubelite.tv.library.TvGridFooter
import com.hhst.youtubelite.tv.library.TvGridMetrics
import com.hhst.youtubelite.tv.library.TvPageTitle
import com.hhst.youtubelite.ui.design.AppIcon

/**
 * TV Subscriptions destination: a strip of subscribed channels (each opens the TV channel page),
 * then the subscriptions feed as a paged grid. State-hoisted; [TvSubscriptionsController] wires it.
 */
@Composable
fun TvSubscriptionsScreen(
    state: TvSubscriptionsUiState,
    onOpenVideo: (TvVideo) -> Unit,
    onOpenChannel: (TvChannel) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onLoadMoreChannels: () -> Unit,
    onRetryChannels: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    focusMemory: TvFocusMemory = rememberTvFocusMemory(),
) {
    Box(modifier.fillMaxSize().testTag(TAG_SUBSCRIPTIONS)) {
        when (state.status) {
            TvSubscriptionsStatus.Loading -> Column(
                Modifier.fillMaxSize().padding(start = Start, top = TvSpacing.OverscanVertical).testTag(TAG_SUBSCRIPTIONS_SKELETON),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
            ) {
                TvPageTitle(stringResource(R.string.tv_nav_subscriptions))
                TvAvatarSkeleton()
                repeat(2) { TvSkeletonRow() }
            }
            TvSubscriptionsStatus.SignedOut -> TvCentered {
                TvStateMessage(
                    title = stringResource(R.string.tv_subscriptions_signed_out_title),
                    message = stringResource(R.string.tv_subscriptions_signed_out_message),
                    icon = AppIcon.Subscriptions,
                    actionLabel = stringResource(R.string.ui_sign_in),
                    onAction = onSignIn,
                    actionModifier = Modifier.testTag(TAG_SUBSCRIPTIONS_SIGN_IN),
                )
            }
            TvSubscriptionsStatus.Failed -> TvCentered {
                TvStateMessage(
                    title = stringResource(R.string.tv_subscriptions_failed_title),
                    message = stringResource(R.string.tv_home_failed_message),
                    icon = AppIcon.WifiOff,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_SUBSCRIPTIONS_RETRY),
                )
            }
            TvSubscriptionsStatus.Ready -> Feed(state, onOpenVideo, onOpenChannel, onLoadMore, onRetryMore, onLoadMoreChannels,
                onRetryChannels, onRetry, focusMemory)
        }
    }
}

@Composable
private fun Feed(
    state: TvSubscriptionsUiState,
    onOpenVideo: (TvVideo) -> Unit,
    onOpenChannel: (TvChannel) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onLoadMoreChannels: () -> Unit,
    onRetryChannels: () -> Unit,
    onRetry: () -> Unit,
    memory: TvFocusMemory,
) {
    val grid = rememberLazyGridState()
    val strip = rememberLazyListState()
    val requesters = remember { TvFocusRequesters() }
    var focused by remember { mutableStateOf(false) }
    val channels = (state.channels as? TvChannelStrip.Ready)?.channels.orEmpty()
    val focusRows = remember(channels, state.videos) {
        listOf(TvFocusRow(ROW_CHANNELS, channels.map(TvChannel::id)), TvFocusRow(ROW_FEED, state.videos.map(TvVideo::id)))
    }
    TvFocusKeeper(focusRows, memory, hasFocus = { focused }, initial = false, focus = { target ->
        val row = focusRows[target.rowIndex].key
        if (row == ROW_CHANNELS) {
            grid.scrollToItem(ITEM_CHANNELS)
            strip.scrollToItem(target.itemIndex)
        } else grid.scrollToItem(FEED_OFFSET + target.itemIndex)
        withFrameNanos { }
        requesters.request(row)
    })
    val nearEnd by remember(grid) { derivedStateOf {
        val info = grid.layoutInfo
        info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - PAGE_AHEAD
    } }
    LaunchedEffect(nearEnd, state.canPage, state.videos.size) { if (nearEnd && state.canPage) onLoadMore() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val metrics = TvGridMetrics.of(maxWidth - Start - TvSpacing.OverscanHorizontal)
        val feedRequester = requesters.of(ROW_FEED)
        val feedAnchor = memory.indexIn(ROW_FEED, state.videos.map(TvVideo::id))
        LazyVerticalGrid(
            columns = GridCells.Fixed(metrics.columns),
            state = grid,
            modifier = Modifier.fillMaxSize().onFocusChanged { focused = it.hasFocus }.testTag(TAG_SUBSCRIPTIONS_GRID),
            contentPadding = PaddingValues(start = Start, end = TvSpacing.OverscanHorizontal,
                top = TvSpacing.OverscanVertical, bottom = TvSpacing.OverscanVertical + 48.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.xl),
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) { TvPageTitle(stringResource(R.string.tv_nav_subscriptions)) }
            item(key = "channels", span = { GridItemSpan(maxLineSpan) }) {
                ChannelStrip(state.channels, strip, memory, requesters, onOpenChannel, onLoadMoreChannels, onRetryChannels)
            }
            item(key = "feed-header", span = { GridItemSpan(maxLineSpan) }) {
                TvRowHeader(stringResource(R.string.tv_subscriptions_latest), Modifier.padding(top = TvSpacing.sm))
            }
            if (state.feedEmpty) item(key = "feed-empty", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tv_subscriptions_empty), style = TvType.Body, color = TvColors.Secondary)
                    Spacer(Modifier.width(TvSpacing.lg))
                    TvButton(stringResource(R.string.retry), onRetry, Modifier.testTag(TAG_SUBSCRIPTIONS_RETRY), icon = AppIcon.Refresh)
                }
            }
            itemsIndexed(state.videos, key = { _, video -> video.id }) { index, video ->
                Box(Modifier.padding(top = 6.dp)) {
                    TvCard(
                        video = video,
                        onClick = { onOpenVideo(video) },
                        width = metrics.cardWidth,
                        modifier = (if (index == feedAnchor) Modifier.focusRequester(feedRequester) else Modifier)
                            .testTag(subscriptionCardTag(index)),
                        onFocused = { memory.record(ROW_FEED, 1, video.id, index) },
                    )
                }
            }
            if (state.footer != TvGridFooter.None) item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = TvSpacing.lg), contentAlignment = Alignment.Center) {
                    if (state.footer == TvGridFooter.Loading) {
                        Text(stringResource(R.string.tv_loading), style = TvType.Body, color = TvColors.Secondary)
                    } else {
                        TvButton(stringResource(if (state.footer == TvGridFooter.Retry) R.string.retry else R.string.tv_load_more),
                            onRetryMore, Modifier.testTag(TAG_SUBSCRIPTIONS_MORE), icon = AppIcon.Refresh)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelStrip(
    strip: TvChannelStrip,
    scroll: LazyListState,
    memory: TvFocusMemory,
    requesters: TvFocusRequesters,
    onOpenChannel: (TvChannel) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when (strip) {
        TvChannelStrip.Loading -> TvAvatarSkeleton()
        // Retry leads the row: as the top-left action it is where focus enters the page.
        TvChannelStrip.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(stringResource(R.string.retry), onRetry, Modifier.testTag(TAG_CHANNELS_RETRY), icon = AppIcon.Refresh)
            Spacer(Modifier.width(TvSpacing.lg))
            Text(stringResource(R.string.tv_subscriptions_channels_failed), style = TvType.Body, color = TvColors.Secondary)
        }
        is TvChannelStrip.Ready -> {
            if (strip.channels.isEmpty()) return
            val requester = requesters.of(ROW_CHANNELS)
            val anchor = memory.indexIn(ROW_CHANNELS, strip.channels.map(TvChannel::id))
            val nearEnd by remember(scroll) { derivedStateOf {
                val info = scroll.layoutInfo
                info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 4
            } }
            LaunchedEffect(nearEnd, strip.canPage, strip.channels.size) { if (nearEnd && strip.canPage) onLoadMore() }
            LazyRow(
                state = scroll,
                modifier = Modifier.fillMaxWidth().tvFocusRestorer(requester).testTag(TAG_CHANNELS),
                // Room for the focus scale; the grid already insets the start edge.
                contentPadding = PaddingValues(top = 10.dp, bottom = 4.dp, end = TvSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap),
            ) {
                itemsIndexed(strip.channels, key = { _, channel -> channel.id }) { index, channel ->
                    TvChannelAvatar(
                        name = channel.name,
                        avatar = channel.avatar,
                        hasNew = channel.hasNew,
                        onClick = { onOpenChannel(channel) },
                        modifier = (if (index == anchor) Modifier.tvAnchor(requester) else Modifier).testTag(channelTag(index)),
                        onFocused = { memory.record(ROW_CHANNELS, 0, channel.id, index) },
                    )
                }
            }
        }
    }
}

/** Matches Home's row inset inside the shell content. */
private val Start = TvSpacing.xl
private const val ROW_CHANNELS = "channels"
private const val ROW_FEED = "feed"
private const val ITEM_CHANNELS = 1
/** Title, channel strip and feed header precede the cards; the empty-feed line only replaces cards. */
private const val FEED_OFFSET = 3
private const val PAGE_AHEAD = 8

const val TAG_SUBSCRIPTIONS = "tv-subscriptions"
const val TAG_SUBSCRIPTIONS_GRID = "tv-subscriptions-grid"
const val TAG_SUBSCRIPTIONS_SKELETON = "tv-subscriptions-skeleton"
const val TAG_SUBSCRIPTIONS_RETRY = "tv-subscriptions-retry"
const val TAG_SUBSCRIPTIONS_SIGN_IN = "tv-subscriptions-sign-in"
const val TAG_SUBSCRIPTIONS_MORE = "tv-subscriptions-more"
const val TAG_CHANNELS = "tv-subscriptions-channels"
const val TAG_CHANNELS_RETRY = "tv-subscriptions-channels-retry"
fun channelTag(index: Int) = "tv-subscriptions-channel-$index"
fun subscriptionCardTag(index: Int) = "tv-subscriptions-card-$index"
