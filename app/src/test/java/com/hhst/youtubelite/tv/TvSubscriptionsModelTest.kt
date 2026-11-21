package com.hhst.youtubelite.tv

import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.library.domain.SignInPrompt
import com.hhst.youtubelite.subscriptions.domain.SubscribedChannel
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsChannels
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsContent
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsPage
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsUiState
import com.hhst.youtubelite.tv.library.TvGridFooter
import com.hhst.youtubelite.tv.subscriptions.TvChannelStrip
import com.hhst.youtubelite.tv.subscriptions.TvSubscriptionsModel
import com.hhst.youtubelite.tv.subscriptions.TvSubscriptionsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSubscriptionsModelTest {
    private fun video(id: String, short: Boolean = false) =
        FeedItem.Video(id, "Video $id", "Channel", "1K views", "", "3:00", "UC1", short = short)
    private fun channel(id: String) = SubscribedChannel(id, "Channel $id", "", "")
    private fun feed(items: List<FeedItem.Video>, next: String? = null, append: AppendState = AppendState.Idle) =
        SubscriptionsContent.Feed(SubscriptionsPage(items, emptyList(), next), append)
    private fun build(content: SubscriptionsContent) = TvSubscriptionsModel.build(SubscriptionsUiState(signedIn = true, content = content))

    @Test fun statusFollowsTheFeed() {
        assertEquals(TvSubscriptionsStatus.Loading, TvSubscriptionsModel.build(SubscriptionsUiState()).status)
        assertEquals(TvSubscriptionsStatus.SignedOut, build(SubscriptionsContent.SignedOut(SignInPrompt("", "", ""))).status)
        assertEquals(TvSubscriptionsStatus.Failed, build(SubscriptionsContent.Failed).status)
    }

    @Test fun readyFeedHasChannelsAndUniformCards() {
        val state = TvSubscriptionsModel.build(SubscriptionsUiState(signedIn = true,
            content = feed(listOf(video("a"), video("s", short = true), video("a")), next = "n"),
            channels = SubscriptionsChannels.Ready(listOf(channel("UC1"), channel("UC1"), channel("UC2")), nextKey = "c")))
        assertEquals(TvSubscriptionsStatus.Ready, state.status)
        assertEquals(listOf("a", "s"), state.videos.map { it.id })
        assertTrue(state.videos.none { it.short })
        assertTrue(state.canPage)
        assertFalse(state.feedEmpty)
        val strip = state.channels as TvChannelStrip.Ready
        assertEquals(listOf("UC1", "UC2"), strip.channels.map { it.id })
        assertTrue(strip.canPage)
    }

    @Test fun pagingFootersAndEmptyFeed() {
        assertEquals(TvGridFooter.Loading, build(feed(listOf(video("a")), "n", AppendState.Loading)).footer)
        build(feed(listOf(video("a")), "n", AppendState.Failed)).let {
            assertEquals(TvGridFooter.Retry, it.footer)
            assertFalse(it.canPage)
        }
        build(feed(emptyList(), "n")).let {
            assertEquals(TvGridFooter.LoadMore, it.footer)
            assertFalse(it.feedEmpty)
        }
        assertTrue(build(feed(emptyList())).feedEmpty)
    }

    @Test fun channelStripStates() {
        assertEquals(TvChannelStrip.Loading, TvSubscriptionsModel.channels(SubscriptionsChannels.Loading))
        assertEquals(TvChannelStrip.Failed, TvSubscriptionsModel.channels(SubscriptionsChannels.Failed))
        val loadingMore = TvSubscriptionsModel.channels(
            SubscriptionsChannels.Ready(listOf(channel("x")), nextKey = "c", append = AppendState.Loading)) as TvChannelStrip.Ready
        assertFalse(loadingMore.canPage)
    }
}
