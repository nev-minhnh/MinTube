package com.hhst.youtubelite.explore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.explore.domain.ExploreAction
import com.hhst.youtubelite.explore.domain.ExploreCategory
import com.hhst.youtubelite.explore.domain.ExploreContent
import com.hhst.youtubelite.explore.domain.ExploreUiState
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.ui.components.FilterChip
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.MinTopBar
import com.hhst.youtubelite.ui.components.PagingFooter
import com.hhst.youtubelite.ui.components.PlaylistCard
import com.hhst.youtubelite.ui.components.SectionHeader
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.components.VideoCard
import com.hhst.youtubelite.ui.components.VideoCardSkeleton
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.CollapsingAppBar
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.RefreshableContent
import com.hhst.youtubelite.ui.design.VerificationCard
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.rememberAppBarCollapseState
import com.hhst.youtubelite.watch.domain.WatchProvisionalStore
import com.hhst.youtubelite.watch.domain.toProvisional

@Composable
fun ExploreScreen(state: ExploreUiState, onAction: (ExploreAction) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val appBar = rememberAppBarCollapseState(state.category, enabled = state.content is ExploreContent.Ready)
    BoxWithConstraints(modifier.fillMaxSize().background(DesignTokens.Background).testTag("explore-screen").nestedScroll(appBar.connection)) {
        // The available width, not the display: split-screen gets the right grid.
        val window = WindowClass.of(maxWidth.value.toInt())
        val inset = MinSpace.pageInset(window)
        // Carousel cards track the window: at a 240dp split they stay inside the viewport.
        val shelfCard = (maxWidth * 0.8f).coerceAtMost(300.dp)
        // Scroll positions are retained per category (and per shelf) across tab switches
        // AND configuration rebuilds (saved via savers, not plain remember).
        val columnStates = rememberSaveable(saver = columnStatesSaver) { mutableStateMapOf<ExploreCategory, LazyListState>() }
        val gridStates = rememberSaveable(saver = gridStatesSaver) { mutableStateMapOf<ExploreCategory, LazyGridState>() }
        val shelfStates = rememberSaveable(saver = shelfStatesSaver) { mutableStateMapOf<String, LazyListState>() }
        Column(Modifier.fillMaxSize()) {
            CollapsingAppBar(appBar) {
                MinTopBar(title = stringResource(R.string.a11y_explore), onBack = onBack, tag = "explore-topbar")
            }
            CategoryChips(state.category, inset, onAction)
            RefreshableContent(value = state, refreshing = state.content == ExploreContent.Loading,
                    onRefresh = { onAction(ExploreAction.Retry) }, modifier = Modifier.weight(1f).fillMaxWidth(),
                    enabled = true, contentKey = state.category, tag = "explore-refresh",
                    canPull = { if ((state.content as? ExploreContent.Ready)?.page?.sections?.isNotEmpty() == true)
                        columnStates[state.category]?.canScrollBackward != true
                        else gridStates[state.category]?.canScrollBackward != true }) { state ->
                when (val content = state.content) {
                    ExploreContent.Loading -> ExploreSkeleton(inset, shelfCard)
                    ExploreContent.Failed -> if (state.verification != null) VerificationCard(
                        state.verification,
                        onVerify = { onAction(ExploreAction.OpenVerification(state.verification!!.challengeUrl)) },
                        onDismiss = { onAction(ExploreAction.DismissVerification) },
                        modifier = Modifier.testTag("explore-verification"),
                    ) else ExploreError(onAction)
                    is ExploreContent.Ready -> if (content.page.sections.isNotEmpty())
                        SectionedContainer(content, inset, shelfCard,
                            columnStates.getOrPut(state.category) { LazyListState() },
                            { index -> shelfStates.getOrPut("${state.category.name}:$index") { LazyListState() } },
                            onAction)
                    else CategoryContainer(state.category, content, window,
                        gridStates.getOrPut(state.category) { LazyGridState() }, onAction)
                }
            }
        }
    }
}

/** Lazy scroll states are not parcelable; their positions are. */
private val columnStatesSaver: Saver<SnapshotStateMap<ExploreCategory, LazyListState>, Any> = mapSaver(
    save = { states -> states.entries.associate { (category, state) ->
        category.name to listOf(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) } },
    restore = { saved -> mutableStateMapOf<ExploreCategory, LazyListState>().apply {
        saved.forEach { (name, pos) -> val p = pos as List<Int>
            put(ExploreCategory.valueOf(name), LazyListState(p[0], p[1])) } } },
)

private val gridStatesSaver: Saver<SnapshotStateMap<ExploreCategory, LazyGridState>, Any> = mapSaver(
    save = { states -> states.entries.associate { (category, state) ->
        category.name to listOf(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) } },
    restore = { saved -> mutableStateMapOf<ExploreCategory, LazyGridState>().apply {
        saved.forEach { (name, pos) -> val p = pos as List<Int>
            put(ExploreCategory.valueOf(name), LazyGridState(p[0], p[1])) } } },
)

