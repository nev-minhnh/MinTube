package com.hhst.youtubelite.tv

import com.hhst.youtubelite.channel.domain.ChannelAction
import com.hhst.youtubelite.channel.domain.ChannelContent
import com.hhst.youtubelite.channel.domain.ChannelLivePage
import com.hhst.youtubelite.channel.domain.ChannelLiveVideo
import com.hhst.youtubelite.channel.domain.ChannelPage
import com.hhst.youtubelite.channel.domain.ChannelProfile
import com.hhst.youtubelite.channel.domain.ChannelSection
import com.hhst.youtubelite.channel.domain.ChannelShort
import com.hhst.youtubelite.channel.domain.ChannelTab
import com.hhst.youtubelite.channel.domain.ChannelUiState
import com.hhst.youtubelite.channel.domain.ChannelVideo
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.tv.channel.TvChannelActivity
import com.hhst.youtubelite.tv.channel.TvChannelCardKind
import com.hhst.youtubelite.tv.channel.TvChannelGrid
import com.hhst.youtubelite.tv.channel.TvChannelModel
import com.hhst.youtubelite.tv.channel.TvChannelStatus
import com.hhst.youtubelite.tv.grid.TvFooter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvChannelModelTest {
    private val profile = ChannelProfile(channelId = "UC1", name = "Blender", handle = "@blender", avatar = "avatar",
        banner = "banner", subscribers = "1.2M subscribers", description = "", subscribed = false)
    private fun video(id: String) = ChannelVideo(id, "Video $id", "1K views", "1 day ago", "10:00", "art/$id")
    private val page = ChannelPage(profile, (1..3).map { video("v$it") }, videosNextKey = "next")

    private fun state(
        tab: ChannelTab = ChannelTab.Videos,
        content: ChannelContent = ChannelContent.Ready(page),
        playlists: ChannelSection<List<PlaylistSummary>> = ChannelSection.Loading,
    ) = ChannelUiState(channelId = "UC1", content = content, selectedTab = tab, playlists = playlists)

    @Test fun tabsAreTheGridTabsTheDataProvides() {
        assertEquals(listOf(ChannelTab.Videos, ChannelTab.Shorts, ChannelTab.Live, ChannelTab.Playlists),
            TvChannelModel.tabs(state(), hideShorts = false))
        assertEquals(listOf(ChannelTab.Videos, ChannelTab.Live),
            TvChannelModel.tabs(state(playlists = ChannelSection.Empty), hideShorts = true))
    }

    @Test fun phoneOnlyTabsFallBackToVideos() {
        val tabs = TvChannelModel.tabs(state(), false)
        assertEquals(ChannelTab.Videos, TvChannelModel.selected(state(tab = ChannelTab.Home), tabs))
        assertEquals(ChannelTab.Videos, TvChannelModel.selected(state(tab = ChannelTab.Posts), tabs))
        assertEquals(ChannelTab.Live, TvChannelModel.selected(state(tab = ChannelTab.Live), tabs))
    }

    @Test fun headerCarriesIdentityAndSubscribeState() {
        val status = TvChannelModel.status(state().copy(subscribePending = true), false) as TvChannelStatus.Ready
        assertEquals("Blender", status.header.name)
        assertEquals("@blender · 1.2M subscribers", status.header.subtitle)
        assertEquals(false, status.header.subscribed)
        assertTrue(status.header.subscribePending)
        assertNull(TvChannelModel.header(profile.copy(banner = " "), false).banner)
    }

    @Test fun videosTabPagesThroughTheViewModel() {
        val grid = TvChannelModel.grid(state().copy(videosAppend = AppendState.Failed), ChannelTab.Videos) as TvChannelGrid.Ready
        assertEquals(listOf("Video:v1", "Video:v2", "Video:v3"), grid.cards.map { it.key })
        assertEquals("Blender · 1K views · 1 day ago", grid.cards[0].video.subtitle)
        assertEquals(TvFooter.Retry, grid.paging.footer)
        val deferred = state().copy(videosTab = ChannelSection.Loading)
        assertEquals(TvChannelGrid.Loading, TvChannelModel.grid(deferred, ChannelTab.Videos))
    }

    @Test fun sectionsMapToTheirCardKinds() {
        val loaded = state().copy(
            shorts = ChannelSection.Ready(listOf(ChannelShort("s1", "Short", "5K views", "art"))),
            shortsNextKey = "more",
            live = ChannelSection.Ready(ChannelLivePage(
                current = listOf(ChannelLiveVideo("l1", "Now", "", "", "", "art", liveNow = true)),
                past = listOf(ChannelLiveVideo("l2", "Past", "2K views", "1 week ago", "1:00:00", "art")))),
            playlists = ChannelSection.Ready(listOf(PlaylistSummary("p1", "List", "Blender", "UC1", 4, "art", "", null))),
        )
        val shorts = TvChannelModel.grid(loaded, ChannelTab.Shorts) as TvChannelGrid.Ready
        assertEquals(TvChannelCardKind.Short, shorts.cards.single().kind)
        assertTrue(shorts.cards.single().video.short)
        assertTrue(shorts.paging.canAutoLoad)
        val live = TvChannelModel.grid(loaded, ChannelTab.Live) as TvChannelGrid.Ready
        assertEquals(listOf(true, false), live.cards.map { it.video.live })
        val lists = TvChannelModel.grid(loaded, ChannelTab.Playlists) as TvChannelGrid.Ready
        assertEquals(TvChannelCardKind.Playlist, lists.cards.single().kind)
        assertEquals("4 videos", lists.cards.single().video.duration)
    }

    @Test fun sectionStatesPassThrough() {
        assertEquals(TvChannelGrid.Failed, TvChannelModel.grid(state().copy(live = ChannelSection.Failed), ChannelTab.Live))
        assertEquals(TvChannelGrid.Empty, TvChannelModel.grid(state().copy(shorts = ChannelSection.Empty), ChannelTab.Shorts))
        assertEquals(TvChannelGrid.Empty, TvChannelModel.grid(state(content = ChannelContent.Ready(page.copy(videos = emptyList(),
            videosNextKey = null))), ChannelTab.Videos))
        assertEquals(TvChannelStatus.Loading, TvChannelModel.status(state(content = ChannelContent.Loading), false))
        assertEquals(TvChannelStatus.Failed, TvChannelModel.status(state(content = ChannelContent.Failed), false))
    }

    @Test fun eachTabPagesWithItsOwnAction() {
        assertEquals(ChannelAction.LoadVideosNext, TvChannelActivity.loadNext(ChannelTab.Videos))
        assertEquals(ChannelAction.LoadShortsNext, TvChannelActivity.loadNext(ChannelTab.Shorts))
        assertEquals(ChannelAction.LoadLiveNext, TvChannelActivity.loadNext(ChannelTab.Live))
        assertEquals(ChannelAction.LoadPlaylistsNext, TvChannelActivity.loadNext(ChannelTab.Playlists))
    }
}
