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
import com.hhst.youtubelite.tv.grid.TvPaging
import com.hhst.youtubelite.tv.playlist.TAG_PLAYLIST_OWNER
import com.hhst.youtubelite.tv.playlist.TAG_PLAYLIST_PLAY_ALL
import com.hhst.youtubelite.tv.playlist.TAG_PLAYLIST_RETRY
import com.hhst.youtubelite.tv.playlist.TAG_PLAYLIST_SHUFFLE
import com.hhst.youtubelite.tv.playlist.TAG_PLAYLIST_SKELETON
import com.hhst.youtubelite.tv.playlist.TvPlaylistHeader
import com.hhst.youtubelite.tv.playlist.TvPlaylistRow
import com.hhst.youtubelite.tv.playlist.TvPlaylistScreen
import com.hhst.youtubelite.tv.playlist.TvPlaylistStatus
import com.hhst.youtubelite.tv.playlist.playlistRowTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** TV playlist page driven with the D-pad on fake state. */
class TvPlaylistAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var status by mutableStateOf<TvPlaylistStatus>(TvPlaylistStatus.Loading)
    private val opened = mutableListOf<String>()
    private var playAll = 0
    private var shuffle = 0
    private var owner = 0
    private var loadMore = 0
    private var retries = 0

    private fun ready(rows: Int = 12, hasMore: Boolean = false) = TvPlaylistStatus.Ready(
        TvPlaylistHeader("Mix", "Owner", "UCowner", "12 videos", ""),
        (0 until rows).map { TvPlaylistRow("e$it", TvVideo("v$it", "Video $it", "Channel", "1K views")) },
        TvPaging(hasMore = hasMore),
    )

    private fun host(initial: TvPlaylistStatus) {
        status = initial
        compose.setContent {
            TvCanvas {
                TvPlaylistScreen(
                    status = status,
                    onPlayAll = { playAll++ },
                    onShuffle = { shuffle++ },
                    onOpenOwner = { owner++ },
                    onOpen = { opened += it.entryId },
                    onLoadMore = { loadMore++ },
                    onRetryMore = {},
                    onRetry = { retries++ },
                )
            }
        }
    }

    @Test fun playAllTakesFirstFocusThenShuffleAndOwner() {
        host(ready())
        compose.awaitFocus(TAG_PLAYLIST_PLAY_ALL)
        compose.press(Key.DirectionCenter)
        assertEquals(1, playAll)
        compose.press(Key.DirectionRight)
        compose.awaitFocus(TAG_PLAYLIST_SHUFFLE)
        compose.press(Key.DirectionCenter)
        assertEquals(1, shuffle)
        compose.press(Key.DirectionLeft)
        compose.awaitFocus(TAG_PLAYLIST_PLAY_ALL)
        compose.press(Key.DirectionUp)
        compose.awaitFocus(TAG_PLAYLIST_OWNER)
        compose.press(Key.DirectionCenter)
        assertEquals(1, owner)
    }

    @Test fun rowsOpenAndTheListRemembersTheRowOnReentry() {
        host(ready())
        compose.awaitFocus(TAG_PLAYLIST_PLAY_ALL)
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionRight)
        compose.awaitFocus(playlistRowTag(0))
        compose.press(Key.DirectionDown)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(playlistRowTag(2))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("e2"), opened)
        compose.press(Key.DirectionLeft)
        compose.awaitAnyFocus(TAG_PLAYLIST_SHUFFLE, TAG_PLAYLIST_PLAY_ALL)
        compose.press(Key.DirectionRight)
        compose.awaitAnyFocus(TAG_PLAYLIST_SHUFFLE, playlistRowTag(2))
        if (compose.isFocused(TAG_PLAYLIST_SHUFFLE)) compose.press(Key.DirectionRight)
        compose.awaitFocus(playlistRowTag(2))
    }

    @Test fun scrollingNearTheEndPagesTheList() {
        host(ready(rows = 12, hasMore = true))
        compose.awaitFocus(TAG_PLAYLIST_PLAY_ALL)
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionRight)
        compose.awaitFocus(playlistRowTag(0))
        repeat(8) { compose.press(Key.DirectionDown) }
        compose.waitUntil(5_000) { loadMore > 0 }
    }

    @Test fun loadingThenFailureOffersAFocusableRetry() {
        host(TvPlaylistStatus.Loading)
        compose.onNodeWithTag(TAG_PLAYLIST_SKELETON).assertExists()
        status = TvPlaylistStatus.Failed
        compose.awaitFocus(TAG_PLAYLIST_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(1, retries)
        status = TvPlaylistStatus.Unavailable
        compose.awaitFocus(TAG_PLAYLIST_RETRY)
    }
}
