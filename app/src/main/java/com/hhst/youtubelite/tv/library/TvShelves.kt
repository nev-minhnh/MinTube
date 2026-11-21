package com.hhst.youtubelite.tv.library

import com.hhst.youtubelite.tv.design.tvFocusRestorer
import com.hhst.youtubelite.tv.design.tvAnchor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSkeletonRow
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.ui.design.AppIcon

/** One horizontal library row ("shelf"). */
data class TvShelf(val key: String, val title: String, val state: TvShelfState)

sealed interface TvShelfState {
    data object Loading : TvShelfState
    /** Shows a focusable Retry. */
    data object Failed : TvShelfState
    data class Empty(val message: String? = null) : TvShelfState
    /** [viewAll] adds the trailing "View all" card. */
    data class Ready(val videos: List<TvVideo>, val viewAll: Boolean = true) : TvShelfState
}

/** Focusable keys of a shelf, in display order: its cards, then "View all". */
fun TvShelf.focusKeys(): List<String> = (state as? TvShelfState.Ready)?.let { ready ->
    ready.videos.map(TvVideo::id) + if (ready.viewAll) listOf(VIEW_ALL_KEY) else emptyList()
}.orEmpty()

private class TvShelfScrolls {
    private val states = mutableMapOf<String, LazyListState>()
    fun of(key: String) = states.getOrPut(key) { LazyListState() }
}

/**
 * Vertical list of shelves under an optional [header]. State-hoisted; focus is remembered per
 * shelf and restored after Back or a refresh ([TvFocusKeeper]); entering a shelf lands on its
 * remembered card.
 */
@Composable
fun TvShelves(
    shelves: List<TvShelf>,
    onOpen: (shelfKey: String, TvVideo) -> Unit,
    onViewAll: (shelfKey: String) -> Unit,
    onRetry: (shelfKey: String) -> Unit,
    memory: TvFocusMemory,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    initialFocus: Boolean = false,
) {
    val column = rememberLazyListState()
    val requesters = remember { TvFocusRequesters() }
    val scrolls = remember { TvShelfScrolls() }
    var focused by remember { mutableStateOf(false) }
    val offset = if (header != null) 1 else 0
    val focusRows = remember(shelves) { shelves.map { TvFocusRow(it.key, it.focusKeys()) } }
    TvFocusKeeper(focusRows, memory, hasFocus = { focused }, initial = initialFocus, focus = { target ->
        val key = focusRows[target.rowIndex].key
        column.scrollToItem(target.rowIndex + offset)
        scrolls.of(key).scrollToItem(target.itemIndex)
        withFrameNanos { }
        requesters.request(key)
    })
    LazyColumn(
        state = column,
        modifier = modifier.fillMaxSize().onFocusChanged { focused = it.hasFocus }.testTag(TAG_SHELVES),
        contentPadding = PaddingValues(top = TvSpacing.OverscanVertical, bottom = TvSpacing.OverscanVertical + 48.dp),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
    ) {
        if (header != null) item(key = "header", contentType = "header") { Box(Modifier.padding(start = ShelfStart)) { header() } }
        itemsIndexed(shelves, key = { _, shelf -> shelf.key }) { index, shelf ->
            Column(Modifier.fillMaxWidth().testTag(shelfTag(shelf.key))) {
                TvRowHeader(shelf.title, Modifier.padding(start = ShelfStart))
                when (val state = shelf.state) {
                    TvShelfState.Loading -> TvSkeletonRow(Modifier.padding(start = ShelfStart), showHeader = false)
                    // Still focusable when empty: View all leads (History's page holds Pause/Resume), so a
                    // Library with nothing in it never leaves focus stranded in the rail.
                    is TvShelfState.Empty -> Row(Modifier.padding(start = ShelfStart), verticalAlignment = Alignment.CenterVertically) {
                        TvButton(stringResource(R.string.ui_view_all), { onViewAll(shelf.key) }, Modifier.testTag(shelfViewAllTag(shelf.key)))
                        Spacer(Modifier.width(TvSpacing.lg))
                        Text(state.message ?: stringResource(R.string.tv_row_empty), style = TvType.Body, color = TvColors.Secondary)
                    }
                    TvShelfState.Failed -> Row(Modifier.padding(start = ShelfStart), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tv_row_failed), style = TvType.Body, color = TvColors.Secondary)
                        Spacer(Modifier.width(TvSpacing.lg))
                        TvButton(stringResource(R.string.retry), { onRetry(shelf.key) }, Modifier.testTag(shelfRetryTag(shelf.key)),
                            icon = AppIcon.Refresh)
                    }
                    is TvShelfState.Ready -> ShelfRow(shelf, state, index, memory, requesters, scrolls.of(shelf.key), onOpen, onViewAll)
                }
            }
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: TvShelf,
    state: TvShelfState.Ready,
    rowIndex: Int,
    memory: TvFocusMemory,
    requesters: TvFocusRequesters,
    scroll: LazyListState,
    onOpen: (String, TvVideo) -> Unit,
    onViewAll: (String) -> Unit,
) {
    val keys = remember(state) { shelf.focusKeys() }
    val anchor = memory.indexIn(shelf.key, keys)
    val requester = requesters.of(shelf.key)
    val short = state.videos.firstOrNull()?.short == true
    val width = if (short) TvSizes.ShortCardWidth else TvSizes.CardWidth
    LazyRow(
        state = scroll,
        modifier = Modifier.fillMaxWidth().tvFocusRestorer(requester).testTag(shelfListTag(shelf.key)),
        contentPadding = PaddingValues(start = ShelfStart, end = TvSpacing.OverscanHorizontal, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap),
    ) {
        itemsIndexed(state.videos, key = { _, video -> video.id }) { index, video ->
            TvCard(
                video = video,
                onClick = { onOpen(shelf.key, video) },
                width = width,
                modifier = (if (index == anchor) Modifier.tvAnchor(requester) else Modifier).testTag(shelfCardTag(shelf.key, index)),
                onFocused = { memory.record(shelf.key, rowIndex, video.id, index) },
            )
        }
        if (state.viewAll) item(key = VIEW_ALL_KEY, contentType = VIEW_ALL_KEY) {
            val index = state.videos.size
            TvViewAllCard(
                onClick = { onViewAll(shelf.key) },
                width = width,
                aspect = if (short) 9f / 16f else 16f / 9f,
                modifier = (if (index == anchor) Modifier.tvAnchor(requester) else Modifier).testTag(shelfViewAllTag(shelf.key)),
                onFocused = { memory.record(shelf.key, rowIndex, VIEW_ALL_KEY, index) },
            )
        }
    }
}

private val ShelfStart = TvSpacing.ContentStart

const val TAG_SHELVES = "tv-shelves"
fun shelfTag(key: String) = "tv-shelf-$key"
fun shelfListTag(key: String) = "tv-shelf-list-$key"
fun shelfRetryTag(key: String) = "tv-shelf-retry-$key"
fun shelfCardTag(key: String, index: Int) = "tv-shelf-card-$key-$index"
fun shelfViewAllTag(key: String) = "tv-shelf-view-all-$key"
