package com.hhst.youtubelite.tv

import com.hhst.youtubelite.channel.domain.ChannelLiveVideo
import com.hhst.youtubelite.channel.domain.ChannelShort
import com.hhst.youtubelite.channel.domain.ChannelVideo
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.HomeContent
import com.hhst.youtubelite.home.domain.HomePage
import com.hhst.youtubelite.library.domain.HistoryEntry
import com.hhst.youtubelite.playlist.domain.PlaylistEntry
import com.hhst.youtubelite.search.domain.SearchResult
import com.hhst.youtubelite.tv.channel.TvChannelModel
import com.hhst.youtubelite.tv.home.TvHomeRows
import com.hhst.youtubelite.tv.home.TvRowState
import com.hhst.youtubelite.tv.library.TvLibraryModel
import com.hhst.youtubelite.tv.playlist.TvPlaylistModel
import com.hhst.youtubelite.tv.search.TvSearchModel
import com.hhst.youtubelite.tv.watch.toTvVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every TV video card carries channel, views and publish time from the shared model. */
class TvCardMappersTest {
    private fun home(metadata: String, published: String) = FeedItem.Video(
        "a", "Title", "Chan", metadata, "art", "1:00", "UC1", published = published)

    private fun ready(item: FeedItem.Video) =
        TvHomeRows.rowState(HomeContent.Feed(HomePage(listOf(item), nextKey = null)), TvHomeRows::videos) as TvRowState.Ready

    @Test fun homeCardCarriesViewsAndPublished() {
        val card = TvHomeRows.video(home("1K views", "2 days ago"))
        assertEquals("Chan", card.channel)
        assertEquals("1K views", card.metadata)
        assertEquals("2 days ago", card.published)
    }

    @Test fun laterRowUpdatesAreReflectedByTheNextMapping() {
        val before = ready(home("", "")).videos.single()
        assertEquals("", before.metadata)
        assertEquals("", before.published)
        // The shared repair rewrites the same row in place; mapping the new state shows it.
        val after = ready(home("5K views", "3 hours ago")).videos.single()
        assertEquals(before.id, after.id)
        assertEquals("5K views", after.metadata)
        assertEquals("3 hours ago", after.published)
    }

    @Test fun searchCarriesViewsAndAge() {
        val card = TvSearchModel.video(SearchResult("s", "T", "Chan", "UC1", "2M views", "1 year ago", "3:00", "art"))
        assertEquals("Chan", card.channel)
        assertEquals("2M views", card.metadata)
        assertEquals("1 year ago", card.published)
    }

    @Test fun historyKeepsPublishedWhenTheSourceHasIt() {
        val card = TvLibraryModel.video(HistoryEntry("h", "v", "T", "Chan", "UC1", "art", "1:00", section = "Today",
            views = "9 views", published = "4 days ago"))
        assertEquals("Chan", card.channel)
        assertEquals("9 views", card.metadata)
        assertEquals("4 days ago", card.published)
    }

    @Test fun playlistEntriesCarryViewsAndAge() {
        val entry = PlaylistEntry("e", "p", "T", "Chan", "UC1", "art", "2:00", "1K views", "1 day ago")
        listOf(TvLibraryModel.video(entry), TvPlaylistModel.video(entry)).forEach {
            assertEquals("Chan", it.channel)
            assertEquals("1K views", it.metadata)
            assertEquals("1 day ago", it.published)
        }
    }

    @Test fun channelTabsShowTheChannelNameAndStats() {
        val video = TvChannelModel.video(ChannelVideo("v", "T", "1K views", "1 day ago", "1:00", "art"), "Blender")
        assertEquals(listOf("Blender", "1K views", "1 day ago"), listOf(video.channel, video.metadata, video.published))
        val short = TvChannelModel.short(ChannelShort("s", "T", "7K views", "art"), "Blender")
        assertEquals(listOf("Blender", "7K views"), listOf(short.channel, short.metadata))
        val live = TvChannelModel.live(ChannelLiveVideo("l", "T", "3K views", "Streamed 1 day ago", "1:00", "art"), "Blender")
        assertEquals(listOf("Blender", "3K views", "Streamed 1 day ago"), listOf(live.channel, live.metadata, live.published))
    }

    @Test fun upNextRelatedCarriesViewsAndAge() {
        val card = com.hhst.youtubelite.watch.domain.WatchRelated("r", "T", "Chan", "UC1", "1K views", "2 days ago", "1:00", "art")
            .toTvVideo()
        assertEquals("1K views", card.metadata)
        assertEquals("2 days ago", card.published)
        assertTrue(card.channel == "Chan")
    }
}
