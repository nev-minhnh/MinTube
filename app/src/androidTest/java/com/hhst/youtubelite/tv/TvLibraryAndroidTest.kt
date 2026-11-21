package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.library.TAG_LIBRARY_RETRY
import com.hhst.youtubelite.tv.library.TAG_LIBRARY_SIGN_IN
import com.hhst.youtubelite.tv.library.TAG_LIBRARY_SKELETON
import com.hhst.youtubelite.tv.library.TAG_PAGE_RETRY
import com.hhst.youtubelite.tv.library.TvLibraryPage
import com.hhst.youtubelite.tv.library.TvLibraryScreen
import com.hhst.youtubelite.tv.library.TvLibraryShelf
import com.hhst.youtubelite.tv.library.TvLibraryStatus
import com.hhst.youtubelite.tv.library.TvLibraryUiState
import com.hhst.youtubelite.tv.library.TvPageState
import com.hhst.youtubelite.tv.library.TvPageStatus
import com.hhst.youtubelite.tv.library.TvShelf
import com.hhst.youtubelite.tv.library.TvShelfState
import com.hhst.youtubelite.tv.library.gridCardTag
import com.hhst.youtubelite.tv.library.shelfCardTag
import com.hhst.youtubelite.tv.library.shelfRetryTag
import com.hhst.youtubelite.tv.library.shelfViewAllTag
import com.hhst.youtubelite.tv.shell.TvShell
import com.hhst.youtubelite.tv.shell.rememberTvShellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Library rows inside the shell and the full-screen "View all" grid, on fake state (no network). */
class TvLibraryAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var library by mutableStateOf(TvLibraryUiState(TvLibraryStatus.Loading))
    private var page by mutableStateOf(TvPageState(TvPageStatus.Loading))
    private val opened = mutableListOf<String>()
    private val viewAll = mutableListOf<TvLibraryShelf>()
    private val retried = mutableListOf<TvLibraryShelf>()
    private var retries = 0
    private var signIns = 0

    private fun videos(prefix: String, count: Int) = (0 until count).map { TvVideo("$prefix$it", "Video $prefix$it", "Channel") }

    private fun shelves() = TvLibraryUiState(TvLibraryStatus.Ready, listOf(
        TvShelf("history", "History", TvShelfState.Ready(videos("h", 2))),
        TvShelf("watch-later", "Watch later", TvShelfState.Failed),
        TvShelf("playlists", "Playlists", TvShelfState.Ready(videos("p", 3))),
    ))

    private fun hostLibrary(initial: TvLibraryUiState) {
        library = initial
        compose.setContent {
            TvCanvas {
                TvShell(rememberTvShellState(), onExit = {}) {
                    TvLibraryScreen(
                        state = library,
                        onOpen = { _, video -> opened += video.id },
                        onViewAll = { viewAll += it },
                        onRetryShelf = { retried += it },
                        onRetry = { retries++ },
                        onSignIn = { signIns++ },
                    )
                }
            }
        }
    }

    private fun hostPage(initial: TvPageState) {
        page = initial
        compose.setContent {
            TvCanvas {
                TvLibraryPage(title = "History", state = page, onOpen = { opened += it.id }, onRetry = { retries++ }, onSignIn = { signIns++ })
            }
        }
    }

    @Test fun rowsEndInViewAllAndFocusComesBackToIt() {
        hostLibrary(shelves())
        compose.awaitFocus(shelfCardTag("history", 0))
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionRight)
        compose.awaitFocus(shelfViewAllTag("history"))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf(TvLibraryShelf.History), viewAll)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(shelfRetryTag("watch-later"))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf(TvLibraryShelf.WatchLater), retried)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(shelfCardTag("playlists", 0))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("p0"), opened)
        compose.press(Key.DirectionUp)
        compose.press(Key.DirectionUp)
        compose.awaitFocus(shelfViewAllTag("history"))
    }

    @Test fun loadingThenRowsTakeFocus() {
        hostLibrary(TvLibraryUiState(TvLibraryStatus.Loading))
        compose.onNodeWithTag(TAG_LIBRARY_SKELETON).assertExists()
        library = shelves()
        compose.awaitFocus(shelfCardTag("history", 0))
    }

    @Test fun signedOutOffersSignInAndFailureOffersRetry() {
        hostLibrary(TvLibraryUiState(TvLibraryStatus.SignedOut))
        compose.awaitFocus(TAG_LIBRARY_SIGN_IN)
        compose.press(Key.DirectionCenter)
        assertEquals(1, signIns)
        library = TvLibraryUiState(TvLibraryStatus.Failed)
        compose.awaitFocus(TAG_LIBRARY_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(1, retries)
    }

    @Test fun gridPageStartsOnTheFirstCardAndARemovedCardHandsFocusToItsNeighbour() {
        val items = videos("v", 6)
        hostPage(TvPageState(TvPageStatus.Ready, items))
        compose.awaitFocus(gridCardTag(0))
        compose.press(Key.DirectionRight)
        compose.awaitFocus(gridCardTag(1))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("v1"), opened)
        // Removing the focused entry (history removal) leaves focus on the card now in its slot.
        page = TvPageState(TvPageStatus.Ready, items - items[1])
        compose.awaitFocus(gridCardTag(1))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("v1", "v2"), opened)
    }

    @Test fun emptyPageFocusesRetry() {
        hostPage(TvPageState(TvPageStatus.Empty))
        compose.awaitFocus(TAG_PAGE_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(1, retries)
    }
}
