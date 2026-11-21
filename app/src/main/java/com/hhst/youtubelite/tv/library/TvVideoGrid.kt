package com.hhst.youtubelite.tv.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvCard
import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvFocusRow
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.ui.design.AppIcon

/** What ends a paged grid. */
enum class TvGridFooter { None, Loading, Retry, LoadMore }

/**
 * Full-width grid of cards for the library pages. [header] spans the first line (title, actions).
 * Initial focus goes to the first card (or [fallback] when there is none); a removed or refreshed
 * card hands focus to its neighbour at the same index. Paging asks [onLoadMore] two lines ahead
 * while [canPage].
 */
@Composable
fun TvVideoGrid(
    items: List<TvVideo>,
    onOpen: (TvVideo) -> Unit,
    memory: TvFocusMemory,
    modifier: Modifier = Modifier,
    onLongClick: ((TvVideo) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: TvGridFooter = TvGridFooter.None,
    canPage: Boolean = false,
    onLoadMore: () -> Unit = {},
    onRetryMore: () -> Unit = {},
    initialFocus: Boolean = true,
    fallback: () -> Boolean = { false },
    state: LazyGridState = rememberLazyGridState(),
) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val keys = remember(items) { items.map(TvVideo::id) }
    val offset = if (header != null) 1 else 0
    val anchor = memory.indexIn(GRID_KEY, keys)
    val rows = remember(keys) { listOf(TvFocusRow(GRID_KEY, keys)) }
    TvFocusKeeper(rows, memory, hasFocus = { focused }, initial = initialFocus, fallback = fallback, focus = { target ->
        state.scrollToItem(target.itemIndex + offset)
        withFrameNanos { }
        runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
    })
    val nearEnd by remember(state) { derivedStateOf {
        val info = state.layoutInfo
        info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - PAGE_AHEAD
    } }
    LaunchedEffect(nearEnd, canPage, items.size) { if (nearEnd && canPage) onLoadMore() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val metrics = TvGridMetrics.of(maxWidth - GridStart - TvSpacing.OverscanHorizontal)
        LazyVerticalGrid(
            columns = GridCells.Fixed(metrics.columns),
            state = state,
            modifier = Modifier.fillMaxSize().focusRestorer(requester).onFocusChanged { focused = it.hasFocus }.testTag(TAG_GRID),
            contentPadding = PaddingValues(start = GridStart, end = TvSpacing.OverscanHorizontal,
                top = TvSpacing.OverscanVertical, bottom = TvSpacing.OverscanVertical + 48.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.xl),
        ) {
            if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { header() }
            itemsIndexed(items, key = { _, video -> video.id }) { index, video ->
                Box(Modifier.padding(top = 6.dp)) {
                    TvCard(
                        video = video.copy(short = false),
                        onClick = { onOpen(video) },
                        width = metrics.cardWidth,
                        onLongClick = onLongClick?.let { hold -> { hold(video) } },
                        modifier = (if (index == anchor) Modifier.focusRequester(requester) else Modifier).testTag(gridCardTag(index)),
                        onFocused = { memory.record(GRID_KEY, 0, video.id, index) },
                    )
                }
            }
            if (footer != TvGridFooter.None) item(key = "footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                Box(Modifier.fillMaxWidth().padding(vertical = TvSpacing.lg), contentAlignment = Alignment.Center) {
                    when (footer) {
                        TvGridFooter.Loading -> androidx.tv.material3.Text(stringResource(R.string.tv_loading),
                            style = com.hhst.youtubelite.tv.design.TvType.Body, color = com.hhst.youtubelite.tv.design.TvColors.Secondary)
                        TvGridFooter.Retry, TvGridFooter.LoadMore -> TvButton(
                            stringResource(if (footer == TvGridFooter.Retry) R.string.retry else R.string.tv_load_more),
                            onRetryMore, Modifier.testTag(TAG_GRID_MORE), icon = AppIcon.Refresh)
                        TvGridFooter.None -> Unit
                    }
                }
            }
        }
    }
}

/** Left inset of grid content (the rail or the screen edge is already outside it). */
val GridStart = TvSpacing.OverscanHorizontal
private const val GRID_KEY = "grid"
private const val PAGE_AHEAD = 8

const val TAG_GRID = "tv-grid"
const val TAG_GRID_MORE = "tv-grid-more"
fun gridCardTag(index: Int) = "tv-grid-card-$index"
