package com.hhst.youtubelite.tv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSkeletonRow
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.design.rememberTvFocusMemory
import com.hhst.youtubelite.ui.design.AppIcon

/**
 * Full-screen library page ("View all"): title, optional header [actions], then a paged grid.
 * State-hoisted; [TvLibraryPageActivity] wires each kind. Every state keeps a focusable action.
 */
@Composable
fun TvLibraryPage(
    title: String,
    state: TvPageState,
    onOpen: (TvVideo) -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    emptyTitle: String = stringResource(R.string.tv_library_empty_title),
    emptyMessage: String? = null,
    onLoadMore: () -> Unit = {},
    onRetryMore: () -> Unit = {},
    onLongClick: ((TvVideo) -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    focusMemory: TvFocusMemory = rememberTvFocusMemory(),
) {
    val actionsRequester = remember { FocusRequester() }
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            TvPageTitle(title, detail = detail)
            if (actions != null) Row(
                Modifier.focusRequester(actionsRequester).focusGroup().testTag(TAG_PAGE_ACTIONS),
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
                content = actions,
            )
        }
    }
    Column(modifier.fillMaxSize().background(TvColors.Background).testTag(TAG_PAGE)) {
        when (state.status) {
            TvPageStatus.Loading -> Column(
                Modifier.fillMaxSize().padding(start = GridStart, top = TvSpacing.OverscanVertical).testTag(TAG_PAGE_SKELETON),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
            ) {
                TvPageTitle(title)
                repeat(3) { TvSkeletonRow(showHeader = false) }
            }
            TvPageStatus.SignedOut -> PageMessage(header) {
                TvStateMessage(stringResource(R.string.tv_library_signed_out_title), message = stringResource(R.string.tv_library_signed_out_message),
                    icon = AppIcon.Library, actionLabel = stringResource(R.string.ui_sign_in), onAction = onSignIn,
                    actionModifier = Modifier.testTag(TAG_PAGE_SIGN_IN), requestFocus = true)
            }
            TvPageStatus.Failed -> PageMessage(header) {
                TvStateMessage(stringResource(R.string.tv_library_failed_title), message = stringResource(R.string.tv_home_failed_message),
                    icon = AppIcon.WifiOff, actionLabel = stringResource(R.string.retry), onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_PAGE_RETRY), requestFocus = true)
            }
            TvPageStatus.Empty -> PageMessage(header) {
                TvStateMessage(emptyTitle, message = emptyMessage, icon = AppIcon.Library,
                    actionLabel = stringResource(R.string.retry), onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_PAGE_RETRY), requestFocus = true)
            }
            TvPageStatus.Ready -> TvVideoGrid(
                items = state.items,
                onOpen = onOpen,
                memory = focusMemory,
                onLongClick = onLongClick,
                header = header,
                footer = state.footer,
                canPage = state.canPage,
                onLoadMore = onLoadMore,
                onRetryMore = onRetryMore,
                fallback = {
                    actions != null && runCatching { actionsRequester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
                },
            )
        }
    }
}

/** The page header stays above a state message so header actions (pause/resume) remain reachable. */
@Composable
private fun PageMessage(header: @Composable () -> Unit, message: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = GridStart, end = TvSpacing.OverscanHorizontal, top = TvSpacing.OverscanVertical)) {
        header()
        TvCentered(Modifier.padding(bottom = 48.dp)) { message() }
    }
}

/**
 * Remote-friendly confirmation for one item (hold OK on a card): a dialog window, so focus stays
 * inside it; Back or Cancel closes it. The confirm button takes focus.
 */
@Composable
fun TvItemActionDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmIcon: AppIcon = AppIcon.Delete,
) {
    // Not the platform dialog width: it clipped the second button ("Can" for Cancel) at 1080p.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val confirm = remember { FocusRequester() }
        Column(
            Modifier.widthIn(max = 840.dp).background(TvColors.Panel, RoundedCornerShape(TvSizes.Corner))
                .padding(TvSpacing.xl).testTag(TAG_ITEM_DIALOG),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.lg),
        ) {
            Text(title, style = TvType.Title, color = TvColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
                TvButton(confirmLabel, onConfirm, Modifier.focusRequester(confirm).testTag(TAG_ITEM_CONFIRM), icon = confirmIcon,
                    primary = true)
                TvButton(stringResource(R.string.cancel), onDismiss, Modifier.testTag(TAG_ITEM_CANCEL))
            }
        }
        LaunchedEffect(Unit) { runCatching { confirm.requestFocus() } }
    }
}

const val TAG_PAGE = "tv-library-page"
const val TAG_PAGE_SKELETON = "tv-library-page-skeleton"
const val TAG_PAGE_RETRY = "tv-library-page-retry"
const val TAG_PAGE_SIGN_IN = "tv-library-page-sign-in"
const val TAG_PAGE_ACTIONS = "tv-library-page-actions"
const val TAG_ITEM_DIALOG = "tv-item-dialog"
const val TAG_ITEM_CONFIRM = "tv-item-confirm"
const val TAG_ITEM_CANCEL = "tv-item-cancel"
