package com.hhst.youtubelite.tv.search

import androidx.compose.runtime.withFrameNanos
import com.hhst.youtubelite.tv.design.tvFocusRestorer
import com.hhst.youtubelite.tv.design.tvAnchor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.search.domain.SearchResultType
import com.hhst.youtubelite.search.domain.SearchUploadDate
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvCard
import com.hhst.youtubelite.tv.design.TvChip
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvRowHeader
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSkeletonRow
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.rememberTvFocusMemory
import com.hhst.youtubelite.tv.grid.TvChannelTile
import com.hhst.youtubelite.tv.grid.TvGridPagingEffect
import com.hhst.youtubelite.tv.grid.TvIconButton
import com.hhst.youtubelite.tv.grid.TvListRow
import com.hhst.youtubelite.tv.grid.TvPagingFooter
import com.hhst.youtubelite.tv.grid.TvRestoreFocusOnResume
import com.hhst.youtubelite.tv.grid.requestWhenReady
import com.hhst.youtubelite.tv.grid.tvGridColumns
import com.hhst.youtubelite.tv.shell.LocalTvShell
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.labelResource
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * TV Search: a search field (OK opens the system keyboard), an optional voice button, then
 * either the suggestion pane (recent searches and live suggestions) or the results pane
 * (filters row and a card grid). State-hoisted; [TvSearchController] wires the ViewModel.
 *
 * The suggestion pane shows while nothing was searched yet or while the user edits the draft;
 * Back while editing returns to the results. After a search, focus moves to the first result
 * (or the Retry of an empty/failed search) unless the user already moved away from the field.
 */
@Composable
fun TvSearchScreen(
    state: TvSearchUiState,
    onDraftChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onVoice: () -> Unit,
    onSelectType: (SearchResultType) -> Unit,
    onSelectDate: (SearchUploadDate) -> Unit,
    onOpen: (TvSearchItem) -> Unit,
    onOpenChannel: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onVisibleVideos: (List<String>) -> Unit = {},
    focusMemory: TvFocusMemory = rememberTvFocusMemory(),
) {
    var editing by rememberSaveable { mutableStateOf(state.query.isBlank()) }
    var lastQuery by rememberSaveable { mutableStateOf(state.query) }
    var awaitingResults by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    var screenFocused by remember { mutableStateOf(false) }
    val fieldRequester = remember { FocusRequester() }
    val resultRequester = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shell = LocalTvShell.current
    val suggesting = editing || state.query.isBlank()

    fun submit(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        keyboard?.hide()
        // Focus may sit in the suggestion list that is about to leave: park it on the field first.
        if (!fieldFocused) runCatching { fieldRequester.requestFocus() }
        editing = false
        awaitingResults = true
        onSubmit(query)
    }

    // A query set from outside (voice) behaves like a submit.
    LaunchedEffect(state.query) {
        if (state.query != lastQuery) {
            lastQuery = state.query
            if (state.query.isNotBlank()) {
                editing = false
                awaitingResults = true
                focusMemory.forget(RESULTS)
            }
        }
    }
    LaunchedEffect(state.filters) { focusMemory.forget(RESULTS) }
    LaunchedEffect(awaitingResults, state.results.status, suggesting) {
        if (!awaitingResults || suggesting) return@LaunchedEffect
        val status = state.results.status
        if (status == TvSearchStatus.Loading || status == TvSearchStatus.Idle) return@LaunchedEffect
        awaitingResults = false
        // The user may have moved on (filters, rail) while results loaded: only move focus from the field.
        if (!fieldFocused) return@LaunchedEffect
        when (status) {
            TvSearchStatus.Ready -> if (state.results.items.isNotEmpty()) resultRequester.requestWhenReady()
            TvSearchStatus.Failed, is TvSearchStatus.Empty -> retryRequester.requestWhenReady()
            else -> Unit
        }
    }
    BackHandler(enabled = editing && state.query.isNotBlank()) {
        editing = false
        onDraftChange(state.query)
        runCatching { fieldRequester.requestFocus() }
    }
    TvRestoreFocusOnResume(state.results.status, hasFocus = { screenFocused || shell?.railFocusedByUser == true }) {
        if (!suggesting && state.results.items.isNotEmpty() && resultRequester.requestWhenReady(attempts = 4)) return@TvRestoreFocusOnResume
        fieldRequester.requestWhenReady(attempts = 4)
    }

    Column(
        modifier
            .fillMaxSize()
            .onFocusChanged { screenFocused = it.hasFocus }
            .focusRestorer(fieldRequester)
            .focusGroup()
            .padding(start = TvSpacing.xl, top = TvSpacing.OverscanVertical, end = TvSpacing.OverscanHorizontal)
            .testTag(TAG_SEARCH),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TvSpacing.lg)) {
            TvSearchField(
                value = state.draft,
                onValueChange = { text ->
                    editing = true
                    onDraftChange(text)
                },
                onSearch = { submit(state.draft) },
                onOpenKeyboard = {
                    editing = true
                    keyboard?.show()
                },
                modifier = Modifier.weight(1f).focusRequester(fieldRequester).onFocusChanged { fieldFocused = it.isFocused },
            )
            if (state.voiceAvailable) {
                TvButton(stringResource(R.string.tv_search_voice), onVoice, Modifier.testTag(TAG_VOICE), icon = AppIcon.Voice)
            }
        }
        Spacer(Modifier.height(TvSpacing.xl))
        if (suggesting) {
            SuggestionPane(state, ::submit, onRemoveRecent, fieldRequester)
        } else {
            // The message leaves while the search reloads: park focus on the field, then follow the results.
            val retry = {
                runCatching { fieldRequester.requestFocus() }
                awaitingResults = true
                onRetry()
            }
            ResultsPane(state, onSelectType, onSelectDate, onOpen, onOpenChannel, onLoadMore, onRetryMore, retry,
                onVisibleVideos, focusMemory, resultRequester, retryRequester)
        }
    }
}

