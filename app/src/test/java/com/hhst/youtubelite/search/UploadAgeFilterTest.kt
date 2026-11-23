package com.hhst.youtubelite.search

import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.search.domain.*
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class UploadAgeFilterTest {
    private val now = Instant.parse("2026-11-23T12:00:00Z")
    private fun video(id: String, age: String) = SearchResult(id, id, "c", "cid", "1 view", age, "1:00", "")
    private fun page(vararg r: SearchResult, cursor: String? = null, shorts: List<SearchResult> = emptyList(),
        playlists: List<PlaylistSummary> = emptyList(), channels: List<SearchChannel> = emptyList()) =
        SearchPage("q", r.toList(), playlists, channels, shorts, cursor)

    @Test fun anytimeIsUntouched() {
        val p = page(video("a", "5 years ago"))
        assertSame(p, p.filteredByUpload(SearchUploadDate.Anytime, now))
    }

    @Test fun dropsOldVideosAndShortsKeepsUnparsable() {
        val p = page(video("new", "3 hours ago"), video("edge", "1 day ago"), video("old", "2 days ago"),
            video("blank", ""), video("odd", "LIVE"),
            shorts = listOf(video("s1", "1 hour ago"), video("s2", "2 weeks ago")))
        val today = p.filteredByUpload(SearchUploadDate.Today, now)
        assertEquals(listOf("new", "edge", "blank", "odd"), today.results.map { it.id })
        assertEquals(listOf("s1"), today.shorts.map { it.id })
        val week = p.filteredByUpload(SearchUploadDate.ThisWeek, now)
        assertEquals(listOf("new", "edge", "old", "blank", "odd"), week.results.map { it.id })
    }

    @Test fun limitsPerOption() {
        val ages = listOf("6 days ago", "1 week ago", "2 weeks ago", "1 month ago", "2 months ago", "1 year ago", "2 years ago")
        fun keep(d: SearchUploadDate) = page(*ages.map { video(it, it) }.toTypedArray())
            .filteredByUpload(d, now).results.map { it.id }
        assertEquals(ages.take(2), keep(SearchUploadDate.ThisWeek))
        assertEquals(ages.take(4), keep(SearchUploadDate.ThisMonth))
        assertEquals(ages.take(6), keep(SearchUploadDate.ThisYear))
    }

    @Test fun channelsAndPlaylistsAreKept() {
        val ch = SearchChannel("c", "n", "", "", "")
        val pl = PlaylistSummary("p", "t", "a", "c", null, "", "", null)
        val out = page(video("old", "3 years ago"), playlists = listOf(pl), channels = listOf(ch))
            .filteredByUpload(SearchUploadDate.Today, now)
        assertEquals(emptyList<SearchResult>(), out.results)
        assertEquals(listOf(pl), out.playlists)
        assertEquals(listOf(ch), out.channels)
    }

    @Test fun uploadedDaysRoundUp() {
        assertEquals(1, uploadedDaysOf("3 hours ago", now))
        assertEquals(5, uploadedDaysOf("5 days ago", now))
        assertEquals(21, uploadedDaysOf("3 weeks ago", now))
        assertNull(uploadedDaysOf("", now))
    }

    @Test fun emptyPageFollowsContinuation() = runBlocking {
        val fetched = mutableListOf<String>()
        val first = page(video("old0", "3 years ago"), cursor = "c1")
        val out = filterUploadPaging(first, SearchUploadDate.Today, now) { cursor ->
            fetched += cursor
            if (cursor == "c1") page(video("old1", "2 years ago"), cursor = "c2")
            else page(video("fresh", "2 hours ago"), cursor = "c3")
        }
        assertEquals(listOf("c1", "c2"), fetched)
        assertEquals(listOf("fresh"), out.results.map { it.id })
        assertEquals("c3", out.nextCursor)
    }

    @Test fun pagingIsBoundedAndKeepsCursor() = runBlocking {
        var calls = 0
        val out = filterUploadPaging(page(video("o", "3 years ago"), cursor = "c0"), SearchUploadDate.Today, now) {
            calls++
            page(video("o$calls", "3 years ago"), cursor = "c$calls")
        }
        assertEquals(3, calls)
        assertEquals(emptyList<SearchResult>(), out.results)
        assertEquals("c3", out.nextCursor)
    }

    @Test fun noFetchWhenNotNeeded() = runBlocking {
        var calls = 0
        val fetch: (String) -> SearchPage = { calls++; page() }
        filterUploadPaging(page(video("n", "1 hour ago"), cursor = "c"), SearchUploadDate.Today, now, fetch = fetch)
        val end = filterUploadPaging(page(video("o", "3 years ago")), SearchUploadDate.Today, now, fetch = fetch)
        assertEquals(0, calls)
        assertNull(end.nextCursor)
    }

    @Test fun continuationPageIsFilteredToo() = runBlocking {
        val out = filterUploadPaging(page(video("old", "3 years ago"), video("new", "1 hour ago"), cursor = "c"),
            SearchUploadDate.ThisWeek, now) { error("no fetch expected") }
        assertEquals(listOf("new"), out.results.map { it.id })
        assertEquals("c", out.nextCursor)
    }
}
