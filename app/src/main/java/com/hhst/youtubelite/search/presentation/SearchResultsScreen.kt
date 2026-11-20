package com.hhst.youtubelite.search.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.home.presentation.LoadingSpinner
import com.hhst.youtubelite.playlist.presentation.PlaylistSearchList
import com.hhst.youtubelite.playlist.presentation.PlaylistShareSheet
import com.hhst.youtubelite.search.domain.SearchAction
import com.hhst.youtubelite.search.domain.SearchContent
import com.hhst.youtubelite.search.domain.SearchDestination
import com.hhst.youtubelite.search.domain.SearchFilters
import com.hhst.youtubelite.search.domain.SearchOverlay
import com.hhst.youtubelite.search.domain.SearchResultType
import com.hhst.youtubelite.search.domain.SearchResultsLayout
import com.hhst.youtubelite.search.domain.SearchUiState
import com.hhst.youtubelite.search.domain.SearchUploadDate
import com.hhst.youtubelite.search.domain.SearchVideoMenuAction
import com.hhst.youtubelite.ui.components.FilterChip
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.MinNavigationBar
import com.hhst.youtubelite.ui.components.PagingFooter
import com.hhst.youtubelite.ui.components.SearchRows
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.AppTheme
import com.hhst.youtubelite.ui.design.CollapsingAppBar
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.LocalHideShorts
import com.hhst.youtubelite.ui.design.LocalMotionScale
import com.hhst.youtubelite.ui.design.MenuBottomSheet
import com.hhst.youtubelite.ui.design.MenuSheetItem
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.RefreshableContent
import com.hhst.youtubelite.ui.design.SearchPanel
import com.hhst.youtubelite.ui.design.SelectField
import com.hhst.youtubelite.ui.design.VerificationBanner
import com.hhst.youtubelite.ui.design.VerificationCard
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.rememberAppBarCollapseState
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SearchResultsScreen(
    state: SearchUiState,
    onAction: (SearchAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    searchRows: SearchRows? = null,
) {
    val appBar = rememberAppBarCollapseState(state.query, enabled = !state.searchOpen && state.overlay == SearchOverlay.None && state.content is SearchContent.Results)
    AppTheme {
        val hideShorts = LocalHideShorts.current
        LaunchedEffect(hideShorts, state.filters.type) {
            if (hideShorts && state.filters.type == SearchResultType.Shorts) onAction(SearchAction.SelectResultType(SearchResultType.All))
        }
        BoxWithConstraints(modifier.fillMaxSize().nestedScroll(appBar.connection).background(DesignTokens.Background).testTag("search-results")) {
            val width = maxWidth.value
            val height = maxHeight.value
            // The available width, not the display: split-screen gets the right layout.
            val spec = SearchListSpec.of(width, height)
            val listState = rememberLazyListState()
            val shortsState = rememberLazyGridState()
            // A new query or filter starts a new list; restoring the page keeps its scroll.
            var appliedQuery by remember { mutableStateOf(state.query) }
            var appliedFilters by remember { mutableStateOf(state.filters) }
            LaunchedEffect(state.query, state.filters) {
                if (state.query != appliedQuery || state.filters != appliedFilters) {
                    shortsState.scrollToItem(0)
                    appliedQuery = state.query
                    appliedFilters = state.filters
                    if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                        listState.scrollToItem(0)
                    }
                }
            }
            val ready = state.content as? SearchContent.Results
            val rows by rememberUpdatedState(if (state.filters.type == SearchResultType.Shorts)
                ready?.page?.shorts.orEmpty() else ready?.page?.results.orEmpty())
            // The mixed list interleaves other rows, so video rows are found by their lazy key.
            val videoIndex by rememberUpdatedState(remember(ready?.page?.results) {
                ready?.page?.results.orEmpty().withIndex().associate { (index, result) -> result.id to index }
            })
            val dispatch by rememberUpdatedState(onAction)
            LaunchedEffect(listState, shortsState, state.query, state.filters) {
                if (state.filters.type != SearchResultType.All && state.filters.type != SearchResultType.Shorts) return@LaunchedEffect
                snapshotFlow {
                    val range = if (state.filters.type == SearchResultType.Shorts) {
                        val visible = shortsState.layoutInfo.visibleItemsInfo
                        visible.firstOrNull()?.index?.let { it to (visible.lastOrNull()?.index ?: it) }
                    } else {
                        val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { videoIndex[it.key] }
                        visible.minOrNull()?.let { it to visible.max() }
                    } ?: return@snapshotFlow emptyList<String>()
                    val (first, lastVisible) = range
                    val last = lastVisible + 3
                    rows.subList(first.coerceAtMost(rows.size), (last + 1).coerceAtMost(rows.size)).map { it.id }
                }.distinctUntilChanged().collect { ids ->
                    if (ids.isNotEmpty()) dispatch(SearchAction.VisibleResults(state.query, state.filters, ids))
                }
            }
            val footer: (@Composable () -> Unit)? = if (ready != null &&
                (ready.page.nextCursor != null || ready.loadingMore || ready.loadMoreFailed)) {
                {
                    if (ready.automaticPagingPaused && !ready.loadingMore && !ready.loadMoreFailed) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("search-load-more"), contentAlignment = Alignment.Center) {
                            MinButton(stringResource(R.string.load_more), { onAction(SearchAction.LoadMore) }, kind = MinButtonKind.Tonal)
                        }
                    } else PagingFooter(ready.loadingMore, ready.loadMoreFailed) {
                        onAction(if (ready.loadMoreFailed) SearchAction.LoadMore else SearchAction.AutomaticLoadMore)
                    }
                }
            } else null
            val backgroundHidden = state.searchOpen || state.overlay is SearchOverlay.ItemMenu || state.overlay is SearchOverlay.PlaylistMenu
            Column(Modifier.fillMaxSize().then(if (backgroundHidden) Modifier.clearAndSetSemantics {} else Modifier)) {
                CollapsingAppBar(appBar) {
                    Column(Modifier.fillMaxWidth().background(DesignTokens.Background)) {
                        SearchTopBar(state.query, state.filters, state.filtersOpen, onAction, onBack)
                        TypeChips(state.filters.type, hideShorts, spec.inset, onAction)
                    }
                }

                val motion = LocalMotionScale.current > 0f
                AnimatedVisibility(visible = state.filtersOpen,
                    enter = if (motion) fadeIn(tween(160)) + expandVertically(tween(180)) else EnterTransition.None,
                    exit = if (motion) fadeOut(tween(100)) + shrinkVertically(tween(140)) else ExitTransition.None) {
                    SearchFilterBar(state.filters, onAction)
                }
                RefreshableContent(value = state, refreshing = state.content == SearchContent.Loading,
                    onRefresh = { onAction(SearchAction.Retry) }, modifier = Modifier.weight(1f).fillMaxWidth(),
                    enabled = !backgroundHidden && state.overlay == SearchOverlay.None, contentKey = Triple(state.query, state.filters, state.signedInPreview), tag = "search-refresh",
                    canPull = { if (state.filters.type == SearchResultType.Shorts) !shortsState.canScrollBackward else !listState.canScrollBackward }) { state ->
                    when (val content = state.content) {
                        SearchContent.Loading -> SearchSkeleton(spec)
                        SearchContent.Failed -> if (state.verification != null) VerificationCard(
                            state.verification,
                            onVerify = { onAction(SearchAction.OpenVerification(state.verification!!.challengeUrl)) },
                            onDismiss = { onAction(SearchAction.DismissVerification) },
                            modifier = Modifier.testTag("search-verification"),
                        ) else SearchFailed(onAction)
                        is SearchContent.Empty -> SearchNoResults(content.query, state.filters.type, onAction)
                        is SearchContent.Results -> when (state.filters.type) {
                            SearchResultType.Playlists -> PlaylistSearchList(content.page.playlists, width, height, listState,
                                { onAction(SearchAction.OpenPlaylist(it)) }, { onAction(SearchAction.OpenChannel(it)) },
                                { onAction(SearchAction.OpenPlaylistMenu(it)) }, footer)
                            SearchResultType.Channels -> ChannelResultsList(content.page.channels, listState, onAction, footer,
                                state.subscriptions, state.subscriptionsPending, spec.inset)
                            SearchResultType.Shorts -> ShortsResultsList(content.page.shorts, shortsState, onAction, footer,
                                columns = spec.window.feedColumns + 1, inset = spec.inset)
                            // The page of an "All" search already carries its channels, Shorts and playlists.
                            SearchResultType.All -> SearchAllList(content.page.results, content.page.channels,
                                if (hideShorts) emptyList() else content.page.shorts, content.page.playlists,
                                spec, listState, onAction, state.subscriptions, state.subscriptionsPending, footer)
                        }
                    }
                    state.verification?.let { challenge ->
                        VerificationBanner(challenge,
                            onVerify = { onAction(SearchAction.OpenVerification(challenge.challengeUrl)) },
                            onDismiss = { onAction(SearchAction.DismissVerification) },
                            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = spec.inset, vertical = MinSpace.sm)
                                .testTag("search-verification-banner"))
                    }
                }
                SearchNavigationBar(state.signedInPreview, onAction)
            }
            if (state.searchOpen) SearchPanel(
                query = state.draftQuery,
                suggestions = searchRows?.suggestions ?: if (state.draftQuery.isBlank()) emptyList() else listOf(
                    state.draftQuery.trim(), "${state.draftQuery.trim()} documentary",
                    "${state.draftQuery.trim()} music", "${state.draftQuery.trim()} live",
                ).distinct(),
                history = searchRows?.history.orEmpty(),
                onRemoveHistory = searchRows?.onRemove,
                onQueryChanged = { onAction(SearchAction.QueryChanged(it)) },
                onSubmit = { onAction(SearchAction.SubmitSearch(it)); searchRows?.onSubmit?.invoke(it) },
                onClose = { onAction(SearchAction.CloseSearch) },
                onVoiceSearch = { onAction(SearchAction.VoiceSearch) },
            )
            (state.overlay as? SearchOverlay.ItemMenu)?.let { menu ->
                // Production rows mirror the card's real capabilities: feedback rows appear
                // only once its own tokens were resolved for this session; fixture
                // menus keep the full typed row set.
                val page = (state.content as? SearchContent.Results)?.page
                val target = page?.results?.firstOrNull { it.id == menu.videoId }
                    ?: page?.shorts?.firstOrNull { it.id == menu.videoId }
                val menuActions = if (state.live) buildList {
                    addAll(listOf(SearchVideoMenuAction.Queue, SearchVideoMenuAction.Download,
                        SearchVideoMenuAction.WatchLater, SearchVideoMenuAction.SaveToPlaylist, SearchVideoMenuAction.Share))
                    if (state.signedInPreview && !target?.notInterestedToken.isNullOrBlank())
                        add(SearchVideoMenuAction.NotInterested)
                    if (state.signedInPreview && !target?.dontRecommendToken.isNullOrBlank())
                        add(SearchVideoMenuAction.DontRecommend)
                } else SearchVideoMenuAction.entries
                MenuBottomSheet(
                    items = menuActions.map {
                        MenuSheetItem(it.icon(), stringResource(it.labelResource), "search-menu-${it.name}")
                    },
                    onDismiss = { onAction(SearchAction.CloseItemMenu) },
                    onSelect = { index ->
                        onAction(SearchAction.MenuAction(menuActions[index]))
                    },
                    tag = "search-video-menu",
                    paneTitle = stringResource(R.string.ui_video_actions),
                    header = if (state.menuLoading || state.menuFailed) ({
                        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                            if (state.menuLoading) Box(Modifier.testTag("search-menu-loading")) { LoadingSpinner() }
                            else AppText(stringResource(R.string.retry), MinType.Label,
                                Modifier.testTag("search-menu-retry").clip(CircleShape).clickable(role = Role.Button) {
                                    onAction(SearchAction.OpenItemMenu(menu.videoId))
                                }.padding(horizontal = 20.dp, vertical = 14.dp), color = DesignTokens.AccentText)
                        }
                    }) else null,
                )
            }
            if (state.overlay is SearchOverlay.PlaylistMenu) PlaylistShareSheet(
                { onAction(SearchAction.CloseItemMenu) }, { onAction(SearchAction.SharePlaylist) })
        }
    }
}

