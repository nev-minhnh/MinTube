package com.hhst.youtubelite.playlist.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.playlist.domain.PlaylistAction
import com.hhst.youtubelite.playlist.domain.PlaylistContent
import com.hhst.youtubelite.playlist.domain.PlaylistDestination
import com.hhst.youtubelite.playlist.domain.PlaylistLayout
import com.hhst.youtubelite.playlist.domain.PlaylistOverlay
import com.hhst.youtubelite.playlist.domain.PlaylistPage
import com.hhst.youtubelite.playlist.domain.PlaylistUiState
import com.hhst.youtubelite.playlist.domain.PlaylistVideoAction
import com.hhst.youtubelite.ui.components.MinTopBar
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.components.VideoRowSkeleton
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.AppTheme
import com.hhst.youtubelite.ui.design.BrowseNavigationBar
import com.hhst.youtubelite.ui.design.CollapsingAppBar
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MenuBottomSheet
import com.hhst.youtubelite.ui.design.MenuSheetItem
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.RefreshableContent
import com.hhst.youtubelite.ui.design.SearchPanel
import com.hhst.youtubelite.ui.design.VerificationBanner
import com.hhst.youtubelite.ui.design.VerificationCard
import com.hhst.youtubelite.ui.design.appDescription
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.rememberAppBarCollapseState

/** Page inset of the playlist screen; the cover and the rows share it at every width. */
internal val PlaylistInset = MinSpace.lg

