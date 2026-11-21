package com.hhst.youtubelite.tv

import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.library.domain.AccountProfile
import com.hhst.youtubelite.library.domain.HistoryContent
import com.hhst.youtubelite.library.domain.HistoryEntry
import com.hhst.youtubelite.library.domain.HistoryPage
import com.hhst.youtubelite.library.domain.HistoryUiState
import com.hhst.youtubelite.library.domain.LibraryContent
import com.hhst.youtubelite.library.domain.LibraryUiState
import com.hhst.youtubelite.library.domain.SignInPrompt
import com.hhst.youtubelite.library.domain.YouPage
import com.hhst.youtubelite.player.QueueItem
import com.hhst.youtubelite.playlist.domain.PlaylistContent
import com.hhst.youtubelite.playlist.domain.PlaylistEntry
import com.hhst.youtubelite.playlist.domain.PlaylistPage
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.playlist.domain.PlaylistUiState
import com.hhst.youtubelite.tv.library.TvGridFooter
import com.hhst.youtubelite.tv.library.TvGridMetrics
import com.hhst.youtubelite.tv.library.TvLibraryModel
import com.hhst.youtubelite.tv.library.TvLibraryShelf
import com.hhst.youtubelite.tv.library.TvLibraryStatus
import com.hhst.youtubelite.tv.library.TvPageStatus
import com.hhst.youtubelite.tv.library.TvShelfState
import com.hhst.youtubelite.tv.library.VIEW_ALL_KEY
import com.hhst.youtubelite.tv.library.focusKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLibraryModelTest {
    private val account = AccountProfile("Me", "@me", "", "UCme")
    private fun entry(i: Int) = HistoryEntry("h$i", "v$i", "Video $i", "Channel", "UC1", "", "1:00", section = "Today")
    private fun summary(id: String) = PlaylistSummary(id, "List $id", "Me", "UCme", 3, "", "", null)
    private fun playlist(id: String, count: Int, next: String? = null) = PlaylistUiState(id,
        PlaylistContent.Ready(PlaylistPage(summary(id), "", (0 until count).map {
            PlaylistEntry("e$it", "p$it", "Entry $it", "Channel", "UC1", "", "2:00", "1K views", "1 day ago")
        }, next)))
    private fun you(page: YouPage) = LibraryUiState(signedIn = true, content = LibraryContent.Ready(page))
    private fun build(
        you: LibraryUiState,
        signedIn: Boolean = true,
        queue: List<QueueItem> = emptyList(),
        watchLater: PlaylistUiState? = playlist("WL", 2),
        liked: PlaylistUiState? = playlist("LL", 0),
    ) = TvLibraryModel.build(you, watchLater, liked, queue, signedIn) { it.key }

    @Test fun signedInShowsHistoryWatchLaterPlaylistsAndLikedInPhoneOrder() {
        val state = build(you(YouPage(account, listOf(entry(1), entry(2)), listOf(summary("PL1")))))
        assertEquals(TvLibraryStatus.Ready, state.status)
        assertEquals(listOf("history", "watch-later", "playlists", "liked"), state.shelves.map { it.key })
        val history = state.shelves[0].state as TvShelfState.Ready
        assertEquals(listOf("v1", "v2"), history.videos.map { it.id })
        assertEquals(listOf("v1", "v2", VIEW_ALL_KEY), state.shelves[0].focusKeys())
        assertEquals(listOf("PL1"), (state.shelves[2].state as TvShelfState.Ready).videos.map { it.id })
        assertTrue(state.shelves[3].state is TvShelfState.Empty)
    }

    @Test fun likedIsAccountOnlyAndQueueAppearsOnlyWithItems() {
        val page = YouPage(account, emptyList(), emptyList())
        assertFalse(build(you(page), signedIn = false).shelves.any { it.key == TvLibraryShelf.Liked.key })
        assertFalse(build(you(page)).shelves.any { it.key == TvLibraryShelf.Queue.key })
        val queued = build(you(page), queue = listOf(QueueItem("q1", "", "Queued"), QueueItem("", "", "No id")))
        assertEquals(TvLibraryShelf.Queue.key, queued.shelves.last().key)
        assertEquals(listOf("q1"), (queued.shelves.last().state as TvShelfState.Ready).videos.map { it.id })
    }

    @Test fun shelvesFailAndLoadIndependently() {
        val page = YouPage(account, emptyList(), emptyList(), historyFailed = true)
        val state = build(you(page).copy(playlistsRetrying = true),
            watchLater = PlaylistUiState("WL", PlaylistContent.Failed), liked = null)
        assertEquals(TvShelfState.Failed, state.shelves[0].state)
        assertEquals(TvShelfState.Failed, state.shelves[1].state)
        assertEquals(TvShelfState.Loading, state.shelves[2].state)
        assertEquals(TvShelfState.Loading, state.shelves[3].state)
    }

    @Test fun rowsStopAtTheCapAndDropDuplicates() {
        val many = (0 until 30).map(::entry) + entry(1)
        val history = build(you(YouPage(account, many, emptyList()))).shelves[0].state as TvShelfState.Ready
        assertEquals(TvLibraryModel.ROW_MAX, history.videos.size)
        assertEquals(history.videos.size, history.videos.map { it.id }.toSet().size)
    }

    @Test fun libraryStatusFollowsTheYouPage() {
        assertEquals(TvLibraryStatus.Loading, build(LibraryUiState()).status)
        assertEquals(TvLibraryStatus.SignedOut, build(LibraryUiState(content = LibraryContent.SignedOut(SignInPrompt("", "", "")))).status)
        assertEquals(TvLibraryStatus.Failed, build(LibraryUiState(content = LibraryContent.Failed)).status)
    }

    @Test fun historyPagePagesAndOffersRetryOrLoadMore() {
        val ready = HistoryUiState(content = HistoryContent.Ready(HistoryPage(listOf(entry(1)), nextKey = "next")))
        TvLibraryModel.history(ready).let {
            assertEquals(TvPageStatus.Ready, it.status)
            assertTrue(it.canPage)
            assertEquals(TvGridFooter.None, it.footer)
        }
        assertEquals(TvGridFooter.Loading, TvLibraryModel.history(ready.copy(append = AppendState.Loading)).footer)
        TvLibraryModel.history(ready.copy(append = AppendState.Failed)).let {
            assertEquals(TvGridFooter.Retry, it.footer)
            assertFalse(it.canPage)
        }
        val emptyButMore = HistoryUiState(content = HistoryContent.Ready(HistoryPage(emptyList(), nextKey = "next")))
        assertEquals(TvGridFooter.LoadMore, TvLibraryModel.history(emptyButMore).footer)
        assertEquals(TvPageStatus.Empty, TvLibraryModel.history(HistoryUiState(content = HistoryContent.Empty())).status)
        assertEquals(TvPageStatus.Empty,
            TvLibraryModel.history(HistoryUiState(content = HistoryContent.Ready(HistoryPage(emptyList())))).status)
    }

    @Test fun playlistAndQueuePages() {
        TvLibraryModel.playlist(playlist("WL", 2, next = "n")).let {
            assertEquals(listOf("p0", "p1"), it.items.map { video -> video.id })
            assertTrue(it.canPage)
        }
        assertEquals(TvPageStatus.Failed, TvLibraryModel.playlist(PlaylistUiState("LL", PlaylistContent.Unavailable)).status)
        assertEquals(TvPageStatus.Empty, TvLibraryModel.queue(emptyList()).status)
    }

    @Test fun gridFillsTheWidthWithCardsOfAtLeastTheMinimum() {
        val metrics = TvGridMetrics.of(816.dp, minCard = 180.dp, gap = 20.dp)
        assertEquals(4, metrics.columns)
        assertEquals(189f, metrics.cardWidth.value, 0.01f)
        assertEquals(1, TvGridMetrics.of(100.dp).columns)
    }
}
