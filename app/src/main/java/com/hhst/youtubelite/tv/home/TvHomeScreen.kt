package com.hhst.youtubelite.tv.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvCard
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocus
import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvFocusRequesters
import com.hhst.youtubelite.tv.design.TvFocusRow
import com.hhst.youtubelite.tv.design.TvRowHeader
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSkeletonRow
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.design.rememberTvFocusMemory
import com.hhst.youtubelite.tv.shell.LocalTvShell
import com.hhst.youtubelite.ui.design.AppIcon
import kotlinx.coroutines.delay

/** Start inset of row content inside the shell's content area. */
private val RowStart = TvSpacing.xl

/**
 * TV Home: one row per Home section. State-hoisted; [TvHomeController] wires the ViewModels.
 * Focus is remembered per row ([TvFocusMemory]) and restored when the screen resumes without
 * focus (Back from Watch after a refresh replaced the rows) or a row is re-entered.
 */
@Composable
fun TvHomeScreen(
    state: TvHomeUiState,
    onOpen: (TvVideo) -> Unit,
    onLoadMore: (rowKey: String) -> Unit,
    onRetryRow: (rowKey: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onRowShown: (TvHomeRow) -> Unit = {},
    onFocusDwell: (TvVideo) -> Unit = {},
    focusMemory: TvFocusMemory = rememberTvFocusMemory(),
) {
    Box(modifier.fillMaxSize().testTag(TAG_HOME)) {
        when (val status = state.status) {
            TvHomeStatus.Loading -> Column(
                Modifier.fillMaxSize().padding(start = RowStart, top = TvSpacing.OverscanVertical).testTag(TAG_SKELETON),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
            ) {
                TvHomeBrand()
                repeat(3) { TvSkeletonRow() }
            }
            TvHomeStatus.Failed -> Centered {
                TvStateMessage(
                    title = stringResource(R.string.tv_home_failed_title),
                    message = stringResource(R.string.tv_home_failed_message),
                    icon = AppIcon.WifiOff,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_RETRY),
                )
            }
            is TvHomeStatus.Empty -> Centered {
                TvStateMessage(
                    title = status.title.ifBlank { stringResource(R.string.home_empty_title) },
                    message = status.message.ifBlank { stringResource(R.string.home_empty_message) },
                    icon = AppIcon.HomeOutline,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_RETRY),
                )
            }
            TvHomeStatus.Ready -> TvHomeRows(state.rows, onOpen, onLoadMore, onRetryRow, onRowShown, onFocusDwell, focusMemory)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(end = TvSpacing.OverscanHorizontal), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun TvHomeBrand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_mintube_mark_dark), contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(TvSpacing.sm))
        Text(stringResource(R.string.app_name), style = TvType.Title, color = TvColors.Ink)
    }
}

/** Per-row horizontal scroll state, kept while rows scroll out of the column. */
private class TvRowScrollStates {
    private val states = mutableMapOf<String, LazyListState>()
    fun of(key: String) = states.getOrPut(key) { LazyListState() }
}

@Composable
private fun TvHomeRows(
    rows: List<TvHomeRow>,
    onOpen: (TvVideo) -> Unit,
    onLoadMore: (String) -> Unit,
    onRetryRow: (String) -> Unit,
    onRowShown: (TvHomeRow) -> Unit,
    onFocusDwell: (TvVideo) -> Unit,
    memory: TvFocusMemory,
) {
    val column = rememberLazyListState()
    val requesters = remember { TvFocusRequesters() }
    val scrolls = remember { TvRowScrollStates() }
    var homeFocused by remember { mutableStateOf(false) }
    val shell = LocalTvShell.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateFlow.collectAsState()
    val focusRows = remember(rows) {
        rows.map { row -> TvFocusRow(row.key, (row.state as? TvRowState.Ready)?.videos?.map(TvVideo::id).orEmpty()) }
    }
    // Focus must never be lost: when this screen is resumed and visible but nothing in it (or the
    // rail) holds focus while it had focus before, put focus back on the remembered card.
    LaunchedEffect(focusRows, resumed.isAtLeast(Lifecycle.State.RESUMED)) {
        if (!resumed.isAtLeast(Lifecycle.State.RESUMED) || memory.lastRowKey == null) return@LaunchedEffect
        withFrameNanos { }
        delay(RESTORE_SETTLE_MS)
        if (homeFocused || shell?.railFocused == true) return@LaunchedEffect
        val target = memory.restoreTarget(focusRows) ?: return@LaunchedEffect
        val key = focusRows[target.rowIndex].key
        column.scrollToItem(target.rowIndex + ROW_OFFSET)
        scrolls.of(key).scrollToItem(target.itemIndex)
        withFrameNanos { }
        requesters.request(key)
    }
    LazyColumn(
        state = column,
        modifier = Modifier.fillMaxSize().onFocusChanged { homeFocused = it.hasFocus }.testTag(TAG_ROWS),
        contentPadding = PaddingValues(top = TvSpacing.OverscanVertical, bottom = TvSpacing.OverscanVertical + 48.dp),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
    ) {
        item(key = "brand", contentType = "brand") { Box(Modifier.padding(start = RowStart)) { TvHomeBrand() } }
        itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, row -> row.kind }) { index, row ->
            LaunchedEffect(row.key) { onRowShown(row) }
            TvHomeRowItem(row, index, memory, requesters, scrolls.of(row.key), onOpen, onLoadMore, onRetryRow, onFocusDwell)
        }
    }
}

