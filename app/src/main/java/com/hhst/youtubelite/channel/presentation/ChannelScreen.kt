package com.hhst.youtubelite.channel.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.channel.domain.ChannelAction
import com.hhst.youtubelite.channel.domain.ChannelContent
import com.hhst.youtubelite.channel.domain.ChannelDestination
import com.hhst.youtubelite.channel.domain.ChannelOverlay
import com.hhst.youtubelite.channel.domain.ChannelSection
import com.hhst.youtubelite.channel.domain.ChannelTab
import com.hhst.youtubelite.channel.domain.ChannelUiState
import com.hhst.youtubelite.playlist.presentation.PlaylistShareSheet
import com.hhst.youtubelite.post.presentation.PostCardInteractions
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.AppTheme
import com.hhst.youtubelite.ui.design.BrowseNavigationBar
import com.hhst.youtubelite.ui.design.CollapsingAppBar
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.LocalHideShorts
import com.hhst.youtubelite.ui.design.LocalMotionScale
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.RefreshableContent
import com.hhst.youtubelite.ui.design.VerificationBanner
import com.hhst.youtubelite.ui.design.VerificationCard
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.motionDuration
import com.hhst.youtubelite.ui.design.rememberAppBarCollapseState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * The channel page. Every tab is its own list that starts with the same header and a pinned tab
 * row, so a tab keeps its scroll position while the header and tabs stay one continuous surface.
 * The page width is measured, not read from the device: split screen and test frames get the
 * layout of the space they really have.
 */

