package com.hhst.youtubelite.home.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hhst.youtubelite.R
import com.hhst.youtubelite.feedback.presentation.FeedbackReason
import com.hhst.youtubelite.feedback.presentation.FeedbackSheet
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.FeedMenuKind
import com.hhst.youtubelite.home.domain.HomeAction
import com.hhst.youtubelite.home.domain.HomeCatalog
import com.hhst.youtubelite.home.domain.HomeContent
import com.hhst.youtubelite.home.domain.HomeDestination
import com.hhst.youtubelite.home.domain.HomeNavigationRequest
import com.hhst.youtubelite.home.domain.HomeOverlay
import com.hhst.youtubelite.home.domain.HomeUiState
import com.hhst.youtubelite.playlist.data.FixturePlaylistRepository
import com.hhst.youtubelite.post.presentation.PostCardInteractions
import com.hhst.youtubelite.saves.domain.NewPlaylistDraft
import com.hhst.youtubelite.saves.domain.SavePlaylist
import com.hhst.youtubelite.saves.presentation.NewPlaylistSheet
import com.hhst.youtubelite.saves.presentation.SaveSheetLive
import com.hhst.youtubelite.saves.presentation.SaveToPlaylistSheet
import com.hhst.youtubelite.ui.components.FilterChip
import com.hhst.youtubelite.ui.components.MainTab
import com.hhst.youtubelite.ui.components.MinAvatarButton
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.MinNavigationBar
import com.hhst.youtubelite.ui.components.PagingFooter
import com.hhst.youtubelite.ui.components.SearchRows
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppMotion
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.AppTheme
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.LocalHideShorts
import com.hhst.youtubelite.ui.design.LocalMotionScale
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinTubeBrand
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.PullRefreshIndicator
import com.hhst.youtubelite.ui.design.VerificationBanner
import com.hhst.youtubelite.ui.design.VerificationCard
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.appDescription
import com.hhst.youtubelite.ui.design.appStateLayer
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.pullRefreshGesture
import com.hhst.youtubelite.ui.design.reasonResources
import com.hhst.youtubelite.ui.design.rememberAppInteractions
import com.hhst.youtubelite.ui.design.rememberPullRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.hhst.youtubelite.ui.design.miniDockListPadding

/** Top bar (56dp) plus chip strip (48dp) before the first measure pass reports the real height. */
private val ChromeEstimate = 104.dp