private fun SearchVideoMenuAction.icon(): AppIcon = when (this) {
    SearchVideoMenuAction.Queue -> AppIcon.QueueAdd
    SearchVideoMenuAction.Download -> AppIcon.Download
    SearchVideoMenuAction.WatchLater -> AppIcon.Clock
    SearchVideoMenuAction.SaveToPlaylist -> AppIcon.Bookmark
    SearchVideoMenuAction.Share -> AppIcon.Share
    SearchVideoMenuAction.NotInterested -> AppIcon.Block
    SearchVideoMenuAction.DontRecommend -> AppIcon.Close
}

/** Result type as always-visible chips; the same choice (plus upload date) stays in the filter strip. */
@Composable
private fun TypeChips(selected: SearchResultType, hideShorts: Boolean, inset: Dp, onAction: (SearchAction) -> Unit) {
    val types = SearchResultType.entries.filterNot { hideShorts && it == SearchResultType.Shorts }
    LazyRow(Modifier.fillMaxWidth().testTag("search-type-chips"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm), contentPadding = PaddingValues(horizontal = inset)) {
        items(types, key = { it.name }) { type ->
            FilterChip(stringResource(type.labelResource), type == selected,
                { onAction(SearchAction.SelectResultType(type)) }, Modifier.testTag("search-type-chip-${type.name}"))
        }
    }
}