@Composable
fun ChannelScreen(
    state: ChannelUiState,
    onAction: (ChannelAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    postInteractions: PostCardInteractions? = null,
) {
    val appBar = rememberAppBarCollapseState(state.channelId, enabled = state.overlay == ChannelOverlay.None && state.content is ChannelContent.Ready)
    AppTheme {
        val hideShorts = LocalHideShorts.current
        LaunchedEffect(hideShorts, state.selectedTab) {
            if (hideShorts && state.selectedTab == ChannelTab.Shorts) onAction(ChannelAction.SelectTab(ChannelTab.Home))
        }
        BoxWithConstraints(modifier.fillMaxSize().nestedScroll(appBar.connection).background(DesignTokens.Background).testTag("channel-page")) {
            val width = maxWidth.value
            val height = maxHeight.value
            val homeState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            val videosState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            val searchState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            val aboutScroll = rememberScrollState()
            val playlistsState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            val shortsState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            val liveState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            val postsState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            val postState = rememberSaveableStateHolder()
            // Every tab has its own list, but the tab strip is one continuous control.
            val tabBarState = rememberChannelTabBarState()
            fun listFor(tab: ChannelTab) = when (tab) {
                ChannelTab.Search -> searchState
                ChannelTab.Home -> homeState
                ChannelTab.Playlists -> playlistsState
                ChannelTab.Shorts -> shortsState
                ChannelTab.Live -> liveState
                ChannelTab.Posts -> postsState
                else -> videosState
            }
            var previousTab by rememberSaveable { mutableStateOf(state.selectedTab) }
            // A tab keeps its exact index/offset once visited; only a FIRST visit
            // inherits the outgoing tab's header collapse. The channel is fixed
            // per activity instance, so these sets never leak across channels.
            val visitedTabs = rememberSaveable(
                saver = listSaver<MutableList<ChannelTab>, ChannelTab>(
                    save = { it.toList() }, restore = { it.toMutableList() }),
            ) { mutableStateListOf<ChannelTab>() }
            LaunchedEffect(state.selectedTab, state.content is ChannelContent.Ready) {
                if (state.content !is ChannelContent.Ready || state.selectedTab == previousTab) return@LaunchedEffect
                val source = listFor(previousTab)
                val target = listFor(state.selectedTab)
                val firstVisit = state.selectedTab !in visitedTabs
                visitedTabs.add(previousTab)
                visitedTabs.add(state.selectedTab)
                previousTab = state.selectedTab
                if (firstVisit && target.firstVisibleItemIndex == 0) {
                    if (source.firstVisibleItemIndex > 0) target.scrollToItem(1)
                    else if (target.firstVisibleItemScrollOffset == 0) {
                        target.scrollToItem(0, source.firstVisibleItemScrollOffset)
                    }
                }
            }
            Column(Modifier.fillMaxSize().then(if (state.overlay != ChannelOverlay.None) Modifier.clearAndSetSemantics {} else Modifier)) {
                CollapsingAppBar(appBar) {
                    ChannelTopBar(state, onBack, onAction)
                }
                val sectionLoading = when (state.selectedTab) {
                    ChannelTab.Videos -> state.videosTab is ChannelSection.Loading
                    ChannelTab.Playlists -> state.playlists is ChannelSection.Loading
                    ChannelTab.Shorts -> state.shorts is ChannelSection.Loading
                    ChannelTab.Live -> state.live is ChannelSection.Loading
                    ChannelTab.Posts -> state.posts is ChannelSection.Loading
                    ChannelTab.Search -> state.searchResults is ChannelSection.Loading
                    else -> false
                }
                CompositionLocalProvider(LocalChannelTabBarState provides tabBarState) {
                    RefreshableContent(value = state,
                        refreshing = state.content == ChannelContent.Loading || state.content is ChannelContent.Ready && sectionLoading,
                        onRefresh = {
                            onAction(when {
                                state.content !is ChannelContent.Ready -> ChannelAction.Retry
                                state.selectedTab == ChannelTab.Home -> ChannelAction.Retry
                                state.selectedTab == ChannelTab.Search -> if (state.searchResults != null && state.submittedQuery.isNotBlank())
                                    ChannelAction.RetrySearch else ChannelAction.Retry
                                else -> ChannelAction.RetrySection(state.selectedTab)
                            })
                        }, modifier = Modifier.weight(1f).fillMaxWidth(),
                        enabled = state.overlay == ChannelOverlay.None,
                        contentKey = listOf(state.channelId, state.selectedTab, state.submittedQuery, state.shortsSort, state.liveSort, state.playlistSort),
                        tag = "channel-refresh",
                        canPull = { !listFor(state.selectedTab).canScrollBackward }) { state ->
                        when (val content = state.content) {
                            ChannelContent.Loading -> ChannelSkeleton(width)
                            ChannelContent.Failed -> if (state.verification != null) VerificationCard(
                                state.verification,
                                onVerify = { onAction(ChannelAction.OpenVerification(state.verification!!.challengeUrl)) },
                                onDismiss = { onAction(ChannelAction.DismissVerification) },
                                modifier = Modifier.testTag("channel-verification"),
                            ) else ChannelFailed(onAction)
                            is ChannelContent.Ready -> when (state.selectedTab) {
                                ChannelTab.Search -> ChannelSearchTab(content.page, width, height, searchState, state.searchQuery, state.submittedQuery, onAction,
                                    state.searchResults, canPage = state.searchNextKey != null, append = state.searchAppend, subscribePending = state.subscribePending)
                                ChannelTab.Home -> ChannelHomeTab(content.page, width, homeState, onAction, state.subscribePending)
                                ChannelTab.Playlists -> ChannelPlaylistsTab(content.page, width, height, playlistsState, state.playlistSort, state.playlists, onAction,
                                    canPage = state.playlistsNextKey != null, append = state.playlistsAppend, subscribePending = state.subscribePending)
                                ChannelTab.Shorts -> ChannelShortsTab(content.page, state.shorts, state.shortsSort, width, shortsState, onAction,
                                    production = state.production, canPage = state.shortsNextKey != null, append = state.shortsAppend, subscribePending = state.subscribePending)
                                ChannelTab.Live -> ChannelLiveTab(content.page, state.live, state.liveSort, width, liveState, onAction,
                                    canPage = state.liveNextKey != null, append = state.liveAppend, subscribePending = state.subscribePending)
                                ChannelTab.Posts -> postState.SaveableStateProvider(state.channelId) {
                                    ChannelPostsTab(content.page, state.posts, width, postsState, state.expandedPosts, onAction,
                                        production = state.production, canPage = state.postsNextKey != null, append = state.postsAppend, subscribePending = state.subscribePending,
                                        postInteractions = postInteractions)
                                }
                                else -> ChannelVideosTab(content.page, width, videosState, onAction, state.videosAppend, state.videosTab, state.subscribePending)
                            }
                        }
                        state.verification?.let { challenge ->
                            VerificationBanner(challenge,
                                onVerify = { onAction(ChannelAction.OpenVerification(challenge.challengeUrl)) },
                                onDismiss = { onAction(ChannelAction.DismissVerification) },
                                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = ChannelMetrics.inset(width), vertical = MinSpace.sm)
                                    .testTag("channel-verification-banner"))
                        }
                    }
                }
                ChannelNavigationBar(state.signedIn, onAction)
            }
            if (state.overlay == ChannelOverlay.About && state.content is ChannelContent.Ready)
                ChannelAboutPanel(state.content.page.profile, width, height, onAction, aboutScroll)
            if (state.overlay is ChannelOverlay.PlaylistMenu) PlaylistShareSheet(
                { onAction(ChannelAction.CloseOverlay) }, { onAction(ChannelAction.SharePlaylist) })
            ChannelCommunityMenuSheets(state.overlay, onAction)
        }
    }
}

