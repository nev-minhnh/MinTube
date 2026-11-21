package com.hhst.youtubelite.tv.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvSkeletonRow
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.design.rememberTvFocusMemory
import com.hhst.youtubelite.ui.design.AppIcon

/** TV Library destination: one row per library shelf. State-hoisted; [TvLibraryController] wires it. */
@Composable
fun TvLibraryScreen(
    state: TvLibraryUiState,
    onOpen: (TvLibraryShelf, TvVideo) -> Unit,
    onViewAll: (TvLibraryShelf) -> Unit,
    onRetryShelf: (TvLibraryShelf) -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    focusMemory: TvFocusMemory = rememberTvFocusMemory(),
) {
    Box(modifier.fillMaxSize().testTag(TAG_LIBRARY)) {
        when (state.status) {
            TvLibraryStatus.Loading -> Column(
                Modifier.fillMaxSize().padding(start = TvSpacing.xl, top = TvSpacing.OverscanVertical).testTag(TAG_LIBRARY_SKELETON),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
            ) {
                TvPageTitle(stringResource(R.string.tv_nav_library))
                repeat(3) { TvSkeletonRow() }
            }
            TvLibraryStatus.SignedOut -> TvCentered {
                TvStateMessage(
                    title = stringResource(R.string.tv_library_signed_out_title),
                    message = stringResource(R.string.tv_library_signed_out_message),
                    icon = AppIcon.Library,
                    actionLabel = stringResource(R.string.ui_sign_in),
                    onAction = onSignIn,
                    actionModifier = Modifier.testTag(TAG_LIBRARY_SIGN_IN),
                )
            }
            TvLibraryStatus.Failed -> TvCentered {
                TvStateMessage(
                    title = stringResource(R.string.tv_library_failed_title),
                    message = stringResource(R.string.tv_home_failed_message),
                    icon = AppIcon.WifiOff,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_LIBRARY_RETRY),
                )
            }
            TvLibraryStatus.Ready -> TvShelves(
                shelves = state.shelves,
                onOpen = { key, video -> TvLibraryShelf.of(key)?.let { onOpen(it, video) } },
                onViewAll = { key -> TvLibraryShelf.of(key)?.let(onViewAll) },
                onRetry = { key -> TvLibraryShelf.of(key)?.let(onRetryShelf) },
                memory = focusMemory,
                header = { TvPageTitle(stringResource(R.string.tv_nav_library)) },
            )
        }
    }
}

const val TAG_LIBRARY = "tv-library"
const val TAG_LIBRARY_SKELETON = "tv-library-skeleton"
const val TAG_LIBRARY_RETRY = "tv-library-retry"
const val TAG_LIBRARY_SIGN_IN = "tv-library-sign-in"
