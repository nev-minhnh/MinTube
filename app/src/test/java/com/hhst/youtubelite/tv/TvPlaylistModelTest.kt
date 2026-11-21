package com.hhst.youtubelite.tv

import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.playlist.domain.PlaylistContent
import com.hhst.youtubelite.playlist.domain.PlaylistEntry
import com.hhst.youtubelite.playlist.domain.PlaylistEnumeration
import com.hhst.youtubelite.playlist.domain.PlaylistPage
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.playlist.domain.PlaylistUiState
import com.hhst.youtubelite.tv.grid.TvFooter
import com.hhst.youtubelite.tv.grid.TvPaging
import com.hhst.youtubelite.tv.grid.nearEnd
import com.hhst.youtubelite.tv.grid.tvGridColumns
import com.hhst.youtubelite.tv.playlist.TvPlaylistModel
import com.hhst.youtubelite.tv.playlist.TvPlaylistStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaylistModelTest {
    private fun entry(n: Int) = PlaylistEntry("e$n", "vid$n", "Video $n", "Chan", "UCc", "art/$n", "4:00", "1K views", "1 year ago")
    private val summary = PlaylistSummary("PL1", "Mix", "Owner", "UCowner", 3, "", "", null)
    private fun state(
        content: PlaylistContent,
        append: AppendState = AppendState.Idle,
        enumeration: PlaylistEnumeration = PlaylistEnumeration.Idle,
    ) = PlaylistUiState("PL1", content = content, append = append, enumeration = enumeration)

    @Test fun readyPageHasHeaderRowsAndPaging() {
        val page = PlaylistPage(summary, "", (1..3).map(::entry), nextKey = "more")
        val ready = TvPlaylistModel.status(state(PlaylistContent.Ready(page), enumeration = PlaylistEnumeration.Running(40, null)))
            as TvPlaylistStatus.Ready
        assertEquals("Mix", ready.header.title)
        assertEquals("Owner", ready.header.owner)
        assertEquals("3 videos", ready.header.count)
        // A playlist without its own cover shows its first video.
        assertEquals("art/1", ready.header.artwork)
        assertEquals(listOf("e1", "e2", "e3"), ready.rows.map { it.entryId })
        assertEquals("vid1", ready.rows[0].video.id)
        assertTrue(ready.paging.canAutoLoad)
        assertEquals(40, ready.enumerating)
        val failed = TvPlaylistModel.status(state(PlaylistContent.Ready(page), AppendState.Failed)) as TvPlaylistStatus.Ready
        assertEquals(TvFooter.Retry, failed.paging.footer)
        assertNull(failed.enumerating)
    }

    @Test fun otherContentStatesPassThrough() {
        assertEquals(TvPlaylistStatus.Loading, TvPlaylistModel.status(state(PlaylistContent.Loading)))
        assertEquals(TvPlaylistStatus.Failed, TvPlaylistModel.status(state(PlaylistContent.Failed)))
        assertEquals(TvPlaylistStatus.Unavailable, TvPlaylistModel.status(state(PlaylistContent.Unavailable)))
    }

    @Test fun queueKeepsPlaylistOrderAndMetadata() {
        val entries = (1..3).map(::entry)
        val queue = TvPlaylistModel.queue(entries)
        assertEquals(listOf("vid1", "vid2", "vid3"), queue.map { it.videoId })
        assertEquals("Video 2", queue[1].title)
        val played = TvPlaylistModel.queue(entries, listOf("vid2", "vid3", "vid9"), shuffle = false)
        assertEquals(listOf("vid2", "vid3", "vid9"), played.map { it.videoId })
        assertEquals("Chan", played[0].author)
        val shuffled = TvPlaylistModel.queue(entries, listOf("vid1", "vid2", "vid3"), shuffle = true)
        assertEquals(setOf("vid1", "vid2", "vid3"), shuffled.map { it.videoId }.toSet())
    }

    @Test fun gridHelpers() {
        assertEquals(3, tvGridColumns(720.dp, 216.dp, 20.dp))
        assertEquals(1, tvGridColumns(100.dp, 216.dp, 20.dp))
        assertTrue(nearEnd(lastVisible = 14, total = 20, ahead = 6))
        assertFalse(nearEnd(lastVisible = 10, total = 20, ahead = 6))
        assertFalse(nearEnd(lastVisible = null, total = 20, ahead = 6))
        assertEquals(TvFooter.None, TvPaging(hasMore = false).footer)
        assertEquals(TvFooter.Loading, TvPaging(hasMore = true, append = AppendState.Loading).footer)
        assertFalse(TvPaging(hasMore = true, append = AppendState.Loading).canAutoLoad)
    }
}
