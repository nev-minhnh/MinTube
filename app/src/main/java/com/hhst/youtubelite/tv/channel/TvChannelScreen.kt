package com.hhst.youtubelite.tv.channel

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.channel.domain.ChannelTab
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvCard
import com.hhst.youtubelite.tv.design.TvChip
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvFocusRequesters
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSkeletonRow
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.rememberTvFocusMemory
import com.hhst.youtubelite.tv.grid.TvGridPagingEffect
import com.hhst.youtubelite.tv.grid.TvPagingFooter
import com.hhst.youtubelite.tv.grid.TvRestoreFocusOnResume
import com.hhst.youtubelite.tv.grid.requestWhenReady
import com.hhst.youtubelite.tv.grid.tvGridColumns
import com.hhst.youtubelite.ui.YoutubeThumb
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.labelResource

/**
 * Full-screen TV channel page: banner header with avatar, name, subscriber line and Subscribe,
 * a tab row (a tab is selected when it takes focus, as TV tab rows do), and the tab's card grid
 * with paging. One lazy grid scrolls the whole page. Initial focus is the selected tab; each tab
 * remembers its focused card, restored after Back from Watch. State-hoisted.
 */
@Composable
fun TvChannelScreen(
    status: TvChannelStatus,
    onSelectTab: (ChannelTab) -> Unit,
    onSubscribe: () -> Unit,
    onOpen: (TvChannelCard) -> Unit,
    onLoadMore: (ChannelTab) -> Unit,
    onRetryTab: (ChannelTab) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    focusMemory: TvFocusMemory = rememberTvFocusMemory(),
) {
    Box(modifier.fillMaxSize().background(TvColors.Background).testTag(TAG_CHANNEL)) {
        when (status) {
            TvChannelStatus.Loading -> Column(
                Modifier.fillMaxSize().padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical)
                    .testTag(TAG_CHANNEL_SKELETON),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
            ) {
                Box(Modifier.fillMaxWidth().height(140.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(TvSizes.Corner))
                    .background(TvColors.Surface))
                repeat(2) { TvSkeletonRow(showHeader = false) }
            }
            TvChannelStatus.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TvStateMessage(
                    title = stringResource(R.string.tv_channel_failed_title),
                    message = stringResource(R.string.tv_home_failed_message),
                    icon = AppIcon.WifiOff,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_CHANNEL_RETRY),
                    requestFocus = true,
                )
            }
            is TvChannelStatus.Ready -> ChannelPage(status, onSelectTab, onSubscribe, onOpen, onLoadMore, onRetryTab, focusMemory)
        }
    }
}

@Composable
private fun ChannelPage(
    status: TvChannelStatus.Ready,
    onSelectTab: (ChannelTab) -> Unit,
    onSubscribe: () -> Unit,
    onOpen: (TvChannelCard) -> Unit,
    onLoadMore: (ChannelTab) -> Unit,
    onRetryTab: (ChannelTab) -> Unit,
    memory: TvFocusMemory,
) {
    val grid = rememberLazyGridState()
    val tabRequesters = remember { TvFocusRequesters() }
    val cardRequesters = remember { TvFocusRequesters() }
    val stateRequester = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    val selected = status.selected
    val gridState = status.grid
    val cards = (gridState as? TvChannelGrid.Ready)?.cards.orEmpty()
    val keys = remember(cards) { cards.map(TvChannelCard::key) }
    val anchor = memory.indexIn(selected.name, keys)
    // First paint: focus the selected tab, so Left/Right browse tabs and Down enters the grid.
    LaunchedEffect(Unit) { tabRequesters.of(selected.name).requestWhenReady() }
    TvRestoreFocusOnResume(selected, hasFocus = { pageFocused }) {
        if (memory.lastRowKey == selected.name && cards.isNotEmpty()) {
            if (anchor >= 0) grid.scrollToItem(anchor + HEADER_ITEMS)
            if (cardRequesters.of(selected.name).requestWhenReady(attempts = 6)) return@TvRestoreFocusOnResume
        }
        tabRequesters.of(selected.name).requestWhenReady(attempts = 6)
    }
    TvGridPagingEffect(grid, (gridState as? TvChannelGrid.Ready)?.paging ?: com.hhst.youtubelite.tv.grid.TvPaging(),
        cards.size, { onLoadMore(selected) })
    val short = selected == ChannelTab.Shorts
    val cardWidth = if (short) TvSizes.ShortCardWidth else TvSizes.CardWidth
    BoxWithConstraints(Modifier.fillMaxSize().onFocusChanged { pageFocused = it.hasFocus }) {
        val inset = TvSpacing.OverscanHorizontal
        val columns = tvGridColumns(maxWidth - inset * 2, cardWidth, TvSpacing.CardGap)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            modifier = Modifier.fillMaxSize().testTag(TAG_CHANNEL_GRID),
            contentPadding = PaddingValues(start = inset, end = inset, bottom = TvSpacing.OverscanVertical + 48.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                ChannelHeader(status.header, onSubscribe)
            }
            item(key = "tabs", span = { GridItemSpan(maxLineSpan) }, contentType = "tabs") {
                // A full-width group: Up from any card of the first row lands on the selected tab, not on
                // Subscribe above the right-hand cards. Selecting a tab never changes on mere entry.
                Row(Modifier.fillMaxWidth().focusRestorer(tabRequesters.of(selected.name)).focusGroup().testTag(TAG_CHANNEL_TABS),
                    horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm)) {
                    status.tabs.forEach { tab ->
                        TvChip(
                            stringResource(tab.labelResource), tab == selected, { onSelectTab(tab) },
                            Modifier.focusRequester(tabRequesters.of(tab.name))
                                .onFocusChanged { if (it.isFocused && tab != selected) onSelectTab(tab) }
                                .testTag(channelTabTag(tab)),
                        )
                    }
                }
            }
            // The state message leaves while the tab reloads: park focus on its tab first.
            val retryTab = { tab: ChannelTab ->
                runCatching { tabRequesters.of(tab.name).requestFocus() }
                onRetryTab(tab)
            }
            tabContent(gridState, selected, cardWidth, anchor, memory, cardRequesters, stateRequester, onOpen, onLoadMore, retryTab)
        }
    }
}

