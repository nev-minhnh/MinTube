@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.hhst.youtubelite.watch.presentation

import com.hhst.youtubelite.ui.design.reasonResources
import com.hhst.youtubelite.ui.design.labelResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.playlist.data.FixturePlaylistRepository
import com.hhst.youtubelite.feedback.presentation.FeedbackReason
import com.hhst.youtubelite.feedback.presentation.FeedbackSheet
import com.hhst.youtubelite.saves.domain.NewPlaylistDraft
import com.hhst.youtubelite.saves.domain.SavePlaylist
import com.hhst.youtubelite.saves.presentation.NewPlaylistSheet
import com.hhst.youtubelite.saves.presentation.SaveSheetLive
import com.hhst.youtubelite.saves.presentation.SaveToPlaylistSheet
import com.hhst.youtubelite.ui.design.*
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.watch.domain.*
import com.hhst.youtubelite.ui.design.appDescription
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.dearrow.VideoArtwork

@Composable
fun WatchScreen(
    state: WatchUiState,
    onAction: (WatchAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    playerSlot: @Composable (Modifier) -> Unit = { WatchPlayer(it, onBack) },
    menuContent: @Composable () -> Unit = { WatchVideoMenu(state, onAction) },
    sidebarCovered: Boolean = false,
    drawBackground: Boolean = true,
    /** An overlay host still needs the embedded destination measured after rotation. */
    keepPlayerSlotInFullscreen: Boolean = false,
    /** Copies the video link through the host's share gateway; the pill hides when the host has none. */
    onCopyLink: ((String) -> Unit)? = null,
) {
    val showPlayer = !fullscreen || keepPlayerSlotInFullscreen
    // One saved scroll state per column and layout, keyed by video: a new video starts at the
    // top, and each column keeps its own place across recomposition, rotation and returning
    // from another screen. The two columns never share a scroll or nested-scroll connection.
    val mainScroll = rememberSaveable(state.videoId, saver = LazyListState.Saver) { LazyListState() }
    val sidebarMainScroll = rememberSaveable(state.videoId, saver = LazyListState.Saver) { LazyListState() }
    val relatedScroll = rememberSaveable(state.videoId, saver = LazyListState.Saver) { LazyListState() }
    // Chip selection is presentation state; a new video starts on "All".
    var fromChannel by rememberSaveable(state.videoId) { mutableStateOf(false) }
    val page = (state.content as? WatchContent.Ready)?.page
    val filter = page?.let { RelatedFilter.of(it, fromChannel) }
    val smallestWidth = watchSmallestWidth()
    AppTheme {
        Box(modifier.fillMaxSize().then(if (drawBackground) Modifier.background(DesignTokens.Background) else Modifier).testTag("watch-screen")) {
            BoxWithConstraints(
                Modifier.fillMaxSize().then(
                    if (state.overlay is WatchOverlay.ItemMenu || state.overlay is WatchOverlay.Save ||
                        state.overlay is WatchOverlay.Feedback) Modifier.clearAndSetSemantics {} else Modifier,
                ),
            ) {
                val width = maxWidth.value
                val height = maxHeight.value
                val sidebarMode = WatchLayout.sidebar(width, height, smallestWidth)
                val columnWidth = WatchLayout.columnWidth(width, height, smallestWidth)
                val playerHeight = WatchLayout.playerHeight(width, height, smallestWidth)
                // The player is pinned at the top of its column in every layout; the page scrolls
                // below it, never with it.
                val pinnedSlot: @Composable (Modifier) -> Unit = { slotModifier ->
                    if (showPlayer) playerSlot(slotModifier.testTag("watch-player-slot")) else Spacer(slotModifier)
                }

                if (sidebarMode) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            pinnedSlot(Modifier.padding(top = WatchLayout.TopBarHeight.dp).fillMaxWidth().height(playerHeight.dp))
                            WatchMainBody(
                                state = state,
                                scroll = sidebarMainScroll,
                                viewportWidth = width,
                                viewportHeight = height,
                                smallestWidth = smallestWidth,
                                filter = null,
                                onFilter = {},
                                onAction = onAction,
                                onCopyLink = onCopyLink,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Box(if (sidebarCovered) Modifier.clearAndSetSemantics {} else Modifier) {
                            WatchSidebarPane(state, width, relatedScroll, filter, { fromChannel = it }, onAction)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        pinnedSlot(Modifier.padding(top = WatchLayout.TopBarHeight.dp).width(columnWidth.dp).height(playerHeight.dp))
                        WatchMainBody(
                            state = state,
                            scroll = mainScroll,
                            viewportWidth = width,
                            viewportHeight = height,
                            smallestWidth = smallestWidth,
                            filter = filter,
                            onFilter = { fromChannel = it },
                            onAction = onAction,
                            onCopyLink = onCopyLink,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            menuContent()
        }
    }
}

@Composable
internal fun WatchVideoMenu(state: WatchUiState, onAction: (WatchAction) -> Unit) {
    when (val overlay = state.overlay) {
        is WatchOverlay.ItemMenu -> MenuBottomSheet(
            items = watchVideoMenuActions(state.live, (state.content as? WatchContent.Ready)?.page?.related?.firstOrNull { it.id == overlay.videoId }).map {
                MenuSheetItem(it.icon(), stringResource(it.labelResource), "watch-menu-${it.name}")
            },
            onDismiss = { onAction(WatchAction.CloseOverlay) },
            onSelect = { index -> onAction(WatchAction.MenuAction(watchVideoMenuActions(state.live, (state.content as? WatchContent.Ready)?.page?.related?.firstOrNull { it.id == overlay.videoId })[index])) },
            tag = "watch-video-menu",
            paneTitle = stringResource(R.string.ui_video_actions),
            fillWatchHeight = false,
            header = if (state.menuLoading || state.menuFailed) ({
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    if (state.menuLoading) Box(Modifier.testTag("watch-menu-loading")) { com.hhst.youtubelite.home.presentation.LoadingSpinner() }
                    else MinButton(stringResource(R.string.retry), { onAction(WatchAction.RetryMenu) },
                        kind = MinButtonKind.Text, testTag = "watch-menu-retry")
                }
            }) else null,
        )
        is WatchOverlay.Save -> if (overlay.newPlaylist) NewPlaylistSheet(
            draft = NewPlaylistDraft(overlay.title, overlay.description),
            onDismiss = { onAction(WatchAction.CloseOverlay) },
            onDraftChange = { onAction(WatchAction.SaveDraftChanged(it.title, it.description)) },
            onSubmit = { onAction(WatchAction.SubmitNewPlaylist) },
            pending = overlay.pending,
        ) else if (overlay.live) SaveToPlaylistSheet(
            playlists = emptyList(),
            onDismiss = { onAction(WatchAction.CloseOverlay) },
            onSelect = {},
            onNewPlaylist = { onAction(WatchAction.OpenNewPlaylist) },
            live = SaveSheetLive(
                choices = overlay.choices,
                failed = overlay.choicesFailed,
                pending = overlay.pending,
                onToggle = { onAction(WatchAction.SaveChoiceToggled(it)) },
                onRetry = { onAction(WatchAction.SaveChoicesRetry) },
            ),
        ) else SaveToPlaylistSheet(
            playlists = savePlaylists,
            onDismiss = { onAction(WatchAction.CloseOverlay) },
            onSelect = { onAction(WatchAction.SaveDestinationSelected(it)) },
            onNewPlaylist = { onAction(WatchAction.OpenNewPlaylist) },
        )
        is WatchOverlay.Feedback -> FeedbackSheet(
            title = stringResource(overlay.kind.labelResource),
            reasons = overlay.kind.reasons.mapIndexed { index, reason ->
                FeedbackReason(reason, "feedback-reason-$index", overlay.kind.reasonResources[index])
            },
            onDismiss = { onAction(WatchAction.CloseOverlay) },
            onSelect = { onAction(WatchAction.FeedbackReason(it.label)) },
        )
        else -> Unit
    }
}

private val savePlaylists: List<SavePlaylist> =
    FixturePlaylistRepository.personalLists()
        .filter { it.id != FixturePlaylistRepository.WATCH_LATER }
        .map { SavePlaylist(it.id, it.title) }

private fun WatchVideoMenuAction.icon(): AppIcon = when (this) {
    WatchVideoMenuAction.Queue -> AppIcon.QueueAdd
    WatchVideoMenuAction.Download -> AppIcon.Download
    WatchVideoMenuAction.WatchLater -> AppIcon.Clock
    WatchVideoMenuAction.SaveToPlaylist -> AppIcon.Bookmark
    WatchVideoMenuAction.Share -> AppIcon.Share
    WatchVideoMenuAction.NotInterested -> AppIcon.Block
    WatchVideoMenuAction.DontRecommend -> AppIcon.Close
}
/**
 * The scrolling page below the pinned player. In the single-column layout it also carries the
 * related feed, whose chip row sticks directly under the player while info and feed scroll.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WatchMainBody(
    state: WatchUiState,
    scroll: LazyListState,
    viewportWidth: Float,
    viewportHeight: Float,
    smallestWidth: Float,
    filter: RelatedFilter?,
    onFilter: (Boolean) -> Unit,
    onAction: (WatchAction) -> Unit,
    onCopyLink: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val includeRelated = !WatchLayout.sidebar(viewportWidth, viewportHeight, smallestWidth)
    val filtered = includeRelated && filter?.fromChannel == true
    // "From <channel>" keeps the loaded items and pauses paging; "All" pages on as before.
    if (includeRelated && !filtered) RelatedPaging(state, scroll, onAction)
    // The slot geometry stays on WatchLayout (the overlay player and the panels read it);
    // only the feed below it follows the Mint window classes.
    val listRows = WatchLayout.relatedVariant(viewportWidth, viewportHeight, smallestWidth) == WatchRelatedVariant.List
    val window = WindowClass.of(viewportWidth.toInt())
    val columns = if (listRows) 1 else window.feedColumns
    val inset = MinSpace.pageInset(window)
    val related = if (includeRelated) filter?.visible.orEmpty() else emptyList()
    val relatedRows = remember(related, columns) { related.chunked(columns) }
    Box(modifier.fillMaxWidth().clipToBounds()) {
    LazyColumn(Modifier.fillMaxSize().testTag("watch-scroll"), state = scroll) {
        when (val content = state.content) {
            WatchContent.Loading -> item("loading") {
                val known = state.provisional
                if (known == null) WatchSkeleton(inset) else WatchProvisionalHeader(known, inset)
                if (includeRelated) RelatedSkeleton(columns, listRows, inset)
            }
            WatchContent.Failed -> item("error") {
                if (state.verification != null) VerificationCard(state.verification,
                    onVerify = { onAction(WatchAction.OpenVerification) },
                    onDismiss = { onAction(WatchAction.DismissVerification) },
                    modifier = Modifier.testTag("watch-verification")) else WatchError(onAction)
            }
            is WatchContent.Ready -> {
                item("metadata") { WatchMetadata(content.page.video, state, inset, onAction, onCopyLink) }
                item("comments") { WatchComments(content.page.video, inset, onAction) }
                if (includeRelated) {
                    if (filter != null && content.page.related.isNotEmpty()) stickyHeader("related-chips", contentType = "chips") {
                        RelatedChipRow(filter, inset, onFilter)
                    }
                    item("related-start") { RelatedHeader(inset) }
                    if (filtered) item("related-filter-hint") { RelatedFilterHint(inset) }
                    if (related.isEmpty() && content.page.nextKey == null) item("related-empty") { RelatedEmpty() }
                    items(relatedRows, key = { row -> "related:${row.first().id}" }, contentType = { "related-row" }) { row ->
                        RelatedFeedRow(row, columns, listRows, inset, onAction)
                    }
                    item("pagination") { if (filtered) Spacer(Modifier.height(MinSpace.lg)) else RelatedFooter(state, onAction) }
                }
            }
        }
    }
    }
}

/** `smallestScreenWidthDp` for the tablet rule of [WatchLayout]; every watch-page layer reads this same value. */
@Composable
fun watchSmallestWidth(): Float = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp.toFloat()

@Composable
internal fun RelatedPaging(state: WatchUiState, scroll: LazyListState, onAction: (WatchAction) -> Unit) {
    val latestAction by rememberUpdatedState(onAction)
    val cursor = (state.content as? WatchContent.Ready)?.page?.nextKey
    LaunchedEffect(scroll, cursor, state.relatedFailed) {
        if (cursor == null || state.relatedFailed) return@LaunchedEffect
        snapshotFlow { scroll.layoutInfo.let { it.totalItemsCount > 0 &&
            (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 3 } }
            .collect { if (it) latestAction(WatchAction.LoadRelated) }
    }
}

@Composable
internal fun RelatedFooter(state: WatchUiState, onAction: (WatchAction) -> Unit) {
    if (state.relatedLoading) Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("watch-related-loading"), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), color = DesignTokens.Accent,
            trackColor = DesignTokens.Ghost, strokeWidth = 2.5.dp)
    }
    if (state.relatedFailed) Column(Modifier.fillMaxWidth().padding(vertical = MinSpace.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(stringResource(R.string.ui_please_try_again), MinType.Caption, color = DesignTokens.Secondary)
        MinButton(stringResource(R.string.retry), { onAction(WatchAction.LoadRelated) },
            kind = MinButtonKind.Tonal, icon = AppIcon.Refresh, testTag = "watch-related-retry")
    }
    Spacer(Modifier.height(MinSpace.lg))
}