/** Stateless rendering boundary; local state is limited to saved scroll/focus state. */
// The warm collector belongs to LaunchedEffect and is cancelled on key changes
// or disposal; the lint follows HomeActivity's deferred setup through onResume.
@android.annotation.SuppressLint("RepeatOnLifecycleWrongUsage")
@Composable
fun HomeScreen(state: HomeUiState, onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier,
    searchRows: SearchRows? = null,
    onWarmCandidate: ((String) -> Unit)? = null,
    postInteractions: PostCardInteractions? = null) {
    AppTheme {
        val hideShorts = state.hideShorts || LocalHideShorts.current
        BoxWithConstraints(modifier.fillMaxSize().background(DesignTokens.Background).testTag("home-screen")) {
            // The available width, not the display: split-screen and previews get the right grid.
            val window = WindowClass.of(maxWidth.value.toInt())
            val inset = MinSpace.pageInset(window)
            val density = LocalDensity.current
            // The chrome (top bar, chips, refresh notice) floats over the feed and grows with the
            // font scale, so the feed inset follows its measured height instead of constants.
            var chromeHeightPx by remember { mutableIntStateOf(with(density) { ChromeEstimate.roundToPx() }) }
            val chromeHeight = with(density) { chromeHeightPx.toDp() }
            val grid = rememberLazyGridState()
            var chromeHidden by rememberSaveable { mutableStateOf(false) }
            var displayedFilter by rememberSaveable { mutableStateOf(state.selectedChip) }
            var displayedSession by rememberSaveable { mutableStateOf(state.sessionGeneration) }
            // Not saveable: a recreated screen adopts the current count instead of jumping to the top.
            var displayedRefreshes by remember { mutableIntStateOf(state.completedUserRefreshes) }
            LaunchedEffect(state.selectedChip, state.sessionGeneration, state.completedUserRefreshes) {
                if (displayedFilter != state.selectedChip || displayedSession != null && displayedSession != state.sessionGeneration ||
                    displayedRefreshes != state.completedUserRefreshes) {
                    grid.scrollToItem(0)
                    chromeHidden = false
                }
                displayedFilter = state.selectedChip
                displayedSession = state.sessionGeneration
                displayedRefreshes = state.completedUserRefreshes
            }
            val feed = state.content as? HomeContent.Feed
            val chromeOffset by animateFloatAsState(if (chromeHidden && feed != null) 1f else 0f,
                AppMotion.scrollResponse(LocalMotionScale.current), label = "header")
            val visibleItems = remember(feed?.page?.items, hideShorts) {
                feed?.page?.items.orEmpty().filterNot { hideShorts && (it is FeedItem.Shorts || it is FeedItem.Video && it.short) }
            }
            val warmCandidate by rememberUpdatedState(onWarmCandidate)
            val homeLifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(grid, visibleItems, state.overlay == HomeOverlay.None, onWarmCandidate != null, homeLifecycle) {
                if (onWarmCandidate == null || state.overlay != HomeOverlay.None) return@LaunchedEffect
                val candidates = visibleItems.associateBy { it.id }
                homeLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    snapshotFlow {
                        if (grid.isScrollInProgress) null else grid.layoutInfo.let { layout ->
                            layout.visibleItemsInfo.firstNotNullOfOrNull { visible ->
                                val shown = minOf(visible.offset.y + visible.size.height, layout.viewportEndOffset) -
                                    maxOf(visible.offset.y, layout.viewportStartOffset)
                                if (shown * 2 < visible.size.height) null else when (val item = candidates[visible.key]) {
                                    is FeedItem.Video -> item.takeUnless { it.live }?.id
                                    is FeedItem.Shorts -> item.videos.firstOrNull { !it.live }?.id
                                    else -> null
                                }
                            }
                        }
                    }.collectLatest { id ->
                        if (id != null) { delay(600); warmCandidate?.invoke(id) }
                    }
                }
            }
            val catalogMode = state.chips == HomeCatalog.filters
            val userRefreshing = when (val visible = state.content) {
                is HomeContent.Feed -> visible.refreshing && visible.userRefresh
                is HomeContent.Empty -> visible.refreshing && visible.userRefresh
                else -> false
            }
            val pullMotion = rememberPullRefreshState(userRefreshing,
                enabled = state.overlay == HomeOverlay.None && state.content != HomeContent.Loading,
                canPull = { feed == null || !grid.canScrollBackward }) { onAction(HomeAction.Refresh) }
            val chromeConnection = remember(pullMotion) { object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0f) chromeHidden = false
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && consumed.y < 0f && grid.canScrollBackward && pullMotion.offset == 0f)
                        chromeHidden = true
                    return Offset.Zero
                }
            } }
            LaunchedEffect(grid, feed != null) {
                if (feed == null) return@LaunchedEffect
                snapshotFlow { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }.collect { current ->
                    if (current.first == 0 && current.second == 0) chromeHidden = false
                }
            }
            val nearEnd by remember(grid) { derivedStateOf {
                val layout = grid.layoutInfo
                layout.totalItemsCount > 0 && (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 3
            } }
            LaunchedEffect(nearEnd, feed?.page?.nextKey, feed?.append, feed?.refreshing) {
                if (nearEnd && feed?.page?.nextKey != null && feed.append == AppendState.Idle && !feed.refreshing && !feed.automaticPagingPaused)
                    onAction(HomeAction.AutomaticLoadNext)
            }
            val searchOpen = state.overlay == HomeOverlay.Search
            Column(Modifier.fillMaxSize().then(if (state.overlay != HomeOverlay.None) Modifier.clearAndSetSemantics {} else Modifier)) {
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().nestedScroll(chromeConnection)
                    .pullRefreshGesture(pullMotion)) {
                    val dock = miniDockListPadding(MinSpace.xxl)
                    when (val content = state.content) {
                        is HomeContent.Empty -> Box(Modifier.fillMaxSize().padding(top = chromeHeight).graphicsLayer { translationY = pullMotion.offset }) {
                            EmptyHome(content, firstVisit = !searchOpen && !catalogMode, catalog = catalogMode, onAction = onAction)
                        }
                        HomeContent.Failed -> Box(Modifier.fillMaxSize().padding(top = chromeHeight).graphicsLayer { translationY = pullMotion.offset }) {
                            if (state.verification != null) VerificationCard(
                                state.verification,
                                onVerify = { onAction(HomeAction.Navigate(HomeNavigationRequest.OpenVerification(state.verification!!.challengeUrl))) },
                                onDismiss = { onAction(HomeAction.DismissVerification) },
                                modifier = Modifier.testTag("home-verification"),
                            ) else ErrorHome(onAction)
                        }
                        // Navigation and card anchors stay in place while the first page loads.
                        HomeContent.Loading -> HomeSkeleton(window, chromeHeight)
                        is HomeContent.Feed -> LazyVerticalGrid(
                            columns = GridCells.Fixed(com.hhst.youtubelite.ui.design.rememberFeedColumns()), state = grid,
                            // Pull moves the feed rows only; the chrome stack keeps its place.
                            modifier = Modifier.fillMaxSize().then(dock.modifier).graphicsLayer { translationY = pullMotion.offset }.testTag("home-feed"),
                            contentPadding = PaddingValues(start = inset, end = inset, top = chromeHeight + MinSpace.sm, bottom = dock.bottom),
                            horizontalArrangement = Arrangement.spacedBy(MinSpace.lg),
                        ) {
                            homeCards(visibleItems, window, state.live, onAction, postInteractions)
                            item(key = "home-footer", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                                FeedFooter(content, onAction)
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().onSizeChanged { chromeHeightPx = it.height }
                        .graphicsLayer { translationY = -chromeHeightPx * chromeOffset }.background(DesignTokens.Background)) {
                        HomeTopBar(state.avatar.takeIf { state.signedInPreview }, onAction)
                        when (val content = state.content) {
                            HomeContent.Loading -> ChipStrip(state.selectedChip, state.chips, catalogMode, inset, onAction)
                            is HomeContent.Feed -> {
                                ChipStrip(state.selectedChip, state.chips, content.page.catalog != null, inset, onAction)
                                if (content.refreshFailed) RetryRow(stringResource(R.string.ui_couldn_t_refresh_try_again), inset) { onAction(HomeAction.Refresh) }
                            }
                            else -> if (catalogMode) ChipStrip(state.selectedChip, state.chips, true, inset, onAction)
                        }
                    }
                    PullRefreshIndicator(pullMotion, "home-refresh-indicator", chromeHeight * (1f - chromeOffset))
                }
                HomeBottomBar(state.signedInPreview, onAction, state.avatar)
            }
            when (val overlay = state.overlay) {
                HomeOverlay.None -> Unit
                HomeOverlay.Search -> SearchOverlay(state, onAction, searchRows)
                is HomeOverlay.Menu -> if (overlay.kind == FeedMenuKind.Mix) MixMenu(onAction, state.live) else VideoMenu(
                    onAction, overlay.kind, state.live, state.signedInPreview,
                    (state.content as? HomeContent.Feed)?.page?.items?.filterIsInstance<FeedItem.Video>()
                        ?.firstOrNull { it.id == overlay.itemId && it.feedbackGeneration == state.sessionGeneration },
                    overlay,
                )
                is HomeOverlay.Save -> if (overlay.newPlaylist) NewPlaylistSheet(
                    draft = NewPlaylistDraft(overlay.title, overlay.description),
                    onDismiss = { onAction(HomeAction.CloseOverlay) },
                    onDraftChange = { onAction(HomeAction.SaveDraftChanged(it.title, it.description)) },
                    onSubmit = { onAction(HomeAction.SubmitNewPlaylist) },
                    pending = overlay.pending,
                ) else if (overlay.live) SaveToPlaylistSheet(
                    playlists = emptyList(),
                    onDismiss = { onAction(HomeAction.CloseOverlay) },
                    onSelect = {},
                    onNewPlaylist = { onAction(HomeAction.OpenNewPlaylist) },
                    live = SaveSheetLive(
                        choices = overlay.choices,
                        failed = overlay.choicesFailed,
                        pending = overlay.pending,
                        onToggle = { onAction(HomeAction.SaveChoiceToggled(it)) },
                        onRetry = { onAction(HomeAction.SaveChoicesRetry) },
                    ),
                ) else SaveToPlaylistSheet(
                    playlists = homeSavePlaylists,
                    onDismiss = { onAction(HomeAction.CloseOverlay) },
                    onSelect = { onAction(HomeAction.SaveDestinationSelected(it)) },
                    onNewPlaylist = { onAction(HomeAction.OpenNewPlaylist) },
                )
                is HomeOverlay.Feedback -> FeedbackSheet(
                    title = stringResource(overlay.kind.labelResource),
                    reasons = overlay.kind.reasons.mapIndexed { index, reason ->
                        FeedbackReason(reason, "feedback-reason-$index", overlay.kind.reasonResources[index])
                    },
                    onDismiss = { onAction(HomeAction.CloseOverlay) },
                    onSelect = { onAction(HomeAction.FeedbackReason(it.label)) },
                )
            }
            if (feed != null) state.verification?.let { verification ->
                VerificationBanner(
                    verification,
                    onVerify = { onAction(HomeAction.Navigate(HomeNavigationRequest.OpenVerification(verification.challengeUrl))) },
                    onDismiss = { onAction(HomeAction.DismissVerification) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(start = inset, end = inset, bottom = DesignTokens.BottomBarHeight + MinSpace.lg)
                        .testTag("home-verification-banner"),
                )
            }
        }
    }
}

