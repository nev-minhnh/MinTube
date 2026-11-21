package com.hhst.youtubelite.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.hhst.youtubelite.account.domain.AccountMenuAction
import com.hhst.youtubelite.library.domain.AccountProfile
import com.hhst.youtubelite.library.domain.LibraryAction
import com.hhst.youtubelite.library.domain.LibraryContent
import com.hhst.youtubelite.library.domain.LibraryOverlay
import com.hhst.youtubelite.library.domain.LibraryRow
import com.hhst.youtubelite.library.domain.LibraryUiState
import com.hhst.youtubelite.ui.components.MainNavigationBar
import com.hhst.youtubelite.ui.components.MinTopBar
import com.hhst.youtubelite.ui.components.SectionHeader
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.CollapsingAppBar
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.RefreshableContent
import com.hhst.youtubelite.ui.design.VerificationCard
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.rememberAppBarCollapseState
import com.hhst.youtubelite.ui.design.rememberWindowClass

/**
 * The You tab: account header, entry tiles, recent-history and playlists shelves, then Settings
 * and About. [privateBrowsing] is the host's reading of the preference; the screen only draws it.
 */
@Composable
fun YouScreen(state: LibraryUiState, onAction: (LibraryAction) -> Unit, modifier: Modifier = Modifier,
              privateBrowsing: Boolean = false) {
    val appBar = rememberAppBarCollapseState(enabled = state.overlay == LibraryOverlay.None && state.content is LibraryContent.Ready)
    val window = rememberWindowClass()
    Box(modifier.fillMaxSize().background(DesignTokens.Background).testTag("you-screen").nestedScroll(appBar.connection)) {
        val listState = rememberLibraryListState()
        Column(Modifier.fillMaxSize().then(if (state.overlay != LibraryOverlay.None) Modifier.clearAndSetSemantics {} else Modifier)) {
            CollapsingAppBar(appBar) {
                // The host reads the preference on resume; the live listener catches a change made while You is open.
                YouTopBar(state.signedIn, privateBrowsing || com.hhst.youtubelite.account.presentation.rememberPrivateBrowsing(), onAction)
            }
            RefreshableContent(value = state, refreshing = state.content == LibraryContent.Loading,
                    onRefresh = { onAction(LibraryAction.Retry) }, modifier = Modifier.weight(1f).fillMaxWidth(),
                    enabled = state.overlay == LibraryOverlay.None && state.content !is LibraryContent.SignedOut, contentKey = state.signedIn, tag = "you-refresh",
                    canPull = { !listState.canScrollBackward }) { state ->
                when (val content = state.content) {
                    LibraryContent.Loading -> LibrarySkeleton("you-skeleton", window)
                    LibraryContent.Failed -> if (state.verification != null) VerificationCard(
                        state.verification,
                        onVerify = { onAction(LibraryAction.OpenVerification(state.verification!!.challengeUrl)) },
                        onDismiss = { onAction(LibraryAction.DismissVerification) },
                        modifier = Modifier.testTag("you-verification"),
                    ) else LibraryError("you-error") { onAction(LibraryAction.Retry) }
                    is LibraryContent.SignedOut -> LibraryStateMessage(
                        content.prompt.title, content.prompt.message, "you-signed-out", icon = AppIcon.Person,
                        actionLabel = content.prompt.action, onAction = { onAction(LibraryAction.SignIn) },
                    )
                    is LibraryContent.Ready -> LibraryList(listState, content, window, state.signedIn,
                        state.historyRetrying, state.playlistsRetrying, onAction)
                }
            }
            MainNavigationBar("main", state.signedIn, accountAvatar(state)) { tab ->
                onAction(LibraryAction.Destination(tab))
            }
        }
        state.undo?.let { removed ->
            HistoryUndoBanner(removed,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = MinSpace.lg, vertical = MinSpace.md),
                onUndo = { onAction(LibraryAction.UndoHistoryRemove) },
                onDismiss = { onAction(LibraryAction.DismissUndo) })
        }
    }
}

private fun accountAvatar(state: LibraryUiState): String? =
    (state.content as? LibraryContent.Ready)?.page?.account?.avatar

@Composable
private fun rememberLibraryListState(): LazyListState =
    rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

@Composable
private fun YouTopBar(signedIn: Boolean, privateBrowsing: Boolean, onAction: (LibraryAction) -> Unit) {
    MinTopBar(privateBrowsing = privateBrowsing, tag = "you-topbar") {
        AppIconButton(AppIcon.Search, stringResource(R.string.a11y_search_youtube)) { onAction(LibraryAction.OpenSearch) }
        if (!signedIn) AppIconButton(AppIcon.More, stringResource(R.string.a11y_more)) { onAction(LibraryAction.OpenAccountMenu) }
    }
}

