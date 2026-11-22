package com.hhst.youtubelite.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.hhst.youtubelite.library.domain.HistoryContent
import com.hhst.youtubelite.library.domain.HistoryUiState
import com.hhst.youtubelite.library.domain.LibraryAction
import com.hhst.youtubelite.library.domain.LibraryOverlay
import com.hhst.youtubelite.ui.components.MinTopBar
import com.hhst.youtubelite.ui.localLabel
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.CollapsingAppBar
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.RefreshableContent
import com.hhst.youtubelite.ui.design.VerificationCard
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.rememberAppBarCollapseState
import com.hhst.youtubelite.ui.design.rememberWindowClass
import com.hhst.youtubelite.ui.design.CenteredListGutter
import com.hhst.youtubelite.ui.design.miniDockListPadding

/** Section names that only repeat the page title; the bar already reads "History". */
private val RedundantHistorySections = setOf("历史记录", "历史", "觀看記錄", "观看记录")

@Composable
fun HistoryScreen(state: HistoryUiState, onAction: (LibraryAction) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier,
    /** Device lane only: recording is paused on this device (the account lane exposes no state). */
    historyPaused: Boolean = false) {
    val appBar = rememberAppBarCollapseState(enabled = state.overlay == LibraryOverlay.None && state.content is HistoryContent.Ready)
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val window = rememberWindowClass()
    Box(modifier.fillMaxSize().background(DesignTokens.Background).testTag("history-screen").nestedScroll(appBar.connection)) {
        Column(Modifier.fillMaxSize()) {
            CollapsingAppBar(appBar) {
                LibrarySubPageBar(stringResource(R.string.download_history), onBack, onAction, trailing = if (!state.live) null else ({
                    AppIconButton(AppIcon.More, stringResource(R.string.a11y_history_settings),
                        modifier = Modifier.testTag("history-manage")) { onAction(LibraryAction.OpenManageHistory) }
                }))
            }
            if (historyPaused) HistoryPausedBanner()
            RefreshableContent(value = state, refreshing = state.content == HistoryContent.Loading,
                    onRefresh = { onAction(LibraryAction.Retry) }, modifier = Modifier.weight(1f).fillMaxWidth(),
                    enabled = state.overlay == LibraryOverlay.None && state.content !is HistoryContent.SignedOut, contentKey = Unit, tag = "history-refresh",
                    canPull = { !listState.canScrollBackward }) { state ->
                when (val content = state.content) {
                    HistoryContent.Loading -> LibraryListSkeleton("history-skeleton", window)
                    HistoryContent.Failed -> if (state.verification != null) VerificationCard(
                        state.verification,
                        onVerify = { onAction(LibraryAction.OpenVerification(state.verification!!.challengeUrl)) },
                        onDismiss = { onAction(LibraryAction.DismissVerification) },
                        modifier = Modifier.testTag("history-verification"),
                    ) else LibraryError("history-error") { onAction(LibraryAction.Retry) }
                    is HistoryContent.SignedOut -> LibraryStateMessage(
                        content.prompt.title, content.prompt.message, "history-signed-out", icon = AppIcon.Person,
                        actionLabel = content.prompt.action, onAction = { onAction(LibraryAction.SignIn) },
                    )
                    is HistoryContent.Empty -> LibraryStateMessage(content.title, content.message, "history-empty", icon = AppIcon.History)
                    is HistoryContent.Ready -> HistoryList(content, state.append, listState, window, onAction)
                }
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

@Composable
private fun HistoryList(content: HistoryContent.Ready, append: AppendState, listState: LazyListState, window: WindowClass,
                        onAction: (LibraryAction) -> Unit) {
    val grouped = remember(content) { content.page.entries.groupBy { it.section } }
    // Two rows side by side from 840dp; one column below that.
    val columns = if (window == WindowClass.Expanded) 2 else 1
    val inset = MinSpace.pageInset(window)
    val thumbnailWidth = libraryThumbnailWidth(window)
    val nearEnd by remember(content) { derivedStateOf {
        val info = listState.layoutInfo
        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 3
    } }
    LaunchedEffect(nearEnd, content.page.nextKey, append) {
        if (nearEnd && content.page.nextKey != null && append == AppendState.Idle) onAction(LibraryAction.LoadHistoryNext)
    }
    CenteredListGutter(if (columns == 2) 1280.dp else LibraryContentMaxWidth) { gutter ->
        val dock = miniDockListPadding(MinSpace.xxl)
        LazyColumn(Modifier.fillMaxSize().then(dock.modifier).testTag("history-list"),
            state = listState, contentPadding = PaddingValues(start = inset + gutter, end = inset + gutter, top = MinSpace.sm, bottom = dock.bottom)) {
            grouped.forEach { (section, entries) ->
                val name = section.trim()
                // The top bar already reads "History"; the body adds only the section groups.
                if (name.isNotEmpty() && !name.equals("History", ignoreCase = true) && name !in RedundantHistorySections) item(key = "section-$section") {
                    LibrarySectionTitle(localLabel(section), Modifier.padding(top = MinSpace.sm, bottom = MinSpace.md))
                }
                items(entries.chunked(columns), key = { it.first().id }) { line ->
                    Row(Modifier.fillMaxWidth().padding(bottom = MinSpace.lg), horizontalArrangement = Arrangement.spacedBy(MinSpace.xxl)) {
                        line.forEach { entry ->
                            HistoryCard(entry, thumbnailWidth, Modifier.weight(1f),
                                onOpen = { onAction(LibraryAction.OpenVideo(entry.videoId)) },
                                onMenu = { onAction(LibraryAction.OpenMenu(entry.videoId)) })
                        }
                        repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item(key = "history-append") { LibraryAppendRow(append) { onAction(LibraryAction.LoadHistoryNext) } }
        }
    }
}

/** Top bar of the History and Playlists pages: back, title, search and an optional trailing action. */
@Composable
internal fun LibrarySubPageBar(title: String, onBack: () -> Unit, onAction: (LibraryAction) -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null) {
    MinTopBar(title = title, onBack = onBack, tag = "library-subpage-bar") {
        AppIconButton(AppIcon.Search, stringResource(R.string.a11y_search_youtube)) { onAction(LibraryAction.OpenSearch) }
        trailing?.invoke(this)
    }
}

/** Shown while device-lane history recording is paused; Resume lives in the manage menu. */
@Composable
private fun HistoryPausedBanner() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MinSpace.lg, vertical = MinSpace.xs)
            .background(DesignTokens.Surface, com.hhst.youtubelite.ui.design.MinRadius.Field)
            .padding(horizontal = MinSpace.lg, vertical = MinSpace.sm).testTag("history-paused-banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.hhst.youtubelite.ui.design.AppIcon(AppIcon.Pause, Modifier.padding(end = MinSpace.md), color = DesignTokens.Secondary)
        com.hhst.youtubelite.ui.design.AppText(stringResource(R.string.qa1_history_paused_banner),
            com.hhst.youtubelite.ui.design.MinType.Body, Modifier.weight(1f), color = DesignTokens.Secondary)
    }
}