private val homeSavePlaylists: List<SavePlaylist> =
    FixturePlaylistRepository.personalLists()
        .filter { it.id != FixturePlaylistRepository.WATCH_LATER }
        .map { SavePlaylist(it.id, it.title) }

private fun LazyGridScope.homeCards(items: List<FeedItem>, window: WindowClass, live: Boolean, onAction: (HomeAction) -> Unit,
    postInteractions: PostCardInteractions?) {
    items(items, key = { it.id }, span = {
        if (it is FeedItem.Shorts || it is FeedItem.Post) GridItemSpan(maxLineSpan) else GridItemSpan(1)
    }, contentType = { it.javaClass.simpleName }) { item ->
        Box((if (LocalMotionScale.current > 0f) Modifier.animateItem() else Modifier).padding(bottom = MinSpace.xxl)) {
            FeedCard(item, window, live, onAction, postInteractions)
        }
    }
}

/**
 * Tail of the feed: progress while more is coming, a retry after a failed append, a manual step
 * once automatic paging pauses. The near-end effect in [HomeScreen] owns the automatic request,
 * so the footer never asks for a page on its own.
 */
@Composable
private fun FeedFooter(content: HomeContent.Feed, onAction: (HomeAction) -> Unit) {
    when (content.append) {
        AppendState.Loading -> Box(Modifier.fillMaxWidth().appDescription(R.string.a11y_loading)) {
            PagingFooter(loading = true, failed = false) {}
        }
        AppendState.Failed -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppText(stringResource(R.string.ui_something_went_wrong_tap_to_retry), MinType.Caption, color = DesignTokens.Secondary)
            PagingFooter(loading = false, failed = true) { onAction(HomeAction.LoadNext) }
        }
        AppendState.Idle -> when {
            content.page.nextKey == null -> Spacer(Modifier.size(1.dp))
            content.automaticPagingPaused -> Box(Modifier.fillMaxWidth().padding(vertical = MinSpace.sm).testTag("home-load-more"), contentAlignment = Alignment.Center) {
                MinButton(stringResource(R.string.load_more), { onAction(HomeAction.LoadNext) }, kind = MinButtonKind.Tonal)
            }
            else -> PagingFooter(loading = true, failed = false) {}
        }
    }
}