private val shelfStatesSaver: Saver<SnapshotStateMap<String, LazyListState>, Any> = mapSaver(
    save = { states -> states.entries.associate { (key, state) ->
        key to listOf(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) } },
    restore = { saved -> mutableStateMapOf<String, LazyListState>().apply {
        saved.forEach { (key, pos) -> val p = pos as List<Int>
            put(key, LazyListState(p[0], p[1])) } } },
)

/**
 * First-load placeholder matching the page geometry: shelved cards on a lazy row (a plain Row
 * squeezes trailing fixed-width cards to zero on narrow screens). No duplicate chip row: the
 * real category chips render above.
 */
@Composable
private fun ExploreSkeleton(inset: Dp, cardWidth: Dp) {
    Column(Modifier.fillMaxSize().testTag("explore-skeleton").clearAndSetSemantics {}) {
        repeat(2) {
            SkeletonLine(Modifier.padding(start = inset, top = MinSpace.lg, bottom = MinSpace.md).width(160.dp), height = 20.dp)
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MinSpace.md),
                contentPadding = PaddingValues(horizontal = inset), userScrollEnabled = false) {
                items(3) { VideoCardSkeleton(Modifier.width(cardWidth)) }
            }
        }
    }
}

@Composable
private fun ExploreError(onAction: (ExploreAction) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("explore-error"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        StateMessage(
            title = stringResource(R.string.ui_something_went_wrong),
            message = stringResource(R.string.ui_please_try_again),
            kind = StateKind.Error,
            actionLabel = stringResource(R.string.retry),
            onAction = { onAction(ExploreAction.Retry) },
            tag = "explore-error-message",
        )
    }
}

@Composable
private fun CategoryChips(selected: ExploreCategory, inset: Dp, onAction: (ExploreAction) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().testTag("explore-chips"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm), contentPadding = PaddingValues(horizontal = inset)) {
        listItems(ExploreCategory.entries, key = { it.name }) { category ->
            FilterChip(stringResource(category.labelResource), category == selected,
                { onAction(ExploreAction.SelectCategory(category)) }, Modifier.testTag("explore-chip-${category.name}"))
        }
    }
}

@Composable
private fun SectionedContainer(content: ExploreContent.Ready, inset: Dp, cardWidth: Dp,
    columnState: LazyListState, shelfState: (Int) -> LazyListState, onAction: (ExploreAction) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("explore-sections"),
        state = columnState,
        contentPadding = PaddingValues(bottom = MinSpace.xxl),
    ) {
        content.page.sections.forEachIndexed { index, section ->
            if (section.videos.isEmpty() && section.playlists.isEmpty()) {
                if (section.nextKey != null || section.append != AppendState.Idle) {
                    item(key = "pending-$index", contentType = "pending-shelf") {
                        ShelfTail(index, section.append, section.nextKey, section.automaticPagingPaused,
                            Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch), onAction)
                    }
                }
                return@forEachIndexed
            }
            item(key = "section-$index", contentType = "section-header") {
                SectionHeader(section.title ?: stringResource(R.string.a11y_explore),
                    Modifier.padding(start = inset, end = inset, top = MinSpace.sm).testTag("explore-section-${index}-title"))
            }
            item(key = "carousel-$index", contentType = "carousel") {
                LazyRow(
                    Modifier.fillMaxWidth().testTag("explore-carousel-$index"),
                    state = shelfState(index),
                    horizontalArrangement = Arrangement.spacedBy(MinSpace.md),
                    contentPadding = PaddingValues(horizontal = inset),
                ) {
                    listItems(section.videos, key = { it.id }, contentType = { "video" }) { item ->
                        ExploreVideoCard(item, Modifier.width(cardWidth), onAction)
                    }
                    listItems(section.playlists, key = { "playlist-${it.id}" }, contentType = { "playlist" }) { playlist ->
                        ExplorePlaylistCard(playlist, Modifier.width(cardWidth), onAction)
                    }
                    if (section.nextKey != null || section.append != AppendState.Idle) {
                        item(key = "shelf-more-$index", contentType = "shelf-tail") {
                            ShelfTail(index, section.append, section.nextKey, section.automaticPagingPaused,
                                Modifier.widthIn(min = 96.dp).height(cardWidth * 9f / 16f), onAction)
                        }
                    }
                }
            }
        }
        item(key = "page-tail", contentType = "page-tail") {
            PageTail(content.append, content.page.nextKey, content.automaticPagingPaused, onAction)
        }
    }
}

@Composable
private fun ShelfTail(index: Int, append: AppendState, nextKey: String?, paused: Boolean,
    modifier: Modifier, onAction: (ExploreAction) -> Unit) {
    Box(modifier.testTag("explore-shelf-$index-tail"), contentAlignment = Alignment.Center) {
        when (append) {
            AppendState.Loading -> ActionProgress()
            AppendState.Failed -> MinButton(stringResource(R.string.retry), { onAction(ExploreAction.LoadShelf(index)) },
                kind = MinButtonKind.Text, testTag = "explore-shelf-$index-retry")
            AppendState.Idle -> {
                if (paused) MinButton(stringResource(R.string.load_more), { onAction(ExploreAction.LoadShelf(index)) },
                    kind = MinButtonKind.Tonal, testTag = "explore-shelf-$index-load-more")
                else {
                    LaunchedEffect(index, nextKey) { onAction(ExploreAction.AutomaticLoadShelf(index)) }
                    ActionProgress()
                }
            }
        }
    }
}

