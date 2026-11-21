package com.hhst.youtubelite.tv

import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.search.domain.SearchChannel
import com.hhst.youtubelite.search.domain.SearchContent
import com.hhst.youtubelite.search.domain.SearchFilters
import com.hhst.youtubelite.search.domain.SearchPage
import com.hhst.youtubelite.search.domain.SearchResult
import com.hhst.youtubelite.search.domain.SearchResultType
import com.hhst.youtubelite.search.domain.SearchUiState
import com.hhst.youtubelite.tv.grid.TvFooter
import com.hhst.youtubelite.tv.search.TvSearchItem
import com.hhst.youtubelite.tv.search.TvSearchModel
import com.hhst.youtubelite.tv.search.TvSearchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSearchModelTest {
    private fun result(id: String, badge: String? = null) =
        SearchResult(id, "Title $id", "Channel", "UC$id", "1K views", "2 days ago", "3:00", "art/$id", badge = badge)

    private fun channel(id: String) = SearchChannel(id, "Name $id", "@$id", "10K subscribers", "avatar/$id")
    private fun playlist(id: String) = PlaylistSummary(id, "List $id", "Owner", "UCowner", 12, "art/$id", "", null)

    private val page = SearchPage(
        "cats",
        results = (1..10).map { result("v$it") },
        playlists = listOf(playlist("p1")),
        channels = (1..4).map { channel("c$it") },
        shorts = listOf(result("s1"), result("s2")),
        nextCursor = "next",
    )

    private fun state(content: SearchContent, type: SearchResultType = SearchResultType.All, query: String = "cats") =
        SearchUiState(query = query, content = content, filters = SearchFilters(type))

    @Test fun allMixesLeadingChannelsVideosPlaylistsThenTheRestAndLeavesShortsOut() {
        val keys = TvSearchModel.items(page, SearchResultType.All).map { it.key }
        val expected = listOf("c:c1", "c:c2", "c:c3") + (1..8).map { "v:v$it" } + "p:p1" + listOf("v:v9", "v:v10")
        assertEquals(expected, keys)
    }

    @Test fun eachFilterShowsOnlyItsKind() {
        val shorts = TvSearchModel.items(page, SearchResultType.Shorts)
        assertEquals(listOf("v:s1", "v:s2"), shorts.map { it.key })
        assertTrue(shorts.all { (it as TvSearchItem.Video).video.short })
        assertEquals(4, TvSearchModel.items(page, SearchResultType.Channels).filterIsInstance<TvSearchItem.Channel>().size)
        assertEquals(listOf("p:p1"), TvSearchModel.items(page, SearchResultType.Playlists).map { it.key })
    }

    @Test fun cardsCarryTheResultText() {
        val video = TvSearchModel.video(result("a", badge = "LIVE"))
        assertEquals("Channel · 1K views · 2 days ago", video.subtitle)
        assertTrue(video.live)
        assertFalse(TvSearchModel.video(result("b", badge = "4K")).live)
        val tile = TvSearchModel.channel(channel("x"))
        assertEquals("@x · 10K subscribers", tile.detail)
        val list = TvSearchModel.playlist(playlist("p")).card
        assertEquals("12 videos", list.duration)
        assertEquals("Owner", list.channel)
    }

    @Test fun statusFollowsTheViewModelContent() {
        assertEquals(TvSearchStatus.Idle, TvSearchModel.results(state(SearchContent.Empty(""), query = "")).status)
        assertEquals(TvSearchStatus.Loading, TvSearchModel.results(state(SearchContent.Loading)).status)
        assertEquals(TvSearchStatus.Failed, TvSearchModel.results(state(SearchContent.Failed)).status)
        assertEquals(TvSearchStatus.Empty("cats", SearchResultType.Shorts),
            TvSearchModel.results(state(SearchContent.Empty("cats"), SearchResultType.Shorts)).status)
        val ready = TvSearchModel.results(state(SearchContent.Results(page)))
        assertEquals(TvSearchStatus.Ready, ready.status)
        assertTrue(ready.paging.canAutoLoad)
        assertEquals(TvFooter.None, ready.paging.footer)
    }

    @Test fun pagingStatesBecomeFooters() {
        assertEquals(TvFooter.Loading, TvSearchModel.results(state(SearchContent.Results(page, loadingMore = true))).paging.footer)
        assertEquals(TvFooter.Retry, TvSearchModel.results(state(SearchContent.Results(page, loadMoreFailed = true))).paging.footer)
        val paused = TvSearchModel.results(state(SearchContent.Results(page, emptyAutoLoads = 3))).paging
        assertEquals(TvFooter.LoadMore, paused.footer)
        assertFalse(paused.canAutoLoad)
        assertEquals(AppendState.Idle, paused.append)
    }

    @Test fun aFilterWithNothingLoadedIsEmptyUnlessMoreCanLoad() {
        val noPlaylists = page.copy(playlists = emptyList(), nextCursor = null)
        assertEquals(TvSearchStatus.Empty("cats", SearchResultType.Playlists),
            TvSearchModel.results(state(SearchContent.Results(noPlaylists), SearchResultType.Playlists)).status)
        val more = TvSearchModel.results(state(SearchContent.Results(page.copy(playlists = emptyList())), SearchResultType.Playlists))
        assertEquals(TvSearchStatus.Ready, more.status)
        assertTrue(more.items.isEmpty())
        assertTrue(more.paging.canAutoLoad)
    }

    @Test fun hiddenShortsRemoveTheirFilter() {
        assertEquals(SearchResultType.entries, TvSearchModel.types(hideShorts = false))
        assertFalse(SearchResultType.Shorts in TvSearchModel.types(hideShorts = true))
    }

    @Test fun visibleIdsAreVideosInTheWindowPlusLookAhead() {
        val items = TvSearchModel.items(page, SearchResultType.All)
        // Cells 0..2 are channels; the look-ahead of 3 reaches the first three videos.
        assertEquals(listOf("v1", "v2", "v3"), TvSearchModel.visibleVideoIds(items, 0, 2))
        assertEquals(emptyList<String>(), TvSearchModel.visibleVideoIds(items, 0, -1))
        assertEquals(listOf("v9", "v10"), TvSearchModel.visibleVideoIds(items, 12, 40))
    }
}