@Composable
private fun HomeTopBar(avatar: String?, onAction: (HomeAction) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home-topbar").padding(horizontal = MinSpace.xs),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.heightIn(min = MinSpace.Touch).clip(MinRadius.Field).clickable(role = Role.Button) {
            onAction(HomeAction.Navigate(HomeNavigationRequest.Destination(HomeDestination.Home)))
        }.appDescription(R.string.a11y_litube_home).padding(horizontal = MinSpace.md), contentAlignment = Alignment.CenterStart) {
            MinTubeBrand()
        }
        com.hhst.youtubelite.ui.components.PrivateBrowsingBadge(
            com.hhst.youtubelite.account.presentation.rememberPrivateBrowsing(), compact = true)
        Spacer(Modifier.weight(1f))
        AppIconButton(AppIcon.Search, stringResource(R.string.a11y_search_youtube)) { onAction(HomeAction.OpenSearch) }
        MinAvatarButton(avatar, stringResource(R.string.browse_home_account), {
            onAction(HomeAction.Navigate(HomeNavigationRequest.Destination(HomeDestination.You)))
        }, Modifier.testTag("home-account"))
    }
}

@Composable
private fun HomeBottomBar(signedIn: Boolean, onAction: (HomeAction) -> Unit, avatar: String? = null) {
    MinNavigationBar(
        tag = "home", signedIn = signedIn, selected = MainTab.Home,
        onSelect = { tab -> onAction(HomeAction.Navigate(HomeNavigationRequest.Destination(HomeDestination.valueOf(tab.name)))) },
        avatarAsset = avatar, tabTag = { "nav-${it.name}" },
    )
}