/** The page tail keeps its own request effect; `PagingFooter` only draws the progress and retry states. */
@Composable
private fun PageTail(append: AppendState, nextKey: String?, paused: Boolean, onAction: (ExploreAction) -> Unit) {
    if (nextKey == null && append == AppendState.Idle) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (append) {
            AppendState.Loading -> Box(Modifier.testTag("explore-tail-loading")) { PagingFooter(loading = true, failed = false) {} }
            AppendState.Failed -> Box(Modifier.testTag("explore-tail-retry")) {
                PagingFooter(loading = false, failed = true) { onAction(ExploreAction.LoadMore) }
            }
            AppendState.Idle -> if (paused) Box(Modifier.padding(vertical = MinSpace.sm).testTag("explore-load-more")) {
                MinButton(stringResource(R.string.load_more), { onAction(ExploreAction.LoadMore) }, kind = MinButtonKind.Tonal)
            } else LaunchedEffect(nextKey) { onAction(ExploreAction.AutomaticLoadMore) }
        }
    }
}

@Composable
private fun CategoryContainer(category: ExploreCategory, content: ExploreContent.Ready, window: WindowClass,
    gridState: LazyGridState, onAction: (ExploreAction) -> Unit) {
    val inset = MinSpace.pageInset(window)
    val page = content.page
    val empty = page.videos.isEmpty() && page.playlists.isEmpty() && page.nextKey == null && content.append == AppendState.Idle
    LazyVerticalGrid(
        columns = GridCells.Fixed(window.feedColumns),
        modifier = Modifier.fillMaxSize().testTag("explore-list"),
        state = gridState,
        contentPadding = PaddingValues(start = inset, end = inset, bottom = MinSpace.xxl),
        horizontalArrangement = Arrangement.spacedBy(MinSpace.lg),
    ) {
        if (empty) item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
            StateMessage(
                title = stringResource(R.string.browse_explore_empty_title),
                message = stringResource(R.string.browse_explore_empty_message),
                kind = StateKind.Empty,
                actionLabel = stringResource(R.string.retry),
                onAction = { onAction(ExploreAction.Retry) },
                tag = "explore-empty",
            )
        } else item(key = "category-header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            SectionHeader(stringResource(category.labelResource), Modifier.testTag("explore-category-title"))
        }
        gridItems(page.videos, key = { it.id }, contentType = { "video" }) { item ->
            ExploreVideoCard(item, Modifier.padding(bottom = MinSpace.xxl), onAction)
        }
        gridItems(page.playlists, key = { "playlist-${it.id}" }, contentType = { "playlist" }) { playlist ->
            ExplorePlaylistCard(playlist, Modifier.padding(bottom = MinSpace.xxl), onAction)
        }
        item(key = "page-tail", span = { GridItemSpan(maxLineSpan) }, contentType = "page-tail") {
            PageTail(content.append, page.nextKey, content.automaticPagingPaused, onAction)
        }
    }
}

@Composable
private fun ExplorePlaylistCard(playlist: PlaylistSummary, modifier: Modifier, onAction: (ExploreAction) -> Unit) {
    PlaylistCard(
        title = playlist.title, metadata = playlist.owner, artwork = playlist.artwork,
        onClick = { onAction(ExploreAction.OpenPlaylist(playlist.id)) },
        modifier = modifier.testTag("explore-playlist-${playlist.id}"),
        countLabel = playlist.countLabel,
    )
}

@Composable
private fun ExploreVideoCard(item: FeedItem.Video, modifier: Modifier, onAction: (ExploreAction) -> Unit) {
    VideoCard(
        title = rememberDeArrowTitle(item.id, item.title),
        metadata = listOf(item.author, item.metadata).filter(String::isNotBlank).joinToString(" · "),
        artwork = item.artwork,
        onClick = {
            WatchProvisionalStore.put(item.toProvisional())
            onAction(ExploreAction.OpenVideo(item.id))
        },
        modifier = modifier.testTag("explore-item-${item.id}"),
        durationLabel = if (item.live) stringResource(R.string.ui_live) else item.duration,
        live = item.live,
        // The author avatar, never the video still; a blank channel id goes nowhere.
        avatar = item.avatar.orEmpty(),
        avatarDescription = if (item.channelId.isNotBlank()) stringResource(R.string.a11y_go_to_channel, item.author) else null,
        onAvatarClick = if (item.channelId.isNotBlank()) ({ onAction(ExploreAction.OpenChannel(item.channelId)) }) else null,
        videoId = item.id,
    )
}
