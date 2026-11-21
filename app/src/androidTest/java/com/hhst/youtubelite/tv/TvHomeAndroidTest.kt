package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.home.TAG_RETRY
import com.hhst.youtubelite.tv.home.TAG_SKELETON
import com.hhst.youtubelite.tv.home.TvHomeRow
import com.hhst.youtubelite.tv.home.TvHomeScreen
import com.hhst.youtubelite.tv.home.TvHomeStatus
import com.hhst.youtubelite.tv.home.TvHomeUiState
import com.hhst.youtubelite.tv.home.TvRowKind
import com.hhst.youtubelite.tv.home.TvRowState
import com.hhst.youtubelite.tv.home.TvRowTitle
import com.hhst.youtubelite.tv.home.cardTag
import com.hhst.youtubelite.tv.home.rowRetryTag
import com.hhst.youtubelite.tv.shell.TvShell
import com.hhst.youtubelite.tv.shell.rememberTvShellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Home rows driven with the D-pad inside the shell, on fake row state (no network). */
class TvHomeAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(TvHomeUiState(TvHomeStatus.Loading))
    private val opened = mutableListOf<String>()
    private val loadMore = mutableListOf<String>()
    private val retriedRows = mutableListOf<String>()
    private var retries = 0

    private fun videos(prefix: String, count: Int, short: Boolean = false) =
        (0 until count).map { TvVideo("$prefix$it", "Video $prefix$it", "Channel", "1K views", short = short) }

    private fun rows(main: List<TvVideo> = videos("v", 6)) = TvHomeUiState(TvHomeStatus.Ready, listOf(
        TvHomeRow("main", TvRowKind.Main, TvRowTitle.Text("Recommended"), TvRowState.Ready(main, AppendState.Idle, hasMore = true)),
        TvHomeRow("shorts", TvRowKind.Shorts, TvRowTitle.Text("Shorts"), TvRowState.Ready(videos("s", 4, short = true))),
        TvHomeRow("chip:Music", TvRowKind.Chip, TvRowTitle.Text("Music"), TvRowState.Failed, chip = "Music"),
        TvHomeRow("chip:Gaming", TvRowKind.Chip, TvRowTitle.Text("Gaming"), TvRowState.Loading, chip = "Gaming"),
    ))

    private fun host(initial: TvHomeUiState) {
        state = initial
        compose.setContent {
            TvCanvas {
                TvShell(rememberTvShellState(), onExit = {}) {
                    TvHomeScreen(
                        state = state,
                        onOpen = { opened += it.id },
                        onLoadMore = { loadMore += it },
                        onRetryRow = { retriedRows += it },
                        onRetry = { retries++ },
                    )
                }
            }
        }
    }

    @Test fun dpadMovesAlongAndBetweenRowsAndEachRowRemembersItsCard() {
        host(rows())
        compose.awaitFocus(cardTag("main", 0))
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionRight)
        compose.awaitFocus(cardTag("main", 2))
        compose.press(Key.DirectionDown)
        compose.awaitFocus(cardTag("shorts", 0))
        compose.press(Key.DirectionRight)
        compose.awaitFocus(cardTag("shorts", 1))
        compose.press(Key.DirectionUp)
        compose.awaitFocus(cardTag("main", 2))
        compose.press(Key.DirectionDown)
        compose.awaitFocus(cardTag("shorts", 1))
        compose.press(Key.DirectionDown)
        compose.awaitFocus(rowRetryTag("chip:Music"))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("chip:Music"), retriedRows)
        compose.press(Key.DirectionUp)
        compose.awaitFocus(cardTag("shorts", 1))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("s1"), opened)
    }

    @Test fun reachingTheEndOfARowPagesIt() {
        host(rows(main = videos("v", 12)))
        compose.awaitFocus(cardTag("main", 0))
        assertTrue(loadMore.isEmpty())
        repeat(7) { compose.press(Key.DirectionRight) }
        compose.awaitFocus(cardTag("main", 7))
        compose.waitUntil(3_000) { "main" in loadMore }
        assertTrue("shorts" !in loadMore)
    }

    @Test fun focusReturnsToTheSameSlotWhenARefreshReplacesTheCard() {
        host(rows())
        compose.awaitFocus(cardTag("main", 0))
        repeat(3) { compose.press(Key.DirectionRight) }
        compose.awaitFocus(cardTag("main", 3))
        // A quiet refresh brings new videos: the focused card leaves the composition.
        state = rows(main = videos("fresh", 6))
        compose.awaitFocus(cardTag("main", 3))
    }

    @Test fun loadingShowsSkeletonRowsThenFocusEntersTheRows() {
        host(TvHomeUiState(TvHomeStatus.Loading))
        compose.onNodeWithTag(TAG_SKELETON).assertExists()
        state = rows()
        compose.awaitFocus(cardTag("main", 0))
    }

    @Test fun failedHomeOffersAFocusableRetry() {
        host(TvHomeUiState(TvHomeStatus.Failed))
        compose.awaitFocus(TAG_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(1, retries)
    }
}