@Composable
private fun ChannelNavigationBar(signedIn: Boolean, onAction: (ChannelAction) -> Unit) {
    BrowseNavigationBar("channel", signedIn) { destination ->
        onAction(ChannelAction.SelectDestination(ChannelDestination.valueOf(destination.name)))
    }
}

/** Same geometry as `MinTopBar`; written out because the title carries this page's tag and the actions depend on the mode. */
@Composable
private fun ChannelTopBar(state: ChannelUiState, onBack: () -> Unit, onAction: (ChannelAction) -> Unit) {
    val title = (state.content as? ChannelContent.Ready)?.page?.profile?.name ?: ""
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(DesignTokens.Background)
        .testTag("channel-topbar").padding(horizontal = MinSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(AppIcon.Back, stringResource(R.string.a11y_close_channel_page), onClick = onBack)
        AppText(title, MinType.Title, Modifier.weight(1f).testTag("channel-title").semantics { heading() }, maxLines = 1)
        AppIconButton(AppIcon.Search, stringResource(R.string.a11y_search_within_channel)) {
            onAction(ChannelAction.ChannelSearch)
        }
        // The anonymous channel page offers no section menu; production hides the dead entry.
        if (!state.production) AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_channel_options)) {
            onAction(ChannelAction.OpenSectionMenu)
        }
    }
}

private class ChannelTabBarState(val scroll: ScrollState) {
    var viewport by mutableIntStateOf(0)
    val bounds = mutableStateMapOf<ChannelTab, Pair<Int, Int>>()
    val indicatorLeft = Animatable(0f)
    val indicatorWidth = Animatable(0f)
    var indicatorPositioned = false
}

private val LocalChannelTabBarState = staticCompositionLocalOf<ChannelTabBarState?> { null }

@Composable
private fun rememberChannelTabBarState(): ChannelTabBarState {
    val scroll = rememberScrollState()
    return remember(scroll) { ChannelTabBarState(scroll) }
}

private val TabLabelInset = MinSpace.md
private val TabIndicatorHeight = 3.dp

/**
 * Scrollable tab row with an accent indicator that slides under the selected label. The scroll
 * position and the indicator are shared by all tab lists, so switching tabs never resets them.
 */