@Composable
fun PlaylistScreen(state: PlaylistUiState, onAction: (PlaylistAction) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val appBar = rememberAppBarCollapseState(state.playlistId, enabled = state.overlay == PlaylistOverlay.None && state.content is PlaylistContent.Ready)
    AppTheme {
        BoxWithConstraints(modifier.fillMaxSize().nestedScroll(appBar.connection).background(DesignTokens.Background).testTag("playlist-page")) {
            val width = maxWidth.value
            val viewport = rememberUpdatedState(width)
            val listState = rememberLazyListState()
            val headerScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
            val loadedPage = (state.content as? PlaylistContent.Ready)?.page
            var lastPage by remember(state.playlistId) { mutableStateOf(loadedPage) }
            SideEffect { if (state.content != PlaylistContent.Loading) lastPage = loadedPage }
            val page = loadedPage ?: lastPage.takeIf { state.content == PlaylistContent.Loading }
            Column(Modifier.fillMaxSize().then(if (state.overlay != PlaylistOverlay.None) Modifier.clearAndSetSemantics {} else Modifier)) {
                CollapsingAppBar(appBar) {
                    // The title joins the bar once the header has scrolled away.
                    MinTopBar(title = if (headerScrolled) page?.summary?.title.orEmpty() else "", onBack = onBack, tag = "playlist-toolbar") {
                        AppIconButton(AppIcon.Search, stringResource(R.string.a11y_search)) { onAction(PlaylistAction.OpenSearch) }
                        AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_options)) { onAction(PlaylistAction.OpenMenu) }
                    }
                }
                RefreshableContent(value = state, refreshing = state.content == PlaylistContent.Loading,
                    onRefresh = { onAction(PlaylistAction.Retry) }, modifier = Modifier.weight(1f).fillMaxWidth(),
                    enabled = state.overlay == PlaylistOverlay.None, contentKey = state.playlistId, tag = "playlist-refresh",
                    canPull = { !listState.canScrollBackward }) { state ->
                    when (val content = state.content) {
                        PlaylistContent.Loading -> PlaylistSkeleton(width)
                        PlaylistContent.Unavailable -> PlaylistMessage("playlist-unavailable") {
                            StateMessage(stringResource(R.string.ui_this_playlist_isn_t_available), icon = AppIcon.Playlist, tag = "playlist-unavailable-message")
                        }
                        PlaylistContent.Failed -> if (state.verification != null) VerificationCard(
                            state.verification,
                            onVerify = { onAction(PlaylistAction.OpenVerification(state.verification!!.challengeUrl)) },
                            onDismiss = { onAction(PlaylistAction.DismissVerification) },
                            modifier = Modifier.testTag("playlist-verification"),
                        ) else PlaylistMessage("playlist-error") {
                            StateMessage(stringResource(R.string.ui_something_went_wrong), message = stringResource(R.string.ui_please_try_again),
                                kind = StateKind.Error, actionLabel = stringResource(R.string.retry),
                                onAction = { onAction(PlaylistAction.Retry) }, tag = "playlist-error-message")
                        }
                        is PlaylistContent.Ready -> {
                            val emptyList = content.page.entries.isEmpty()
                            val nearEnd by remember(content) { derivedStateOf {
                                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= content.page.entries.size - 2
                            } }
                            LaunchedEffect(nearEnd, content.page.nextKey, state.append) {
                                if (nearEnd && content.page.nextKey != null && state.append == AppendState.Idle) {
                                    onAction(PlaylistAction.LoadNext)
                                }
                            }
                            LazyColumn(Modifier.fillMaxSize().testTag("playlist-list"), state = listState,
                                contentPadding = PaddingValues(bottom = MinSpace.lg)) {
                                item(key = "header") {
                                    val w = viewport.value
                                    // A device-stored list that is still empty has no artwork to show.
                                    if (content.page.summary.local && emptyList) PlaylistCompactHeader(content.page)
                                    else PlaylistHero(content.page, w, PlaylistLayout.sideBySide(w), state.descriptionExpanded, onAction)
                                }
                                if (emptyList && content.page.nextKey == null) item {
                                    // Formal empty state: an honest message, no fake cover or author.
                                    StateMessage(stringResource(R.string.ui_no_videos_in_this_playlist_yet),
                                        message = stringResource(R.string.ui_videos_you_add_will_appear_here),
                                        icon = AppIcon.Playlist, tag = "playlist-empty")
                                }
                                items(content.page.entries, key = { it.id }, contentType = { "video" }) { entry -> PlaylistVideoRow(entry, onAction) }
                                item(key = "append") {
                                    PlaylistAppendRow(state, onAction)
                                }
                            }
                            state.verification?.let { challenge ->
                                VerificationBanner(challenge,
                                    onVerify = { onAction(PlaylistAction.OpenVerification(challenge.challengeUrl)) },
                                    onDismiss = { onAction(PlaylistAction.DismissVerification) },
                                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = MinSpace.md, vertical = MinSpace.sm)
                                        .testTag("playlist-verification-banner"))
                            }
                        }
                    }
                }
                PlaylistNavigation(state.signedIn, onAction)
            }
            when (state.overlay) {
                PlaylistOverlay.None -> Unit
                PlaylistOverlay.Search -> SearchPanel(state.query, if (state.query.isBlank()) emptyList() else listOf(state.query),
                    { onAction(PlaylistAction.QueryChanged(it)) }, { onAction(PlaylistAction.Search(it)) },
                    { onAction(PlaylistAction.CloseOverlay) }, { onAction(PlaylistAction.VoiceSearch) })
                PlaylistOverlay.Menu -> MenuBottomSheet(
                    listOf(MenuSheetItem(AppIcon.Shuffle, stringResource(R.string.playback_end_shuffle), "playlist-shuffle"),
                        MenuSheetItem(AppIcon.Download, stringResource(R.string.download), "playlist-download")),
                    { onAction(PlaylistAction.CloseOverlay) }, { onAction(if (it == 0) PlaylistAction.Shuffle else PlaylistAction.Download) },
                    tag = "playlist-menu", paneTitle = stringResource(R.string.ui_playlist_actions))
                is PlaylistOverlay.VideoMenu -> MenuBottomSheet(
                    PlaylistVideoAction.entries.map { MenuSheetItem(when (it) {
                        PlaylistVideoAction.WatchLater -> AppIcon.Clock
                        PlaylistVideoAction.Save -> AppIcon.Bookmark
                    }, stringResource(it.labelResource), "playlist-video-action-${it.name}") },
                    { onAction(PlaylistAction.CloseOverlay) }, { onAction(PlaylistAction.VideoMenuAction(PlaylistVideoAction.entries[it])) },
                    tag = "playlist-video-menu", paneTitle = stringResource(R.string.ui_video_actions))
            }
        }
    }
}

/** Artwork, title, owner, count and the visible Play all / Shuffle actions. Side by side from 840dp. */
@Composable
private fun PlaylistHero(
    page: PlaylistPage,
    width: Float,
    wide: Boolean,
    descriptionExpanded: Boolean,
    onAction: (PlaylistAction) -> Unit,
) {
    val cover = Modifier.clip(MinRadius.Card).background(DesignTokens.Surface)
        .clickable(role = Role.Button) { onAction(PlaylistAction.PlayAll) }
        .appDescription(R.string.a11y_play_title, page.summary.title).testTag("playlist-hero-cover")
    Box(Modifier.fillMaxWidth().testTag("playlist-hero")) {
        if (wide) {
            Row(Modifier.padding(PlaylistInset), verticalAlignment = Alignment.CenterVertically) {
                PlaylistArtwork(page.summary.artwork, Modifier.size(PlaylistLayout.coverWidth(width).dp, PlaylistLayout.coverHeight(width).dp).then(cover))
                Spacer(Modifier.width(MinSpace.xxl))
                Column(Modifier.weight(1f)) {
                    PlaylistIdentity(page, descriptionExpanded, onAction)
                    PlaylistButtons(onAction, stretch = false, local = page.summary.local, empty = page.entries.isEmpty())
                }
            }
        } else {
            Column(Modifier.padding(PlaylistInset)) {
                PlaylistArtwork(page.summary.artwork, Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(cover))
                Spacer(Modifier.height(MinSpace.lg))
                PlaylistIdentity(page, descriptionExpanded, onAction)
                PlaylistButtons(onAction, stretch = width < 600f, local = page.summary.local, empty = page.entries.isEmpty())
            }
        }
    }
}