/**
 * The search field. It does not open the keyboard on focus, so the D-pad can pass over it; OK
 * opens the keyboard. Up/Down always move focus, and Left/Right leave the field at either end
 * of the text, whatever input device sent them.
 */
@Composable
private fun TvSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val hint = stringResource(R.string.tv_search_hint)
    BasicTextField(
        value = field,
        onValueChange = { next ->
            field = next
            if (next.text != value) onValueChange(next.text)
        },
        singleLine = true,
        textStyle = TvType.Body.copy(color = TvColors.Ink),
        cursorBrush = SolidColor(TvColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }, onDone = { onSearch() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyUp) onOpenKeyboard()
                        true
                    }
                    Key.DirectionDown -> { if (down) focusManager.moveFocus(FocusDirection.Down); true }
                    Key.DirectionUp -> { if (down) focusManager.moveFocus(FocusDirection.Up); true }
                    Key.DirectionRight -> if (field.selection.end >= field.text.length) {
                        if (down) focusManager.moveFocus(FocusDirection.Right); true
                    } else false
                    Key.DirectionLeft -> if (field.selection.start <= 0) {
                        if (down) focusManager.moveFocus(FocusDirection.Left); true
                    } else false
                    else -> false
                }
            }
            .testTag(TAG_FIELD),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(if (focused) TvColors.Surface else TvColors.Panel, CircleShape)
                    .border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) TvColors.Accent else TvColors.Ghost), CircleShape)
                    .padding(horizontal = TvSpacing.xl, vertical = TvSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
            ) {
                com.hhst.youtubelite.ui.design.AppIcon(AppIcon.Search, Modifier.size(24.dp),
                    color = if (focused) TvColors.Accent else TvColors.Secondary)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, style = TvType.Body, color = TvColors.Secondary, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun SuggestionPane(
    state: TvSearchUiState,
    onSubmit: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    fieldRequester: FocusRequester,
) {
    if (state.recent.isEmpty() && state.suggestions.isEmpty()) {
        if (state.draft.isBlank()) Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.Center) {
            TvStateMessage(
                title = stringResource(R.string.tv_search_start_title),
                message = stringResource(R.string.tv_search_start_message),
                icon = AppIcon.Search,
                modifier = Modifier.testTag(TAG_START),
            )
        }
        return
    }
    val rowRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    var refocusIndex by remember { mutableIntStateOf(-1) }
    var focusedRecent by remember { mutableIntStateOf(-1) }
    val list = rememberLazyListState()
    // A removed row takes focus with it: hand focus to the row now in its place, else the field.
    // The window re-enters the screen after the focused button leaves (landing on the field), which
    // can happen after this request: check that the row kept focus and ask again.
    LaunchedEffect(state.recent) {
        val index = refocusIndex
        if (index < 0) return@LaunchedEffect
        refocusIndex = -1
        val target = index.coerceAtMost(state.recent.lastIndex)
        if (target < 0) {
            fieldRequester.requestWhenReady(attempts = 6)
            return@LaunchedEffect
        }
        repeat(REFOCUS_ATTEMPTS) {
            rowRequesters.getOrPut(target) { FocusRequester() }.requestWhenReady(attempts = 6)
            withFrameNanos { }
            withFrameNanos { }
            if (focusedRecent == target) return@LaunchedEffect
        }
    }
    LazyColumn(
        state = list,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag(TAG_SUGGESTIONS),
        contentPadding = PaddingValues(bottom = TvSpacing.OverscanVertical + 48.dp),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.xs),
    ) {
        if (state.recent.isNotEmpty()) {
            item(key = "recent-header", contentType = "header") {
                TvRowHeader(stringResource(R.string.tv_search_recent), Modifier.padding(top = TvSpacing.sm),
                    detail = stringResource(R.string.tv_search_remove_hint))
            }
            itemsIndexed(state.recent, key = { _, entry -> "recent:$entry" }, contentType = { _, _ -> "recent" }) { index, entry ->
                val remove = {
                    refocusIndex = index
                    onRemoveRecent(entry)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm)) {
                    TvListRow(entry, { onSubmit(entry) },
                        Modifier.weight(1f).focusRequester(rowRequesters.getOrPut(index) { FocusRequester() })
                            .onFocusChanged { focus ->
                                if (focus.isFocused) focusedRecent = index else if (focusedRecent == index) focusedRecent = -1
                            }
                            .testTag(recentTag(index)),
                        icon = AppIcon.History, onLongClick = remove)
                    TvIconButton(AppIcon.Close, stringResource(R.string.tv_search_remove, entry), remove,
                        Modifier.testTag(recentRemoveTag(index)))
                }
            }
        }
        if (state.suggestions.isNotEmpty()) {
            item(key = "suggestion-header", contentType = "header") {
                TvRowHeader(stringResource(R.string.tv_search_suggestions), Modifier.padding(top = TvSpacing.lg))
            }
            itemsIndexed(state.suggestions, key = { _, entry -> "suggestion:$entry" }, contentType = { _, _ -> "suggestion" }) { index, entry ->
                // Rows end where the remove buttons of the recent rows end, so Up/Down stay in one column.
                Row(Modifier.padding(end = TvSizes.ButtonHeight + TvSpacing.sm)) {
                    TvListRow(entry, { onSubmit(entry) }, Modifier.testTag(suggestionTag(index)), icon = AppIcon.Search)
                }
            }
        }
    }
}