@Composable
internal fun ChannelTabBar(selected: ChannelTab, onAction: (ChannelAction) -> Unit) {
    val strip = LocalChannelTabBarState.current ?: rememberChannelTabBarState()
    val scroll = strip.scroll
    val bounds = strip.bounds
    val viewport = strip.viewport
    val motionScale = LocalMotionScale.current
    LaunchedEffect(selected, bounds[selected], viewport, motionScale) {
        val (left, right) = bounds[selected] ?: return@LaunchedEffect
        if (viewport == 0) return@LaunchedEffect
        if (!strip.indicatorPositioned || motionScale <= 0f) {
            strip.indicatorLeft.snapTo(left.toFloat())
            strip.indicatorWidth.snapTo((right - left).toFloat())
            strip.indicatorPositioned = true
        }
        coroutineScope {
            launch { strip.indicatorLeft.animateTo(left.toFloat(), tween(motionDuration(220, motionScale), easing = LinearOutSlowInEasing)) }
            launch { strip.indicatorWidth.animateTo((right - left).toFloat(), tween(motionDuration(220, motionScale), easing = LinearOutSlowInEasing)) }
            val scrollTarget = when {
                left < scroll.value -> left
                right > scroll.value + viewport -> right - viewport
                else -> null
            }
            if (scrollTarget != null) {
                if (motionScale <= 0f) scroll.scrollTo(scrollTarget)
                else scroll.animateScrollTo(scrollTarget, tween(motionDuration(220, motionScale), easing = LinearOutSlowInEasing))
            }
        }
    }
    val accent = DesignTokens.Accent
    Column(Modifier.fillMaxWidth().background(DesignTokens.Background).testTag("channel-tabbar")) {
        Box(Modifier.fillMaxWidth()
            .onSizeChanged { strip.viewport = it.width }
            .horizontalScroll(scroll).testTag("channel-tabs-row")) {
            Canvas(Modifier.matchParentSize()) {
                val labelInset = TabLabelInset.toPx()
                val thickness = TabIndicatorHeight.toPx()
                drawRoundRect(accent, topLeft = Offset(strip.indicatorLeft.value + labelInset, size.height - thickness),
                    size = Size((strip.indicatorWidth.value - labelInset * 2).coerceAtLeast(0f), thickness),
                    cornerRadius = CornerRadius(thickness / 2))
            }
            Row(Modifier.padding(horizontal = MinSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                ChannelTab.entries.filterNot { LocalHideShorts.current && it == ChannelTab.Shorts }.forEach { tab ->
                    val isSelected = tab == selected
                    Box(Modifier.heightIn(min = MinSpace.Touch).widthIn(min = MinSpace.Touch)
                        .onGloballyPositioned {
                            val left = it.positionInParent().x.toInt()
                            bounds[tab] = left to left + it.size.width
                        }.testTag("channel-tab-${tab.name}").semantics { this.selected = isSelected }
                        .clickable(role = Role.Tab) { onAction(ChannelAction.SelectTab(tab)) }
                        .padding(horizontal = TabLabelInset, vertical = MinSpace.sm),
                        contentAlignment = Alignment.Center) {
                        AppText(stringResource(tab.labelResource), MinType.Subtitle, maxLines = 1,
                            color = if (isSelected) DesignTokens.Ink else DesignTokens.Secondary)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Border))
    }
}

/** First load: banner, identity, tabs and the first rows in outline. */
@Composable
private fun ChannelSkeleton(width: Float) {
    val inset = ChannelMetrics.inset(width)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("channel-skeleton").clearAndSetSemantics {}) {
        SkeletonBox(Modifier.padding(start = inset, end = inset, top = MinSpace.sm).fillMaxWidth()
            .aspectRatio(ChannelMetrics.bannerAspect(width)), MinRadius.Card)
        Row(Modifier.fillMaxWidth().padding(start = inset, end = inset, top = MinSpace.lg)) {
            SkeletonBox(Modifier.size(ChannelMetrics.avatar(width)), CircleShape)
            Spacer(Modifier.width(ChannelMetrics.AvatarGap))
            Column(Modifier.weight(1f).padding(top = MinSpace.xs)) {
                SkeletonLine(fraction = 0.6f, height = 24.dp)
                Spacer(Modifier.height(MinSpace.md))
                SkeletonLine(fraction = 0.85f)
                Spacer(Modifier.height(MinSpace.sm))
                SkeletonLine(fraction = 0.5f, height = 12.dp)
            }
        }
        Row(Modifier.padding(start = inset, end = inset, top = MinSpace.xxl, bottom = MinSpace.lg),
            horizontalArrangement = Arrangement.spacedBy(MinSpace.xl)) {
            repeat(4) { SkeletonBox(Modifier.size(56.dp, 16.dp), CircleShape) }
        }
        ChannelVideoPlaceholders(width, count = 3) { "skeleton-thumb-$it" }
    }
}

@Composable
private fun ChannelFailed(onAction: (ChannelAction) -> Unit) {
    StatePage {
        StateMessage(
            stringResource(R.string.ui_something_went_wrong), message = stringResource(R.string.ui_please_try_again),
            kind = StateKind.Error, actionLabel = stringResource(R.string.retry),
            onAction = { onAction(ChannelAction.Retry) }, tag = "channel-error",
        )
    }
}
