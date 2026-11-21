package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.hhst.youtubelite.search.domain.SearchFilters
import com.hhst.youtubelite.search.domain.SearchResultType
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.grid.TvPaging
import com.hhst.youtubelite.tv.search.TAG_FIELD
import com.hhst.youtubelite.tv.search.TAG_RETRY
import com.hhst.youtubelite.tv.search.TAG_SKELETON
import com.hhst.youtubelite.tv.search.TAG_START
import com.hhst.youtubelite.tv.search.TAG_VOICE
import com.hhst.youtubelite.tv.search.TvSearchItem
import com.hhst.youtubelite.tv.search.TvSearchResults
import com.hhst.youtubelite.tv.search.TvSearchScreen
import com.hhst.youtubelite.tv.search.TvSearchStatus
import com.hhst.youtubelite.tv.search.TvSearchUiState
import com.hhst.youtubelite.tv.search.recentRemoveTag
import com.hhst.youtubelite.tv.search.recentTag
import com.hhst.youtubelite.tv.search.resultTag
import com.hhst.youtubelite.tv.search.typeTag
import com.hhst.youtubelite.tv.shell.TvShell
import com.hhst.youtubelite.tv.shell.rememberTvShellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** TV Search driven with the D-pad inside the shell, on fake state (no network, no IME). */
class TvSearchAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(TvSearchUiState())
    private val submitted = mutableListOf<String>()
    private val removed = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val types = mutableListOf<SearchResultType>()
    private var retries = 0
    private var voice = 0

    private fun results(count: Int = 8) = TvSearchResults(TvSearchStatus.Ready,
        (0 until count).map { TvSearchItem.Video(TvVideo("v$it", "Video $it", "Channel", "1K views"), "UC$it") },
        TvPaging(hasMore = false))

    private fun host(initial: TvSearchUiState) {
        state = initial
        compose.setContent {
            TvCanvas {
                TvShell(rememberTvShellState(com.hhst.youtubelite.tv.shell.TvDestination.Search), onExit = {}) {
                    TvSearchScreen(
                        state = state,
                        onDraftChange = { state = state.copy(draft = it) },
                        onSubmit = { query ->
                            submitted += query
                            state = state.copy(draft = query, query = query, results = TvSearchResults(TvSearchStatus.Loading))
                        },
                        onRemoveRecent = { entry ->
                            removed += entry
                            state = state.copy(recent = state.recent - entry)
                        },
                        onVoice = { voice++ },
                        onSelectType = { type ->
                            types += type
                            state = state.copy(filters = SearchFilters(type))
                        },
                        onSelectDate = {},
                        onOpen = { opened += it.key },
                        onOpenChannel = {},
                        onLoadMore = {},
                        onRetryMore = {},
                        onRetry = {
                            // As the ViewModel does: a retry reloads the results.
                            retries++
                            state = state.copy(results = TvSearchResults(TvSearchStatus.Loading))
                        },
                    )
                }
            }
        }
    }

    @Test fun searchFieldTakesFocusFirstAndTheVoiceButtonSitsToItsRight() {
        host(TvSearchUiState(voiceAvailable = true))
        compose.awaitFocus(TAG_FIELD)
        compose.onNodeWithTag(TAG_START).assertExists()
        compose.press(Key.DirectionRight)
        compose.awaitFocus(TAG_VOICE)
        compose.press(Key.DirectionCenter)
        assertEquals(1, voice)
    }

    @Test fun noVoiceButtonWhenNothingResolves() {
        host(TvSearchUiState(voiceAvailable = false))
        compose.awaitFocus(TAG_FIELD)
        compose.onNodeWithTag(TAG_VOICE).assertDoesNotExist()
    }

    @Test fun aRecentSearchIsRemovedFromTheRemoteAndFocusStaysInTheList() {
        host(TvSearchUiState(recent = listOf("cats", "dogs")))
        compose.awaitFocus(TAG_FIELD)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(recentTag(0))
        compose.press(Key.DirectionRight)
        compose.awaitFocus(recentRemoveTag(0))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("cats"), removed)
        // "dogs" moved into the removed row's place and takes focus.
        compose.awaitFocus(recentTag(0))
    }

    @Test fun choosingARecentSearchSubmitsItAndFocusFollowsTheResults() {
        host(TvSearchUiState(recent = listOf("cats")))
        compose.awaitFocus(TAG_FIELD)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(recentTag(0))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("cats"), submitted)
        compose.onNodeWithTag(TAG_SKELETON).assertExists()
        compose.awaitFocus(TAG_FIELD)
        state = state.copy(results = results())
        compose.awaitFocus(resultTag(0))
    }

    @Test fun filtersSitBetweenTheFieldAndTheGridAndCardsOpen() {
        host(TvSearchUiState(draft = "cats", query = "cats", results = results()))
        compose.awaitFocus(TAG_FIELD)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(typeTag(SearchResultType.All))
        compose.press(Key.DirectionRight)
        compose.awaitFocus(typeTag(SearchResultType.Shorts))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf(SearchResultType.Shorts), types)
        compose.press(Key.DirectionDown)
        compose.waitUntil(5_000) { (0 until 8).any { compose.isFocused(resultTag(it)) } }
        compose.press(Key.DirectionCenter)
        assertEquals(1, opened.size)
        compose.press(Key.DirectionUp)
        compose.waitUntil(5_000) { SearchResultType.entries.any { compose.isFocused(typeTag(it)) } }
    }

    @Test fun aFailedSearchOffersAFocusableRetry() {
        host(TvSearchUiState(recent = listOf("cats")))
        compose.awaitFocus(TAG_FIELD)
        compose.press(Key.DirectionDown)
        compose.press(Key.DirectionCenter)
        state = state.copy(results = TvSearchResults(TvSearchStatus.Failed))
        compose.awaitFocus(TAG_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(1, retries)
        assertTrue(compose.isFocused(TAG_FIELD))
    }

    @Test fun anEmptySearchOffersAFocusableRetry() {
        host(TvSearchUiState(recent = listOf("zzz")))
        compose.awaitFocus(TAG_FIELD)
        compose.press(Key.DirectionDown)
        compose.press(Key.DirectionCenter)
        state = state.copy(results = TvSearchResults(TvSearchStatus.Empty("zzz", SearchResultType.All)))
        compose.awaitFocus(TAG_RETRY)
    }
}
