package com.hhst.youtubelite.shorts.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.shorts.domain.ShortItem
import com.hhst.youtubelite.shorts.domain.ShortsAction
import com.hhst.youtubelite.shorts.domain.ShortsContent
import com.hhst.youtubelite.shorts.domain.ShortsUiState
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DarkAppPalette
import com.hhst.youtubelite.ui.design.LocalAppColors
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.watch.domain.VideoRating

/**
 * The Shorts feed. The stage is black and its chrome is the fixed dark palette in both app
 * themes: white on scrims, Accent for progress and selection.
 *
 * [onBack] adds the back control to the top bar; [playbackProgress] is the host's progress
 * line for the short the engine is playing (see [ShortProgressBar]).
 */
@Composable
fun ShortsScreen(state: ShortsUiState, onAction: (ShortsAction) -> Unit, modifier: Modifier = Modifier,
    videoContent: (@Composable (String) -> Unit)? = null,
    isVideoReady: (String) -> Boolean = { false },
    playbackControls: (@Composable (String, Boolean) -> Unit)? = null,
    onSwipeStart: (() -> Unit)? = null, onSwipeCancel: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    playbackProgress: (@Composable (String) -> Unit)? = null) {
    CompositionLocalProvider(LocalAppColors provides DarkAppPalette) {
        Box(modifier.fillMaxSize().background(Color.Black).testTag("shorts-screen")) {
            when (val content = state.content) {
                ShortsContent.Loading -> ShortsSkeleton()
                ShortsContent.Failed -> ShortsStateMessage {
                    StateMessage(
                        stringResource(R.string.ui_something_went_wrong),
                        message = stringResource(R.string.ui_shorts_could_not_be_loaded),
                        kind = StateKind.Error,
                        actionLabel = stringResource(R.string.retry),
                        onAction = { onAction(ShortsAction.Retry) },
                        tag = "shorts-error",
                    )
                }
                ShortsContent.Empty -> ShortsStateMessage {
                    StateMessage(
                        stringResource(R.string.ui_no_shorts_available),
                        message = stringResource(R.string.shorts_empty_message),
                        kind = StateKind.Empty,
                        icon = AppIcon.ShortsFilled,
                        actionLabel = stringResource(R.string.retry),
                        onAction = { onAction(ShortsAction.Retry) },
                        tag = "shorts-empty",
                    )
                }
                is ShortsContent.Ready -> ShortsPager(content.page.items, state.currentItemId, onAction, videoContent,
                    isVideoReady, playbackControls, state, onSwipeStart, onSwipeCancel, playbackProgress)
            }
            ShortsTopBar(onBack, scrim = state.content is ShortsContent.Ready)
        }
    }
}

/** Fixed over the pager: back and the screen name. Each page adds its own overflow at the end. */
@Composable
private fun ShortsTopBar(onBack: (() -> Unit)?, scrim: Boolean) {
    if (scrim) Box(Modifier.fillMaxWidth()
        .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent))
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        .height(TopBarHeight + MinSpace.xxl))
    Row(
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .heightIn(min = TopBarHeight)
            .padding(start = if (onBack != null) MinSpace.xs else MinSpace.lg, end = MinSpace.huge + MinSpace.sm)
            .testTag("shorts-topbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) AppIconButton(AppIcon.Back, stringResource(R.string.a11y_back),
            Modifier.testTag("shorts-back"), color = Color.White, onClick = onBack)
        AppText(stringResource(R.string.ui_shorts), MinType.Title.onVideo(), Modifier.semantics { heading() },
            color = Color.White, maxLines = 1)
    }
}

/** Centers a full-area state inside the safe area; scrolls when a large font needs the room. */
@Composable
private fun ShortsStateMessage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = TopBarHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun ShortsSkeleton() {
    val rail = railWidth(LocalDensity.current.fontScale)
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("shorts-skeleton").clearAndSetSemantics {}
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .padding(start = MinSpace.lg, end = rail + MinSpace.md, bottom = MinSpace.lg),
            verticalArrangement = Arrangement.spacedBy(MinSpace.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
                SkeletonBox(Modifier.size(AvatarSize), MinRadius.Full)
                SkeletonLine(Modifier.width(112.dp), height = 16.dp)
            }
            SkeletonLine(height = 16.dp)
            SkeletonLine(fraction = 0.65f, height = 16.dp)
        }
        Column(Modifier.align(Alignment.BottomEnd).width(rail).padding(bottom = MinSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MinSpace.xxl)) {
            repeat(4) { SkeletonBox(Modifier.size(MinSpace.Touch), MinRadius.Full) }
        }
    }
}