private fun LazyGridScope.tabContent(
    grid: TvChannelGrid,
    tab: ChannelTab,
    cardWidth: androidx.compose.ui.unit.Dp,
    anchor: Int,
    memory: TvFocusMemory,
    requesters: TvFocusRequesters,
    stateRequester: FocusRequester,
    onOpen: (TvChannelCard) -> Unit,
    onLoadMore: (ChannelTab) -> Unit,
    onRetryTab: (ChannelTab) -> Unit,
) {
    when (grid) {
        TvChannelGrid.Loading -> item(key = "skeleton-${tab.name}", span = { GridItemSpan(maxLineSpan) }, contentType = "skeleton") {
            TvSkeletonRow(Modifier.testTag(TAG_CHANNEL_TAB_SKELETON), cardWidth = cardWidth,
                aspectRatio = if (tab == ChannelTab.Shorts) 9f / 16f else 16f / 9f, showHeader = false)
        }
        TvChannelGrid.Failed, TvChannelGrid.Empty -> item(key = "state-${tab.name}", span = { GridItemSpan(maxLineSpan) },
            contentType = "state") {
            Box(Modifier.fillMaxWidth().padding(vertical = TvSpacing.xl), contentAlignment = Alignment.Center) {
                TvStateMessage(
                    title = stringResource(if (grid == TvChannelGrid.Failed) R.string.tv_row_failed else R.string.tv_row_empty),
                    actionLabel = stringResource(R.string.retry),
                    onAction = { onRetryTab(tab) },
                    actionModifier = Modifier.focusRequester(stateRequester).testTag(TAG_CHANNEL_TAB_RETRY),
                )
            }
        }
        is TvChannelGrid.Ready -> {
            itemsIndexed(grid.cards, key = { _, card -> card.key }, contentType = { _, card -> card.kind }) { index, card ->
                TvCard(
                    video = card.video,
                    onClick = { onOpen(card) },
                    width = cardWidth,
                    modifier = (if (index == anchor) Modifier.focusRequester(requesters.of(tab.name)) else Modifier)
                        .testTag(channelCardTag(index)),
                    onFocused = { memory.record(tab.name, 0, card.key, index) },
                )
            }
            item(key = "footer-${tab.name}", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                TvPagingFooter(grid.paging.footer, { onLoadMore(tab) }, { onLoadMore(tab) }, tag = TAG_CHANNEL_GRID)
            }
        }
    }
}

@Composable
private fun ChannelHeader(header: TvChannelHeader, onSubscribe: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = TvSpacing.OverscanVertical)) {
        if (header.banner != null) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(TvSizes.Corner))) {
                YoutubeThumb(header.banner, Modifier.fillMaxSize(), placeholderColor = TvColors.Surface)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, TvColors.Background))))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = if (header.banner != null) 96.dp else 0.dp, start = TvSpacing.lg, end = TvSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YoutubeThumb(header.avatar, Modifier.size(96.dp).clip(CircleShape), placeholderColor = TvColors.Ghost)
            Spacer(Modifier.width(TvSpacing.xl))
            Column(Modifier.weight(1f)) {
                Text(header.name, style = TvType.Display, color = TvColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(TAG_CHANNEL_NAME))
                if (header.subtitle.isNotBlank()) {
                    Text(header.subtitle, style = TvType.Body, color = TvColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(TvSpacing.xl))
            val subscribed = header.subscribed == true
            TvButton(
                text = stringResource(if (subscribed) R.string.subscribed else R.string.subscribe),
                onClick = onSubscribe,
                modifier = Modifier.testTag(TAG_CHANNEL_SUBSCRIBE),
                icon = if (subscribed) AppIcon.Check else AppIcon.Bell,
                primary = !subscribed,
                enabled = !header.subscribePending,
            )
        }
    }
}

/** Header and tab row precede the cards in the grid. */
private const val HEADER_ITEMS = 2

const val TAG_CHANNEL = "tv-channel"
const val TAG_CHANNEL_SKELETON = "tv-channel-skeleton"
const val TAG_CHANNEL_RETRY = "tv-channel-retry"
const val TAG_CHANNEL_GRID = "tv-channel-grid"
const val TAG_CHANNEL_TABS = "tv-channel-tabs"
const val TAG_CHANNEL_NAME = "tv-channel-name"
const val TAG_CHANNEL_SUBSCRIBE = "tv-channel-subscribe"
const val TAG_CHANNEL_TAB_SKELETON = "tv-channel-tab-skeleton"
const val TAG_CHANNEL_TAB_RETRY = "tv-channel-tab-retry"
fun channelTabTag(tab: ChannelTab) = "tv-channel-tab-${tab.name}"
fun channelCardTag(index: Int) = "tv-channel-card-$index"