@Composable
private fun ResultsPane(
    state: TvSearchUiState,
    onSelectType: (SearchResultType) -> Unit,
    onSelectDate: (SearchUploadDate) -> Unit,
    onOpen: (TvSearchItem) -> Unit,
    onOpenChannel: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onRetry: () -> Unit,
    onVisibleVideos: (List<String>) -> Unit,
    memory: TvFocusMemory,
    resultRequester: FocusRequester,
    retryRequester: FocusRequester,
) {
    Column(Modifier.fillMaxSize()) {
        FiltersRow(state, onSelectType, onSelectDate)
        Spacer(Modifier.height(TvSpacing.lg))
        when (val status = state.results.status) {
            TvSearchStatus.Idle, TvSearchStatus.Loading -> Column(Modifier.testTag(TAG_SKELETON),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap)) {
                val short = state.filters.type == SearchResultType.Shorts
                repeat(2) {
                    TvSkeletonRow(cardWidth = if (short) TvSizes.ShortCardWidth else TvSizes.CardWidth,
                        aspectRatio = if (short) 9f / 16f else 16f / 9f, count = if (short) 6 else 4, showHeader = false)
                }
            }
            TvSearchStatus.Failed -> Centered {
                TvStateMessage(
                    title = stringResource(R.string.tv_search_failed_title),
                    message = stringResource(R.string.tv_home_failed_message),
                    icon = AppIcon.WifiOff,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    actionModifier = Modifier.focusRequester(retryRequester).testTag(TAG_RETRY),
                )
            }
            is TvSearchStatus.Empty -> Centered {
                TvStateMessage(
                    title = stringResource(R.string.tv_search_empty_title),
                    message = stringResource(emptyMessage(status.type), status.query),
                    icon = AppIcon.Search,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    actionModifier = Modifier.focusRequester(retryRequester).testTag(TAG_RETRY),
                )
            }
            TvSearchStatus.Ready -> ResultsGrid(state, onOpen, onOpenChannel, onLoadMore, onRetryMore, onVisibleVideos, memory,
                resultRequester)
        }
    }
}

private fun emptyMessage(type: SearchResultType): Int = when (type) {
    SearchResultType.All -> R.string.ui_search_no_videos
    SearchResultType.Shorts -> R.string.ui_search_no_shorts
    SearchResultType.Channels -> R.string.ui_search_no_channels
    SearchResultType.Playlists -> R.string.ui_search_no_playlists
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.Center) { content() }
}