/** Inline dual-select strip below the bar; selections apply immediately. */
@Composable
private fun SearchFilterBar(filters: SearchFilters, onAction: (SearchAction) -> Unit) {
    val density = LocalDensity.current
    val textHeight = with(density) { SearchResultsLayout.FilterTextSize.sp.toDp() }
    val chipHeight = maxOf(SearchResultsLayout.FilterGroupHeight.dp, textHeight + 28.dp)
    val barHeight = chipHeight + 25.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val barWidth = minOf(maxWidth.value, SearchResultsLayout.ColumnMaxWidth)
        Box(Modifier.fillMaxWidth().height(barHeight).testTag("search-filter-bar-host"),
            contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.width(barWidth.dp).fillMaxHeight().testTag("search-filter-bar"),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(SearchResultsLayout.FilterBarPadding.dp)
                        .horizontalScroll(rememberScrollState()).testTag("search-filter-groups"),
                    horizontalArrangement = Arrangement.spacedBy(SearchResultsLayout.FilterGroupGap.dp),
                ) {
                    SelectField(
                        tag = "search-filter-type",
                        selected = filters.type,
                        options = SearchResultType.entries.filterNot { LocalHideShorts.current && it == SearchResultType.Shorts },
                        labelOf = { stringResource(it.labelResource) },
                        minWidth = SearchResultsLayout.FilterTypeMinWidth,
                        chipHeight = chipHeight,
                        onSelect = { onAction(SearchAction.SelectResultType(it)) },
                    )
                    SelectField(
                        tag = "search-filter-date",
                        selected = filters.uploaded,
                        options = SearchUploadDate.entries,
                        labelOf = { stringResource(it.labelResource) },
                        minWidth = SearchResultsLayout.FilterDateMinWidth,
                        chipHeight = chipHeight,
                        onSelect = { onAction(SearchAction.SelectUploadDate(it)) },
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp)
                        .background(DesignTokens.Border)
                        .testTag("search-filter-divider"),
                )
            }
        }
    }
}