@Composable
private fun PlaylistCompactHeader(page: PlaylistPage) {
    Column(Modifier.fillMaxWidth().padding(PlaylistInset).testTag("playlist-hero-local")) {
        AppText(page.summary.title, MinType.Display, Modifier.testTag("playlist-heading"))
        page.summary.countLabel?.let {
            AppText(it, MinType.Caption, Modifier.padding(top = MinSpace.xs), color = DesignTokens.Secondary)
        }
    }
}

@Composable
private fun PlaylistIdentity(page: PlaylistPage, descriptionExpanded: Boolean, onAction: (PlaylistAction) -> Unit) {
    AppText(page.summary.title, MinType.Display, Modifier.testTag("playlist-heading"), maxLines = 3)
    if (page.summary.owner.isNotBlank()) PlaylistOwner(page.summary.owner, page.summary.artwork, page.summary.local) {
        onAction(PlaylistAction.OpenOwner)
    }
    val metadata = listOfNotNull(stringResource(R.string.ui_playlist), page.summary.countLabel,
        page.summary.views.takeIf(String::isNotBlank)).joinToString(" · ")
    AppText(metadata, MinType.Caption, Modifier.padding(top = MinSpace.xs), color = DesignTokens.Secondary, maxLines = 2)
    if (page.description.isNotBlank()) AppText(page.description,
        MinType.Body, Modifier.padding(top = MinSpace.sm).animateContentSize(tween(180))
            .clickable(role = Role.Button) { onAction(PlaylistAction.ToggleDescription) }
            .appDescription(if (descriptionExpanded) R.string.ui_show_less_description else R.string.ui_show_full_description)
            .testTag("playlist-description"),
        color = DesignTokens.Secondary, maxLines = if (descriptionExpanded) Int.MAX_VALUE else 2)
}

@Composable
private fun PlaylistNavigation(signedIn: Boolean, onAction: (PlaylistAction) -> Unit) {
    BrowseNavigationBar("playlist", signedIn) { destination ->
        onAction(PlaylistAction.Destination(PlaylistDestination.valueOf(destination.name)))
    }
}

/** Scrolling full-page host for the unavailable and error states; [tag] names the state. */
@Composable
private fun PlaylistMessage(tag: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        content()
    }
}

@Composable
private fun PlaylistSkeleton(width: Float) {
    val wide = PlaylistLayout.sideBySide(width)
    val coverWidth = if (wide) PlaylistLayout.coverWidth(width) else width - PlaylistInset.value * 2
    val coverHeight = if (wide) PlaylistLayout.coverHeight(width) else coverWidth * 9f / 16f
    val cover: @Composable () -> Unit = {
        SkeletonBox(Modifier.size(coverWidth.dp, coverHeight.dp).testTag("playlist-loading-cover"), MinRadius.Card)
    }
    val details: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(MinSpace.md)) {
            SkeletonLine(fraction = 0.8f, height = 24.dp)
            SkeletonLine(fraction = 0.55f, height = 12.dp)
            SkeletonLine(fraction = 0.7f, height = 12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
                SkeletonBox(Modifier.size(120.dp, 40.dp), MinRadius.Full)
                SkeletonBox(Modifier.size(120.dp, 40.dp), MinRadius.Full)
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("playlist-loading").clearAndSetSemantics {}) {
        Box(Modifier.fillMaxWidth().padding(PlaylistInset)) {
            if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                cover()
                Spacer(Modifier.width(MinSpace.xxl))
                Box(Modifier.weight(1f)) { details() }
            } else Column {
                cover()
                Spacer(Modifier.height(MinSpace.lg))
                details()
            }
        }
        repeat(4) { VideoRowSkeleton(Modifier.padding(horizontal = PlaylistInset, vertical = MinSpace.sm)) }
    }
}