@Composable
private fun LibraryList(listState: LazyListState, content: LibraryContent.Ready, window: WindowClass, signedIn: Boolean,
                        historyRetrying: Boolean, playlistsRetrying: Boolean,
                        onAction: (LibraryAction) -> Unit) {
    val page = content.page
    val inset = MinSpace.pageInset(window)
    val cardWidth = if (window == WindowClass.Compact) 160.dp else 208.dp
    LazyColumn(Modifier.fillMaxSize().testTag("you-library"), state = listState,
        contentPadding = PaddingValues(top = MinSpace.sm, bottom = MinSpace.xxl)) {
        item(key = "account") { AccountHeader(page.account, inset) { onAction(LibraryAction.OpenAccountMenu) } }
        item(key = "rows") {
            // Liked has no device lane, so its tile exists only for a signed-in account.
            val tiles = buildList<Triple<LibraryRow, AppIcon, LibraryAction>> {
                add(Triple(LibraryRow.History, AppIcon.History, LibraryAction.OpenHistory))
                add(Triple(LibraryRow.Playlists, AppIcon.Playlist, LibraryAction.OpenPlaylists))
                add(Triple(LibraryRow.WatchLater, AppIcon.Clock, LibraryAction.OpenWatchLater))
                if (signedIn) add(Triple(LibraryRow.LikedVideos, AppIcon.Like, LibraryAction.OpenLiked))
                add(Triple(LibraryRow.Queue, AppIcon.Queue, LibraryAction.OpenQueue))
                add(Triple(LibraryRow.Downloads, AppIcon.Download, LibraryAction.OpenDownloads))
            }
            val columns = libraryTileColumns(window, androidx.compose.ui.platform.LocalDensity.current.fontScale)
            Column(Modifier.fillMaxWidth().padding(horizontal = inset, vertical = MinSpace.sm),
                verticalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
                tiles.chunked(columns).forEach { line ->
                    // Tiles of one line share a height when a label wraps (large font scales).
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
                        line.forEach { (row, icon, action) ->
                            LibraryRowItem(row, icon, Modifier.weight(1f).fillMaxHeight()) { onAction(action) }
                        }
                        repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        when {
            page.historyFailed -> item(key = "history-failed") {
                ShelfRetry(stringResource(R.string.ui_couldn_t_load_history), historyRetrying,
                    Modifier.padding(horizontal = inset, vertical = MinSpace.sm)) { onAction(LibraryAction.RetryHistoryShelf) }
            }
            page.history.isNotEmpty() -> {
                item(key = "history-header") {
                    SectionHeader(stringResource(R.string.download_history), Modifier.padding(start = inset, end = MinSpace.xs),
                        actionLabel = stringResource(R.string.ui_view_all), onAction = { onAction(LibraryAction.OpenHistory) })
                }
                item(key = "history-shelf") { HistoryShelf(page.history, inset, cardWidth) { onAction(LibraryAction.OpenVideo(it)) } }
            }
        }
        when {
            page.playlistsFailed -> item(key = "playlists-failed") {
                ShelfRetry(stringResource(R.string.ui_couldn_t_load_playlists), playlistsRetrying,
                    Modifier.padding(horizontal = inset, vertical = MinSpace.sm)) { onAction(LibraryAction.RetryPlaylistsShelf) }
            }
            page.playlists.isNotEmpty() -> {
                item(key = "playlists-header") {
                    SectionHeader(stringResource(R.string.ui_playlists), Modifier.padding(start = inset, end = MinSpace.xs, top = MinSpace.sm),
                        actionLabel = stringResource(R.string.ui_view_all), onAction = { onAction(LibraryAction.OpenPlaylists) })
                }
                item(key = "playlists-shelf") { PlaylistShelf(page.playlists, inset, cardWidth) { onAction(LibraryAction.OpenPlaylist(it)) } }
            }
        }
        // The same Settings and About entries as the account menu, reachable without opening it.
        item(key = "links") {
            Column(Modifier.fillMaxWidth().padding(horizontal = inset).padding(top = MinSpace.lg)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Border))
                LibraryLinkRow(stringResource(AccountMenuAction.Settings.labelResource), AppIcon.Settings, "you-link-settings") {
                    onAction(LibraryAction.AccountSelected(AccountMenuAction.Settings))
                }
                LibraryLinkRow(stringResource(AccountMenuAction.About.labelResource), AppIcon.Info, "you-link-about") {
                    onAction(LibraryAction.AccountSelected(AccountMenuAction.About))
                }
            }
        }
    }
}

/** Avatar, name and handle (or "On this device"); opens the account menu the host owns. */
@Composable
private fun AccountHeader(account: AccountProfile, inset: androidx.compose.ui.unit.Dp, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen)
        .testTag("you-account").heightIn(min = 72.dp).padding(horizontal = inset, vertical = MinSpace.md),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(DesignTokens.AccentContainer), contentAlignment = Alignment.Center) {
            if (account.avatar.isBlank()) AppIcon(AppIcon.Person, Modifier.size(28.dp), color = DesignTokens.AccentText)
            else LibraryArtwork(account.avatar, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(MinSpace.lg))
        Column(Modifier.weight(1f)) {
            AppText(account.name, MinType.Title, maxLines = 2)
            if (account.handle.isNotBlank()) AppText(account.handle, MinType.Body, color = DesignTokens.Secondary, maxLines = 1)
        }
        AppIcon(AppIcon.ChevronRight, Modifier.padding(start = MinSpace.sm).size(20.dp), color = DesignTokens.Secondary)
    }
}
