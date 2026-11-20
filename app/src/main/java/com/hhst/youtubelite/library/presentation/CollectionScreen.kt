package com.hhst.youtubelite.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.library.domain.CollectionContent
import com.hhst.youtubelite.library.domain.CollectionUiState
import com.hhst.youtubelite.library.domain.LibraryAction
import com.hhst.youtubelite.library.domain.LibraryOverlay
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.CollapsingAppBar
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.RefreshableContent
import com.hhst.youtubelite.ui.design.VerificationCard
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.rememberAppBarCollapseState
import com.hhst.youtubelite.ui.design.rememberWindowClass

/** The Playlists list. */
@Composable
fun CollectionScreen(state: CollectionUiState,
                     onAction: (LibraryAction) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val appBar = rememberAppBarCollapseState(enabled = state.overlay == LibraryOverlay.None && state.content is CollectionContent.Ready)
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val window = rememberWindowClass()
    Column(modifier.fillMaxSize().background(DesignTokens.Background).testTag("playlists-screen").nestedScroll(appBar.connection)) {
        CollapsingAppBar(appBar) {
            LibrarySubPageBar(stringResource(R.string.ui_playlists), onBack, onAction)
        }
        RefreshableContent(value = state, refreshing = state.content == CollectionContent.Loading,
                onRefresh = { onAction(LibraryAction.Retry) }, modifier = Modifier.weight(1f).fillMaxWidth(),
                enabled = state.overlay == LibraryOverlay.None && state.content !is CollectionContent.SignedOut, contentKey = Unit, tag = "playlists-refresh",
                canPull = { !listState.canScrollBackward }) { state ->
            when (val content = state.content) {
                CollectionContent.Loading -> LibraryListSkeleton("playlists-skeleton", window)
                CollectionContent.Failed -> if (state.verification != null) VerificationCard(
                    state.verification,
                    onVerify = { onAction(LibraryAction.OpenVerification(state.verification!!.challengeUrl)) },
                    onDismiss = { onAction(LibraryAction.DismissVerification) },
                    modifier = Modifier.testTag("playlists-verification"),
                ) else LibraryError("playlists-error") { onAction(LibraryAction.Retry) }
                is CollectionContent.SignedOut -> LibraryStateMessage(
                    content.prompt.title, content.prompt.message, "playlists-signed-out", icon = AppIcon.Person,
                    actionLabel = content.prompt.action, onAction = { onAction(LibraryAction.SignIn) },
                )
                is CollectionContent.Empty -> LibraryStateMessage(content.title, content.message, "playlists-empty", icon = AppIcon.Playlist)
                is CollectionContent.Ready -> CollectionList(content, state.append, listState, window, onAction)
            }
        }
    }
}

@Composable
private fun CollectionList(content: CollectionContent.Ready, append: AppendState, listState: LazyListState, window: WindowClass,
                           onAction: (LibraryAction) -> Unit) {
    // Two rows side by side from 840dp; one column below that.
    val columns = if (window == WindowClass.Expanded) 2 else 1
    val inset = MinSpace.pageInset(window)
    val thumbnailWidth = libraryThumbnailWidth(window)
    val nearEnd by remember(content) { derivedStateOf {
        val info = listState.layoutInfo
        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 3
    } }
    LaunchedEffect(nearEnd, content.nextKey, append) {
        if (nearEnd && content.nextKey != null && append == AppendState.Idle) onAction(LibraryAction.LoadCollectionNext)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = if (columns == 2) 1280.dp else LibraryContentMaxWidth).fillMaxSize().testTag("playlists-list"),
            state = listState, contentPadding = PaddingValues(start = inset, end = inset, top = MinSpace.sm, bottom = MinSpace.xxl)) {
            items(content.playlists.chunked(columns), key = { it.first().id }) { line ->
                Row(Modifier.fillMaxWidth().padding(bottom = MinSpace.lg), horizontalArrangement = Arrangement.spacedBy(MinSpace.xxl)) {
                    line.forEach { playlist ->
                        PlaylistListRow(playlist, thumbnailWidth, Modifier.weight(1f)) { onAction(LibraryAction.OpenPlaylist(playlist.id)) }
                    }
                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item(key = "playlists-append") { LibraryAppendRow(append) { onAction(LibraryAction.LoadCollectionNext) } }
        }
    }
}
