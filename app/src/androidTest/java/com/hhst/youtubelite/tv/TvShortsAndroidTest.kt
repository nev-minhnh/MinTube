package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.shorts.TAG_SHORTS_LIKE
import com.hhst.youtubelite.tv.shorts.TAG_SHORTS_PLAYBACK_RETRY
import com.hhst.youtubelite.tv.shorts.TAG_SHORTS_RETRY
import com.hhst.youtubelite.tv.shorts.TAG_SHORTS_VIDEO
import com.hhst.youtubelite.tv.shorts.TvShortInfo
import com.hhst.youtubelite.tv.shorts.TvShortPlayback
import com.hhst.youtubelite.tv.shorts.TvShortsCallbacks
import com.hhst.youtubelite.tv.shorts.TvShortsScreen
import com.hhst.youtubelite.tv.shorts.TvShortsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Shorts player's remote keys on a fake player (no engine, no network). */
class TvShortsAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var status by mutableStateOf(TvShortsStatus.Ready)
    private var playback by mutableStateOf(TvShortPlayback(playing = true, loading = false, durationMs = 30_000, videoReady = true))
    private val position = mutableLongStateOf(10_000L)
    private val calls = mutableListOf<String>()

    private val callbacks = object : TvShortsCallbacks {
        override fun playPause() { calls += "playPause" }
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun seekBy(deltaMs: Long) { calls += "seek:$deltaMs" }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun like() { calls += "like" }
        override fun dislike() { calls += "dislike" }
        override fun retry() { calls += "retry" }
        override fun retryPlayback() { calls += "retryPlayback" }
    }

    private fun host() {
        compose.setContent {
            TvCanvas {
                TvShortsScreen(
                    status = status,
                    info = TvShortInfo("s1", "A short", "Channel", "1K views"),
                    playback = playback,
                    position = position,
                    hasPrevious = true,
                    hasNext = true,
                    canRate = true,
                    callbacks = callbacks,
                )
            }
        }
    }

    @Test fun remoteDrivesTheShort() {
        host()
        compose.awaitFocus(TAG_SHORTS_VIDEO)
        compose.press(Key.DirectionDown)
        compose.press(Key.DirectionUp)
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionLeft)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("next", "previous", "seek:5000", "seek:-5000", "playPause"), calls)
        assertTrue(compose.isFocused(TAG_SHORTS_VIDEO))
    }

    @Test fun backFromTheLikeButtonReturnsToTheVideo() {
        host()
        compose.awaitFocus(TAG_SHORTS_VIDEO)
        compose.onNodeWithTag(TAG_SHORTS_LIKE).requestFocus()
        compose.awaitFocus(TAG_SHORTS_LIKE)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("like"), calls)
        compose.back()
        compose.awaitFocus(TAG_SHORTS_VIDEO)
        assertTrue(!compose.activity.isFinishing)
    }

    @Test fun playbackFailureFocusesRetry() {
        host()
        compose.awaitFocus(TAG_SHORTS_VIDEO)
        playback = playback.copy(failed = true)
        compose.awaitFocus(TAG_SHORTS_PLAYBACK_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("retryPlayback"), calls)
    }

    @Test fun failedFeedOffersRetry() {
        status = TvShortsStatus.Failed
        host()
        compose.awaitFocus(TAG_SHORTS_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("retry"), calls)
    }
}