@Composable
private fun TvHomeRowItem(
    row: TvHomeRow,
    rowIndex: Int,
    memory: TvFocusMemory,
    requesters: TvFocusRequesters,
    scroll: LazyListState,
    onOpen: (TvVideo) -> Unit,
    onLoadMore: (String) -> Unit,
    onRetryRow: (String) -> Unit,
    onFocusDwell: (TvVideo) -> Unit,
) {
    val cardWidth = if (row.kind == TvRowKind.Shorts) TvSizes.ShortCardWidth else TvSizes.CardWidth
    val aspect = if (row.kind == TvRowKind.Shorts) 9f / 16f else 16f / 9f
    Column(Modifier.fillMaxWidth().testTag(rowTag(row.key))) {
        TvRowHeader(row.title.text(), Modifier.padding(start = RowStart))
        when (val state = row.state) {
            TvRowState.Loading -> TvSkeletonRow(Modifier.padding(start = RowStart), cardWidth, aspect, showHeader = false)
            TvRowState.Empty -> Text(stringResource(R.string.tv_row_empty), style = TvType.Body, color = TvColors.Secondary,
                modifier = Modifier.padding(start = RowStart))
            TvRowState.Failed -> Row(Modifier.padding(start = RowStart), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tv_row_failed), style = TvType.Body, color = TvColors.Secondary)
                Spacer(Modifier.width(TvSpacing.lg))
                TvButton(stringResource(R.string.retry), { onRetryRow(row.key) }, Modifier.testTag(rowRetryTag(row.key)),
                    icon = AppIcon.Refresh)
            }
            is TvRowState.Ready -> ReadyRow(row, state, rowIndex, cardWidth, aspect, memory, requesters, scroll,
                onOpen, onLoadMore, onRetryRow, onFocusDwell)
        }
    }
}

@Composable
private fun ReadyRow(
    row: TvHomeRow,
    state: TvRowState.Ready,
    rowIndex: Int,
    cardWidth: Dp,
    aspect: Float,
    memory: TvFocusMemory,
    requesters: TvFocusRequesters,
    scroll: LazyListState,
    onOpen: (TvVideo) -> Unit,
    onLoadMore: (String) -> Unit,
    onRetryRow: (String) -> Unit,
    onFocusDwell: (TvVideo) -> Unit,
) {
    val keys = remember(state.videos) { state.videos.map(TvVideo::id) }
    val anchor = memory.indexIn(row.key, keys)
    val requester = requesters.of(row.key)
    val nearEnd by remember(scroll) { derivedStateOf {
        val info = scroll.layoutInfo
        info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - PAGE_AHEAD
    } }
    val canPage = state.hasMore && state.append == AppendState.Idle && !state.pagingPaused
    LaunchedEffect(nearEnd, canPage, state.videos.size) {
        if (nearEnd && canPage) onLoadMore(row.key)
    }
    var dwell by remember { mutableStateOf<TvVideo?>(null) }
    val dwellAction by rememberUpdatedState(onFocusDwell)
    LaunchedEffect(dwell) {
        val video = dwell ?: return@LaunchedEffect
        delay(TvFocus.DwellMs)
        dwellAction(video)
    }
    LazyRow(
        state = scroll,
        // Entering the row (Up/Down, or Right from the rail) lands on the remembered card.
        modifier = Modifier.fillMaxWidth().focusRestorer(requester).testTag(rowListTag(row.key)),
        contentPadding = PaddingValues(start = RowStart, end = TvSpacing.OverscanHorizontal, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap),
    ) {
        itemsIndexed(state.videos, key = { _, video -> video.id }) { index, video ->
            TvCard(
                video = video,
                onClick = { onOpen(video) },
                width = cardWidth,
                modifier = (if (index == anchor) Modifier.focusRequester(requester) else Modifier)
                    .testTag(cardTag(row.key, index)),
                onFocused = {
                    memory.record(row.key, rowIndex, video.id, index)
                    dwell = video
                },
            )
        }
        when {
            state.append == AppendState.Loading -> item(key = "append", contentType = "append") {
                Box(Modifier.width(cardWidth).aspectRatio(aspect))
            }
            state.append == AppendState.Failed || state.hasMore && state.pagingPaused -> item(key = "more", contentType = "more") {
                Box(Modifier.width(cardWidth).aspectRatio(aspect), contentAlignment = Alignment.Center) {
                    TvButton(stringResource(if (state.append == AppendState.Failed) R.string.retry else R.string.tv_load_more),
                        { onRetryRow(row.key) }, Modifier.testTag(rowMoreTag(row.key)), icon = AppIcon.Refresh)
                }
            }
        }
    }
}

@Composable
private fun TvRowTitle.text(): String = when (this) {
    is TvRowTitle.Text -> text
    is TvRowTitle.Resource -> stringResource(id)
}

/** Rows sit after the brand item in the column. */
private const val ROW_OFFSET = 1
private const val PAGE_AHEAD = 4
private const val RESTORE_SETTLE_MS = 150L

const val TAG_HOME = "tv-home"
const val TAG_ROWS = "tv-home-rows"
const val TAG_SKELETON = "tv-home-skeleton"
const val TAG_RETRY = "tv-home-retry"
fun rowTag(key: String) = "tv-home-row-$key"
fun rowListTag(key: String) = "tv-home-row-list-$key"
fun rowRetryTag(key: String) = "tv-home-row-retry-$key"
fun rowMoreTag(key: String) = "tv-home-row-more-$key"
fun cardTag(rowKey: String, index: Int) = "tv-card-$rowKey-$index"
