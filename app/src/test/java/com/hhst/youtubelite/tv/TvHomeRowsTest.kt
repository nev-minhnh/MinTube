package com.hhst.youtubelite.tv

import com.hhst.youtubelite.R
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.HomeCatalog
import com.hhst.youtubelite.home.domain.HomeContent
import com.hhst.youtubelite.home.domain.HomePage
import com.hhst.youtubelite.home.domain.HomeUiState
import com.hhst.youtubelite.navigation.ContentLink
import com.hhst.youtubelite.tv.home.TvHomeRows
import com.hhst.youtubelite.tv.home.TvHomeStatus
import com.hhst.youtubelite.tv.home.TvRowKind
import com.hhst.youtubelite.tv.home.TvRowState
import com.hhst.youtubelite.tv.home.TvRowTitle
import com.hhst.youtubelite.tv.watch.TvAutoplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeRowsTest {
    private fun video(id: String, short: Boolean = false) =
        FeedItem.Video(id, "Title $id", "Author", "1K views · 1 day ago", "https://i.ytimg.com/vi/$id/hq.jpg", "1:00", "UC$id", short = short)

    private fun feed(vararg items: FeedItem, next: String? = "next", catalog: HomeCatalog? = null) =
        HomeContent.Feed(HomePage(items.toList(), nextKey = next, catalog = catalog))

    private val chips = listOf("All", "Music", "Gaming", "Live")

    @Test fun mainFeedThenShortsThenOneRowPerOtherChip() {
        val main = HomeUiState(content = feed(video("a"), FeedItem.Shorts("rail", listOf(video("s1", true), video("s2", true))),
            video("b"), video("s3", short = true)), chips = chips)
        val state = TvHomeRows.build(main, mapOf("Music" to HomeUiState(content = feed(video("m1")))), trendingInVietnam = true)

        assertEquals(TvHomeStatus.Ready, state.status)
        assertEquals(listOf("main", "shorts", "chip:Music", "chip:Gaming", "chip:Live"), state.rows.map { it.key })
        val main0 = state.rows[0]
        assertEquals(TvRowKind.Main, main0.kind)
        assertEquals(TvRowTitle.Resource(R.string.tv_row_recommended), main0.title)
        assertEquals(listOf("a", "b"), (main0.state as TvRowState.Ready).videos.map { it.id })
        val shorts = state.rows[1].state as TvRowState.Ready
        assertEquals(listOf("s1", "s2", "s3"), shorts.videos.map { it.id })
        assertTrue(shorts.videos.all { it.short })
        assertEquals(TvRowTitle.Text("Music"), state.rows[2].title)
        assertEquals(listOf("m1"), (state.rows[2].state as TvRowState.Ready).videos.map { it.id })
        // Chips whose ViewModel was not created yet (row not shown) load lazily.
        assertEquals(TvRowState.Loading, state.rows[3].state)
        assertEquals("Gaming", state.rows[3].chip)
    }

    @Test fun cardsCarryThePhoneCardText() {
        val card = TvHomeRows.video(video("a"))
        assertEquals("Author · 1K views · 1 day ago", card.subtitle)
        assertEquals("1:00", card.duration)
    }

    @Test fun signedOutCatalogIsTrendingWithTheRegionTitle() {
        val main = HomeUiState(content = feed(video("t1"), catalog = HomeCatalog.Popular), chips = HomeCatalog.filters)
        val vietnam = TvHomeRows.build(main, emptyMap(), trendingInVietnam = true)
        assertEquals(TvRowTitle.Resource(R.string.locale_trending_vietnam), vietnam.rows[0].title)
        assertEquals(TvRowTitle.Resource(R.string.home_music_charts), vietnam.rows.first { it.chip == "Music charts" }.title)
        assertEquals(TvRowTitle.Resource(R.string.home_popular_gaming), vietnam.rows.first { it.chip == "Gaming" }.title)
        val device = TvHomeRows.build(main, emptyMap(), trendingInVietnam = false)
        assertEquals(TvRowTitle.Resource(R.string.tv_row_trending), device.rows[0].title)
    }

    @Test fun hiddenShortsAndEmptyChipRowsAreDropped() {
        val main = HomeUiState(content = feed(video("a"), FeedItem.Shorts("rail", listOf(video("s1", true)))),
            chips = chips, hideShorts = true)
        val state = TvHomeRows.build(main, mapOf("Music" to HomeUiState(content = HomeContent.Empty())), trendingInVietnam = true)
        assertEquals(listOf("main", "chip:Gaming", "chip:Live"), state.rows.map { it.key })
    }

    @Test fun rowStatesFollowTheViewModel() {
        assertEquals(TvRowState.Loading, TvHomeRows.rowState(HomeContent.Loading, TvHomeRows::videos))
        assertEquals(TvRowState.Failed, TvHomeRows.rowState(HomeContent.Failed, TvHomeRows::videos))
        assertEquals(TvRowState.Loading, TvHomeRows.rowState(HomeContent.Empty(refreshing = true), TvHomeRows::videos))
        val failedAppend = TvHomeRows.rowState(feed(video("a")).copy(append = AppendState.Failed), TvHomeRows::videos)
        assertEquals(TvRowState.Ready(listOf(TvHomeRows.video(video("a"))), AppendState.Failed, hasMore = true), failedAppend)
        val paused = TvHomeRows.rowState(feed(video("a")).copy(emptyAutoLoads = 3), TvHomeRows::videos) as TvRowState.Ready
        assertTrue(paused.pagingPaused)
        val done = TvHomeRows.rowState(feed(video("a"), next = null), TvHomeRows::videos) as TvRowState.Ready
        assertEquals(false, done.hasMore)
        assertEquals(TvRowState.Empty, TvHomeRows.rowState(feed(next = null), TvHomeRows::videos))
    }

    @Test fun wholeScreenStatesComeFromTheMainFeed() {
        assertEquals(TvHomeStatus.Loading, TvHomeRows.build(HomeUiState(content = HomeContent.Loading), emptyMap(), true).status)
        assertEquals(TvHomeStatus.Failed, TvHomeRows.build(HomeUiState(content = HomeContent.Failed), emptyMap(), true).status)
        assertEquals(TvHomeStatus.Empty("No videos", ""),
            TvHomeRows.build(HomeUiState(content = HomeContent.Empty(title = "No videos")), emptyMap(), true).status)
        assertTrue(TvHomeRows.build(HomeUiState(content = HomeContent.Failed), emptyMap(), true).rows.isEmpty())
    }

    @Test fun autoplayCountdownCoversTheLastTenSeconds() {
        fun left(position: Long, duration: Long = 60_000, live: Boolean = false, enabled: Boolean = true,
            candidate: Boolean = true, cancelled: Boolean = false, ended: Boolean = false) =
            TvAutoplay.secondsLeft(position, duration, live, enabled, candidate, cancelled, ended)
        assertNull(left(49_000))
        assertEquals(10, left(50_000))
        assertEquals(10, left(50_001))
        assertEquals(1, left(59_500))
        assertNull(left(60_000))
        assertNull(left(55_000, live = true))
        assertNull(left(55_000, enabled = false))
        assertNull(left(55_000, candidate = false))
        assertNull(left(55_000, cancelled = true))
        assertNull(left(55_000, ended = true))
        assertNull(left(55_000, duration = 0))
    }

    @Test fun linksOpenTheTvWatchScreenOrHome() {
        assertEquals(TvRoute.Watch("dQw4w9WgXcQ", 42), TvRoute.of(ContentLink.parse("https://youtu.be/dQw4w9WgXcQ?t=42")))
        assertEquals(TvRoute.Watch("dQw4w9WgXcQ"), TvRoute.of(ContentLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")))
        assertEquals(TvRoute.Watch("dQw4w9WgXcQ"), TvRoute.of(ContentLink.parse("https://www.youtube.com/shorts/dQw4w9WgXcQ")))
        assertEquals(TvRoute.Home, TvRoute.of(ContentLink.parse("https://www.youtube.com/shorts")))
        assertEquals(TvRoute.Home, TvRoute.of(ContentLink.parse("https://www.youtube.com/@channel")))
        assertEquals(TvRoute.Home, TvRoute.of(null))
    }
}