/** Type chips, then upload-date chips, in one horizontally scrolling row. */
@Composable
private fun FiltersRow(state: TvSearchUiState, onSelectType: (SearchResultType) -> Unit, onSelectDate: (SearchUploadDate) -> Unit) {
    val selectedType = state.types.indexOf(state.filters.type).coerceAtLeast(0)
    val anchor = remember { FocusRequester() }
    LazyRow(
        Modifier.fillMaxWidth().tvFocusRestorer(anchor).testTag(TAG_FILTERS),
        contentPadding = PaddingValues(vertical = TvSpacing.xs, horizontal = TvSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(state.types, key = { _, type -> "type:${type.name}" }) { index, type ->
            TvChip(stringResource(type.labelResource), type == state.filters.type, { onSelectType(type) },
                (if (index == selectedType) Modifier.tvAnchor(anchor) else Modifier).testTag(typeTag(type)))
        }
        item(key = "divider") { Box(Modifier.padding(horizontal = TvSpacing.sm).width(1.dp).height(24.dp).background(TvColors.Ghost)) }
        itemsIndexed(SearchUploadDate.entries, key = { _, date -> "date:${date.name}" }) { _, date ->
            TvChip(stringResource(date.labelResource), date == state.filters.uploaded, { onSelectDate(date) },
                Modifier.testTag(dateTag(date)))
        }
    }
}

@Composable
private fun ResultsGrid(
    state: TvSearchUiState,
    onOpen: (TvSearchItem) -> Unit,
    onOpenChannel: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onVisibleVideos: (List<String>) -> Unit,
    memory: TvFocusMemory,
    requester: FocusRequester,
) {
    val items = state.results.items
    val paging = state.results.paging
    val short = state.filters.type == SearchResultType.Shorts
    val cardWidth = if (short) TvSizes.ShortCardWidth else TvSizes.CardWidth
    val grid = rememberLazyGridState()
    val keys = remember(items) { items.map(TvSearchItem::key) }
    val anchor = memory.indexIn(RESULTS, keys)
    TvGridPagingEffect(grid, paging, items.size, onLoadMore)
    val visible by rememberUpdatedState(onVisibleVideos)
    val currentItems by rememberUpdatedState(items)
    LaunchedEffect(grid) {
        snapshotFlow {
            val info = grid.layoutInfo.visibleItemsInfo
            TvSearchModel.visibleVideoIds(currentItems, info.firstOrNull()?.index ?: 0, info.lastOrNull()?.index ?: -1)
        }.distinctUntilChanged().collect { ids -> if (ids.isNotEmpty()) visible(ids) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = tvGridColumns(maxWidth - TvSpacing.lg, cardWidth, TvSpacing.CardGap)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            modifier = Modifier.fillMaxSize().tvFocusRestorer(requester).testTag(TAG_GRID),
            contentPadding = PaddingValues(start = TvSpacing.sm, end = TvSpacing.sm, top = 10.dp, bottom = TvSpacing.OverscanVertical + 48.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
        ) {
            itemsIndexed(items, key = { _, item -> item.key }, contentType = { _, item -> item::class.simpleName }) { index, item ->
                val focus = (if (index == anchor) Modifier.tvAnchor(requester) else Modifier).testTag(resultTag(index))
                val record = { memory.record(RESULTS, 0, item.key, index) }
                when (item) {
                    is TvSearchItem.Video -> TvCard(
                        video = item.video, onClick = { onOpen(item) }, modifier = focus, width = cardWidth,
                        onLongClick = if (item.channelId.isNotBlank()) ({ onOpenChannel(item.channelId) }) else null,
                        onFocused = record,
                    )
                    is TvSearchItem.Playlist -> TvCard(video = item.card, onClick = { onOpen(item) }, modifier = focus,
                        width = cardWidth, onFocused = record)
                    is TvSearchItem.Channel -> TvChannelTile(item.name, item.detail, item.avatar, { onOpen(item) }, focus,
                        width = cardWidth, onFocused = record)
                }
            }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                TvPagingFooter(paging.footer, onRetryMore, onRetryMore, tag = TAG_GRID)
            }
        }
    }
}

private const val RESULTS = "results"

const val TAG_SEARCH = "tv-search"
const val TAG_FIELD = "tv-search-field"
const val TAG_VOICE = "tv-search-voice"
const val TAG_START = "tv-search-start"
const val TAG_SUGGESTIONS = "tv-search-suggestions"
const val TAG_FILTERS = "tv-search-filters"
const val TAG_GRID = "tv-search-grid"
const val TAG_SKELETON = "tv-search-skeleton"
const val TAG_RETRY = "tv-search-retry"
fun recentTag(index: Int) = "tv-search-recent-$index"
fun recentRemoveTag(index: Int) = "tv-search-recent-remove-$index"
fun suggestionTag(index: Int) = "tv-search-suggestion-$index"
fun resultTag(index: Int) = "tv-search-result-$index"
fun typeTag(type: SearchResultType) = "tv-search-type-${type.name}"
fun dateTag(date: SearchUploadDate) = "tv-search-date-${date.name}"

private const val REFOCUS_ATTEMPTS = 4
