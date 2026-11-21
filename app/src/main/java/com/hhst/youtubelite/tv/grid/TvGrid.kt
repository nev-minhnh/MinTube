package com.hhst.youtubelite.tv.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.ui.design.AppIcon
import kotlinx.coroutines.delay

/**
 * Paging of one TV grid or list, read from the phone ViewModels' append fields.
 * [paused]: automatic paging stopped after empty pages; the footer offers "Load more".
 */
data class TvPaging(
    val hasMore: Boolean = false,
    val append: AppendState = AppendState.Idle,
    val paused: Boolean = false,
) {
    val canAutoLoad: Boolean get() = hasMore && append == AppendState.Idle && !paused
    val footer: TvFooter get() = when {
        append == AppendState.Loading -> TvFooter.Loading
        append == AppendState.Failed -> TvFooter.Retry
        hasMore && paused -> TvFooter.LoadMore
        else -> TvFooter.None
    }
}

enum class TvFooter { None, Loading, Retry, LoadMore }

/** Columns of [cardWidth] cards that fit [available] with [gap] between them (at least one). */
fun tvGridColumns(available: Dp, cardWidth: Dp, gap: Dp): Int =
    ((available.value + gap.value) / (cardWidth.value + gap.value)).toInt().coerceAtLeast(1)

/** True when the last visible item is within [ahead] items of the end. */
fun nearEnd(lastVisible: Int?, total: Int, ahead: Int): Boolean =
    total > 0 && lastVisible != null && lastVisible >= total - ahead

@Composable
fun TvGridPagingEffect(state: LazyGridState, paging: TvPaging, itemCount: Int, onLoadMore: () -> Unit, ahead: Int = PAGE_AHEAD) {
    val near by remember(state) { derivedStateOf {
        nearEnd(state.layoutInfo.visibleItemsInfo.lastOrNull()?.index, state.layoutInfo.totalItemsCount, ahead)
    } }
    val load by rememberUpdatedState(onLoadMore)
    LaunchedEffect(near, paging.canAutoLoad, itemCount) { if ((near || itemCount == 0) && paging.canAutoLoad) load() }
}

@Composable
fun TvListPagingEffect(state: LazyListState, paging: TvPaging, itemCount: Int, onLoadMore: () -> Unit, ahead: Int = PAGE_AHEAD) {
    val near by remember(state) { derivedStateOf {
        nearEnd(state.layoutInfo.visibleItemsInfo.lastOrNull()?.index, state.layoutInfo.totalItemsCount, ahead)
    } }
    val load by rememberUpdatedState(onLoadMore)
    LaunchedEffect(near, paging.canAutoLoad, itemCount) { if ((near || itemCount == 0) && paging.canAutoLoad) load() }
}

/**
 * The end of a paged grid: nothing while idle, a quiet caption while loading, and a focusable
 * Retry or "Load more" (a remote has no pull or scroll gesture to page again).
 */
@Composable
fun TvPagingFooter(footer: TvFooter, onRetry: () -> Unit, onLoadMore: () -> Unit, modifier: Modifier = Modifier, tag: String = "") {
    if (footer == TvFooter.None) return
    val focusManager = LocalFocusManager.current
    // The button leaves while the next page loads: hand focus to the card above it first.
    val retry = { focusManager.moveFocus(FocusDirection.Up); onRetry() }
    val more = { focusManager.moveFocus(FocusDirection.Up); onLoadMore() }
    Box(modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = TvSpacing.md), contentAlignment = Alignment.Center) {
        when (footer) {
            TvFooter.Loading -> Text(stringResource(R.string.tv_loading_more), style = TvType.Caption, color = TvColors.Secondary)
            TvFooter.Retry -> Box(contentAlignment = Alignment.Center) {
                TvButton(stringResource(R.string.retry), retry, Modifier.testTag("$tag-retry-more"), icon = AppIcon.Refresh)
            }
            TvFooter.LoadMore -> TvButton(stringResource(R.string.tv_load_more), more, Modifier.testTag("$tag-load-more"),
                icon = AppIcon.Refresh)
            TvFooter.None -> Unit
        }
    }
}

/** Requests focus until the target is composed and takes it, or the attempts run out. */
suspend fun FocusRequester.requestWhenReady(attempts: Int = 40, delayMs: Long = 50): Boolean {
    repeat(attempts) {
        withFrameNanos { }
        if (runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return true
        delay(delayMs)
    }
    return false
}

/**
 * Focus must never be lost on a full-screen page: when the activity resumes (Back from Watch)
 * and nothing in [hasFocus] holds focus, [restore] puts it back on the remembered item.
 */
@Composable
fun TvRestoreFocusOnResume(key: Any?, hasFocus: () -> Boolean, restore: suspend () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    val focused by rememberUpdatedState(hasFocus)
    val action by rememberUpdatedState(restore)
    LaunchedEffect(key, resumed) {
        if (!resumed) return@LaunchedEffect
        withFrameNanos { }
        delay(RESTORE_SETTLE_MS)
        if (!focused()) action()
    }
}

const val PAGE_AHEAD = 6
private const val RESTORE_SETTLE_MS = 150L