/** Back, the query as a pill that reopens the search panel, then filters, voice and the page menu. */
@Composable
private fun SearchTopBar(query: String, filters: SearchFilters, filtersOpen: Boolean, onAction: (SearchAction) -> Unit, onBack: () -> Unit) {
    val filtered = filtersOpen || filters.uploaded != SearchUploadDate.Anytime
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(DesignTokens.Background).padding(horizontal = MinSpace.xs)
        .testTag("search-topbar"), verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(AppIcon.Back, stringResource(R.string.a11y_back), onClick = onBack)
        Box(Modifier.weight(1f).heightIn(min = MinSpace.Touch).testTag("search-query")
            .clickable(role = Role.Button) { onAction(SearchAction.OpenSearch) }, contentAlignment = Alignment.CenterStart) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(CircleShape).background(DesignTokens.Surface)
                .testTag("search-field").padding(horizontal = MinSpace.lg), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcon.Search, Modifier.size(18.dp), color = DesignTokens.Secondary)
                Spacer(Modifier.width(MinSpace.sm))
                AppText(query, MinType.Body, Modifier.weight(1f).testTag("search-query-label"), maxLines = 1)
            }
        }
        AppIconButton(AppIcon.Filter, stringResource(R.string.a11y_search_filters),
            background = if (filtered) DesignTokens.AccentContainer else Color.Transparent) { onAction(SearchAction.OpenFilters) }
        AppIconButton(AppIcon.Voice, stringResource(R.string.a11y_voice_search)) { onAction(SearchAction.VoiceSearch) }
        AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_options)) { onAction(SearchAction.OpenPageMenu) }
    }
}

@Composable
private fun SearchNavigationBar(signedIn: Boolean, onAction: (SearchAction) -> Unit) {
    MinNavigationBar(
        tag = "search", signedIn = signedIn, selected = null,
        onSelect = { tab -> onAction(SearchAction.SelectDestination(SearchDestination.valueOf(tab.name))) },
    )
}
