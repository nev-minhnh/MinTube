package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.subscriptions.TAG_CHANNELS_RETRY
import com.hhst.youtubelite.tv.subscriptions.TAG_SUBSCRIPTIONS_RETRY
import com.hhst.youtubelite.tv.subscriptions.TAG_SUBSCRIPTIONS_SIGN_IN
import com.hhst.youtubelite.tv.subscriptions.TvChannel
import com.hhst.youtubelite.tv.subscriptions.TvChannelStrip
import com.hhst.youtubelite.tv.subscriptions.TvSubscriptionsScreen
import com.hhst.youtubelite.tv.subscriptions.TvSubscriptionsStatus
import com.hhst.youtubelite.tv.subscriptions.TvSubscriptionsUiState
import com.hhst.youtubelite.tv.subscriptions.channelTag
import com.hhst.youtubelite.tv.subscriptions.subscriptionCardTag
import com.hhst.youtubelite.tv.shell.TvShell
import com.hhst.youtubelite.tv.shell.rememberTvShellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Subscriptions inside the shell on fake state: channel strip, feed grid, signed-out and failure states. */
class TvSubscriptionsAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(TvSubscriptionsUiState(TvSubscriptionsStatus.Loading))
    private val videos = mutableListOf<String>()
    private val channels = mutableListOf<String>()
    private var signIns = 0
    private var retries = 0
    private var channelRetries = 0

    private fun ready(strip: TvChannelStrip = TvChannelStrip.Ready((0 until 4).map { TvChannel("UC$it", "Channel $it", "") })) =
        TvSubscriptionsUiState(TvSubscriptionsStatus.Ready, strip, (0 until 8).map { TvVideo("v$it", "Video $it", "Channel") })

    private fun host(initial: TvSubscriptionsUiState) {
        state = initial
        compose.setContent {
            TvCanvas {
                TvShell(rememberTvShellState(), onExit = {}) {
                    TvSubscriptionsScreen(
                        state = state,
                        onOpenVideo = { videos += it.id },
                        onOpenChannel = { channels += it.id },
                        onLoadMore = {},
                        onRetryMore = {},
                        onLoadMoreChannels = {},
                        onRetryChannels = { channelRetries++ },
                        onRetry = { retries++ },
                        onSignIn = { signIns++ },
                    )
                }
            }
        }
    }

    @Test fun channelStripOpensChannelsAndFocusReturnsFromTheFeed() {
        host(ready())
        compose.awaitFocus(channelTag(0))
        compose.press(Key.DirectionRight)
        compose.awaitFocus(channelTag(1))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("UC1"), channels)
        compose.press(Key.DirectionDown)
        compose.waitUntil(5_000) { (0 until 8).any { compose.isFocused(subscriptionCardTag(it)) } }
        compose.press(Key.DirectionCenter)
        assertEquals(1, videos.size)
        compose.press(Key.DirectionUp)
        compose.awaitFocus(channelTag(1))
    }

    @Test fun failedChannelListKeepsTheFeedAndARetry() {
        host(ready(TvChannelStrip.Failed))
        compose.awaitFocus(TAG_CHANNELS_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(1, channelRetries)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(subscriptionCardTag(0))
    }

    @Test fun signedOutOffersSignIn() {
        host(TvSubscriptionsUiState(TvSubscriptionsStatus.SignedOut))
        compose.awaitFocus(TAG_SUBSCRIPTIONS_SIGN_IN)
        compose.press(Key.DirectionCenter)
        assertEquals(1, signIns)
    }

    @Test fun failedFeedOffersRetry() {
        host(TvSubscriptionsUiState(TvSubscriptionsStatus.Failed))
        compose.awaitFocus(TAG_SUBSCRIPTIONS_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(1, retries)
    }
}
