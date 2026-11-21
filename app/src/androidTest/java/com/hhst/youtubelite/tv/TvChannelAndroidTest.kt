package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.hhst.youtubelite.channel.domain.ChannelTab
import com.hhst.youtubelite.tv.channel.TAG_CHANNEL_RETRY
import com.hhst.youtubelite.tv.channel.TAG_CHANNEL_SKELETON
import com.hhst.youtubelite.tv.channel.TAG_CHANNEL_SUBSCRIBE
import com.hhst.youtubelite.tv.channel.TAG_CHANNEL_TAB_RETRY
import com.hhst.youtubelite.tv.channel.TAG_CHANNEL_TAB_SKELETON
import com.hhst.youtubelite.tv.channel.TvChannelCard
import com.hhst.youtubelite.tv.channel.TvChannelCardKind
import com.hhst.youtubelite.tv.channel.TvChannelGrid
import com.hhst.youtubelite.tv.channel.TvChannelHeader
import com.hhst.youtubelite.tv.channel.TvChannelScreen
import com.hhst.youtubelite.tv.channel.TvChannelStatus
import com.hhst.youtubelite.tv.channel.channelCardTag
import com.hhst.youtubelite.tv.channel.channelTabTag
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.grid.TvPaging
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** TV channel page driven with the D-pad on fake state. */
class TvChannelAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var status by mutableStateOf<TvChannelStatus>(TvChannelStatus.Loading)
    private val selectedTabs = mutableListOf<ChannelTab>()
    private val opened = mutableListOf<String>()
    private val retriedTabs = mutableListOf<ChannelTab>()
    private var subscribes = 0
    private var retries = 0

    private val header = TvChannelHeader("Blender", "@blender", "1M subscribers", "", null, subscribed = false, subscribePending = false)
    private val tabs = listOf(ChannelTab.Videos, ChannelTab.Shorts, ChannelTab.Live)

    private fun videos(count: Int = 6) = TvChannelGrid.Ready(
        (0 until count).map { TvChannelCard(TvChannelCardKind.Video, TvVideo("v$it", "Video $it", "Blender", "1K views")) },
        TvPaging(hasMore = false))

    private fun ready(selected: ChannelTab = ChannelTab.Videos, grid: TvChannelGrid = videos()) =
        TvChannelStatus.Ready(header, tabs, selected, grid)

    private fun host(initial: TvChannelStatus) {
        status = initial
        compose.setContent {
            TvCanvas {
                TvChannelScreen(
                    status = status,
                    onSelectTab = { tab ->
                        selectedTabs += tab
                        status = ready(tab, if (tab == ChannelTab.Videos) videos() else TvChannelGrid.Loading)
                    },
                    onSubscribe = { subscribes++ },
                    onOpen = { opened += it.key },
                    onLoadMore = {},
                    onRetryTab = { retriedTabs += it },
                    onRetry = { retries++ },
                )
            }
        }
    }

    @Test fun selectedTabTakesFirstFocusAndTabsSwitchOnFocus() {
        host(ready())
        compose.awaitFocus(channelTabTag(ChannelTab.Videos))
        compose.press(Key.DirectionRight)
        compose.awaitFocus(channelTabTag(ChannelTab.Shorts))
        assertEquals(listOf(ChannelTab.Shorts), selectedTabs)
        compose.onNodeWithTag(TAG_CHANNEL_TAB_SKELETON).assertExists()
        compose.press(Key.DirectionLeft)
        compose.awaitFocus(channelTabTag(ChannelTab.Videos))
        assertEquals(listOf(ChannelTab.Shorts, ChannelTab.Videos), selectedTabs)
    }

    @Test fun downEntersTheGridAndUpReachesSubscribe() {
        host(ready())
        compose.awaitFocus(channelTabTag(ChannelTab.Videos))
        compose.press(Key.DirectionDown)
        compose.awaitFocus(channelCardTag(0))
        compose.press(Key.DirectionRight)
        compose.awaitFocus(channelCardTag(1))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("Video:v1"), opened)
        compose.press(Key.DirectionUp)
        compose.waitUntil(5_000) { tabs.any { compose.isFocused(channelTabTag(it)) } }
        compose.press(Key.DirectionUp)
        compose.awaitFocus(TAG_CHANNEL_SUBSCRIBE)
        compose.press(Key.DirectionCenter)
        assertEquals(1, subscribes)
    }

    @Test fun anEmptyTabOffersAFocusableRetry() {
        host(ready(grid = TvChannelGrid.Empty))
        compose.awaitFocus(channelTabTag(ChannelTab.Videos))
        compose.press(Key.DirectionDown)
        compose.awaitFocus(TAG_CHANNEL_TAB_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf(ChannelTab.Videos), retriedTabs)
        // The message leaves while the tab reloads; focus parks on the tab.
        compose.awaitFocus(channelTabTag(ChannelTab.Videos))
    }

    @Test fun loadingShowsASkeletonAndAFailedPageOffersRetry() {
        host(TvChannelStatus.Loading)
        compose.onNodeWithTag(TAG_CHANNEL_SKELETON).assertExists()
        status = TvChannelStatus.Failed
        compose.awaitFocus(TAG_CHANNEL_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(1, retries)
        status = ready()
        compose.awaitFocus(channelTabTag(ChannelTab.Videos))
    }
}