@Composable
private fun ChipStrip(selected: String, chips: List<String>, catalog: Boolean, inset: Dp, onAction: (HomeAction) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().testTag("home-chips"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm), contentPadding = PaddingValues(horizontal = inset)) {
        item { ExploreChip { onAction(HomeAction.Navigate(HomeNavigationRequest.Explore)) } }
        rowItems(chips) { chip ->
            val label = if (!catalog) chip else when (HomeCatalog.forFilter(chip)) {
                HomeCatalog.Popular -> stringResource(R.string.download_filter_all)
                HomeCatalog.MusicCharts -> stringResource(R.string.home_music_charts)
                HomeCatalog.Gaming -> stringResource(R.string.home_popular_gaming)
                HomeCatalog.Sports -> stringResource(R.string.home_popular_sports)
                HomeCatalog.News -> stringResource(R.string.home_top_news)
                null -> chip
            }
            FilterChip(label, chip == selected, { onAction(HomeAction.SelectChip(chip)) })
        }
    }
}

/** Entry to Explore at the head of the chip strip; an icon pill, so it never reads as a filter. */
@Composable
private fun ExploreChip(onClick: () -> Unit) {
    val interactions = rememberAppInteractions()
    Box(Modifier.heightIn(min = MinSpace.Touch)
        .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
        .appDescription(R.string.a11y_explore), contentAlignment = Alignment.Center) {
        Row(Modifier.heightIn(min = 32.dp).clip(CircleShape).background(DesignTokens.Surface)
            .appStateLayer(interactions, 16.dp).padding(horizontal = MinSpace.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcon.Explore, Modifier.size(18.dp), color = DesignTokens.AccentText)
            Spacer(Modifier.width(6.dp))
            AppText(stringResource(R.string.a11y_explore), MinType.Label, maxLines = 1)
        }
    }
}

@Composable
private fun RetryRow(label: String, inset: Dp, retry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = inset), verticalAlignment = Alignment.CenterVertically) {
        AppText(label, MinType.Body, Modifier.weight(1f), color = DesignTokens.Secondary)
        MinButton(stringResource(R.string.retry), retry, kind = MinButtonKind.Text)
    }
}