@Composable
private fun ShortsPager(items: List<ShortItem>, startItemId: String?, onAction: (ShortsAction) -> Unit, videoContent: (@Composable (String) -> Unit)?,
    isVideoReady: (String) -> Boolean,
    playbackControls: (@Composable (String, Boolean) -> Unit)?,
    state: ShortsUiState, onSwipeStart: (() -> Unit)?, onSwipeCancel: ((String) -> Unit)?,
    playbackProgress: (@Composable (String) -> Unit)?) {
    val pagerState = rememberPagerState(initialPage = items.indexOfFirst { it.id == startItemId }.coerceAtLeast(0)) { items.size }
    val currentAction by rememberUpdatedState(onAction)
    val currentItems by rememberUpdatedState(items)
    val currentSwipeStart by rememberUpdatedState(onSwipeStart)
    val currentSwipeCancel by rememberUpdatedState(onSwipeCancel)
    val currentSelectedId by rememberUpdatedState(state.currentItemId)
    // End-of-playback selection moves the visible page along with the engine.
    LaunchedEffect(state.currentItemId) {
        val index = items.indexOfFirst { it.id == state.currentItemId }
        if (index >= 0 && index != pagerState.settledPage) pagerState.scrollToPage(index)
    }
    // Keyed on the pager only: hydration and appends mutate `items`, and a
    // restart here would replay SelectItem for the settled page.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentItems.getOrNull(page)?.let { currentAction(ShortsAction.SelectItem(it.id)) }
            if (page >= currentItems.size - 3) currentAction(ShortsAction.LoadMore)
        }
    }
    // A swipe silences the settled short while it is in progress; one that
    // settles back onto the short it started on resumes it.
    LaunchedEffect(pagerState) {
        var holding = false
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            when {
                scrolling && !holding -> { holding = true; currentSwipeStart?.invoke() }
                !scrolling && holding -> {
                    holding = false
                    val item = currentItems.getOrNull(pagerState.settledPage)
                    if (item != null && item.id == currentSelectedId) currentSwipeCancel?.invoke(item.id)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Keep one neighbor composed so a fast fling lands on its cover instead of
        // an unpainted window background; the video surface still attaches to the
        // settled page only.
        VerticalPager(state = pagerState, key = { items[it].id }, beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().testTag("shorts-pager")) { page ->
            val settled = page == pagerState.settledPage
            ShortPage(items[page], onAction, if (settled) videoContent else null,
                showArtwork = videoContent == null,
                videoReady = settled && isVideoReady(items[page].id),
                playbackControls = if (settled) playbackControls?.let { controls ->
                    { id -> controls(id, !pagerState.isScrollInProgress && id == state.currentItemId) }
                } else null,
                playbackProgress = if (settled) playbackProgress else null,
                rating = state.ratings[items[page].id] ?: VideoRating.None,
                subscribed = state.subscriptions[items[page].channelId] == true,
                subscribePending = items[page].channelId in state.subscriptionsPending,
                ratingPending = items[page].id in state.ratingsPending)
        }
        val bottomEdge = Modifier.align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = MinSpace.lg)
        if (state.appendFailed) MinButton(stringResource(R.string.retry), { onAction(ShortsAction.RetryMore) },
            bottomEdge, kind = MinButtonKind.Tonal, icon = AppIcon.Refresh, testTag = "shorts-append-retry")
        else if (state.appendLoading && pagerState.settledPage >= items.lastIndex) Box(
            bottomEdge.size(MinSpace.Touch).background(Color.Black.copy(alpha = 0.45f), MinRadius.Full)
                .testTag("shorts-append-loading").clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) { ActionProgress(color = Color.White) }
    }
}
