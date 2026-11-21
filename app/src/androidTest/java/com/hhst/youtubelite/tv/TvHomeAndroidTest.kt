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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import com.hhst.youtubelite.tv.home.TvHomeViewState
import com.hhst.youtubelite.tv.shell.TAG_RAIL_REFRESH
import com.hhst.youtubelite.tv.shell.TvComingSoon
import com.hhst.youtubelite.tv.shell.TvDestination
import com.hhst.youtubelite.tv.shell.TvShellState
import com.hhst.youtubelite.tv.shell.railItemTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Home rows driven with the D-pad inside the shell, on fake row state (no network). */
class TvHomeAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var shell: TvShellState
    private var refreshes = 0
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

    /** Like the activity: Home keeps its view state outside the composition; Refresh comes from the rail. */
    private fun hostInShell(initial: TvHomeUiState) {
        state = initial
        compose.setContent {
            TvCanvas {
                shell = rememberTvShellState()
                val view = remember { TvHomeViewState() }
                var handled by remember { mutableIntStateOf(0) }
                var focusFirst by remember { mutableIntStateOf(0) }
                TvShell(shell, onExit = {}) { destination ->
                    if (destination == TvDestination.Home) {
                        val requests = shell.refreshRequests
                        LaunchedEffect(requests) {
                            if (requests == handled) return@LaunchedEffect
                            handled = requests
                            refreshes++
                            state = rows(main = videos("new", 6))
                            focusFirst++
                        }
                        TvHomeScreen(state = state, onOpen = { opened += it.id }, onLoadMore = {}, onRetryRow = {},
                            onRetry = {}, view = view, focusFirst = focusFirst)
                    } else {
                        TvComingSoon(destination, onHome = { shell.select(TvDestination.Home) })
                    }
                }
            }
        }
    }

    @Test fun switchingDestinationsKeepsTheRowsAndTheFocusedCard() {
        hostInShell(rows())
        compose.awaitFocus(cardTag("main", 0))
        repeat(3) { compose.press(Key.DirectionRight) }
        compose.press(Key.DirectionDown)
        compose.press(Key.DirectionRight)
        compose.awaitFocus(cardTag("shorts", 1))
        compose.runOnUiThread { shell.select(TvDestination.Library) }
        compose.onNodeWithTag(cardTag("main", 0)).assertDoesNotExist()
        compose.runOnUiThread { shell.select(TvDestination.Home) }
        compose.awaitFocus(cardTag("shorts", 1))
        // Up still lands on the card the main row was left on: nothing reloaded or reset.
        compose.press(Key.DirectionUp)
        compose.awaitFocus(cardTag("main", 3))
        assertEquals(0, refreshes)
    }

    @Test fun theRailRefreshReloadsAndFocusesTheFirstNewCard() {
        hostInShell(rows())
        compose.awaitFocus(cardTag("main", 0))
        repeat(2) { compose.press(Key.DirectionRight) }
        compose.press(Key.DirectionDown)
        compose.awaitFocus(cardTag("shorts", 0))
        compose.press(Key.DirectionLeft)
        compose.awaitAnyFocus(railItemTag(TvDestination.Home), railItemTag(TvDestination.Search))
        // Refresh sits below the destinations and is not one of them.
        repeat(6) { compose.press(Key.DirectionDown) }
        compose.awaitFocus(TAG_RAIL_REFRESH)
        compose.onNodeWithTag(TAG_RAIL_REFRESH).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.press(Key.DirectionCenter)
        compose.waitUntil(3_000) { refreshes == 1 }
        compose.onNodeWithText("Video new0").assertExists()
        compose.awaitFocus(cardTag("main", 0))
        assertEquals(TvDestination.Home, shell.selected)
    }

    @Test fun refreshFromAnotherDestinationShowsHome() {
        hostInShell(rows())
        compose.awaitFocus(cardTag("main", 0))
        compose.runOnUiThread { shell.select(TvDestination.Settings) }
        compose.onNodeWithTag(cardTag("main", 0)).assertDoesNotExist()
        compose.runOnUiThread { shell.refresh() }
        compose.awaitFocus(cardTag("main", 0))
        assertEquals(1, refreshes)
        assertEquals(TvDestination.Home, shell.selected)
    }

    @Test fun failedHomeOffersAFocusableRetry() {
        host(TvHomeUiState(TvHomeStatus.Failed))
        compose.awaitFocus(TAG_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(1, retries)
    }
}
