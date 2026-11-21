package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.hhst.youtubelite.player.PlayerUiState
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.watch.TAG_AUTOPLAY
import com.hhst.youtubelite.tv.watch.TAG_OPTION
import com.hhst.youtubelite.tv.watch.TAG_OPTIONS
import com.hhst.youtubelite.tv.watch.TAG_PLAYER
import com.hhst.youtubelite.tv.watch.TAG_PLAYER_RETRY
import com.hhst.youtubelite.tv.watch.TAG_SEEK_BAR
import com.hhst.youtubelite.tv.watch.TAG_SETTING
import com.hhst.youtubelite.tv.watch.TAG_SETTINGS
import com.hhst.youtubelite.tv.watch.TAG_UP_NEXT_CARD
import com.hhst.youtubelite.tv.watch.TvPlayerCallbacks
import com.hhst.youtubelite.tv.watch.TvPlayerScreen
import com.hhst.youtubelite.tv.watch.TvWatchInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Remote keys on the TV player overlay, with a fake player state and recorded commands. */
class TvPlayerAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    // Paused: the test clock skips delays, so a playing state would auto-hide the controls at once.
    private val player = mutableStateOf(PlayerUiState(
        visible = true, prepared = true, isPlaying = false, userWantsPlay = false, videoId = "v0", title = "Test video",
        author = "Test channel", durationMs = 600_000, qualities = listOf("1080p", "720p"),
    ))
    private val position = mutableLongStateOf(60_000)
    private val buffered = mutableLongStateOf(90_000)
    private val seekTarget = mutableStateOf<Long?>(null)
    private val related = (1..4).map { TvVideo("r$it", "Related $it", "Channel") }

    private val callbacks = object : TvPlayerCallbacks {
        override fun playPause() { calls += "playPause" }
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun seekPreview(offsetMs: Long) { calls += "preview:$offsetMs" }
        override fun seekCommit(offsetMs: Long) { calls += "commit:$offsetMs" }
        override fun seekTo(positionMs: Long) { calls += "seekTo:$positionMs" }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun quality(label: String?) { calls += "quality:$label" }
        override fun speed(speed: Float) { calls += "speed:$speed" }
        override fun subtitle(trackKey: String) { calls += "subtitle:$trackKey" }
        override fun subtitlesOff() { calls += "subtitlesOff" }
        override fun audioTrack(trackKey: String?) { calls += "audio:$trackKey" }
        override fun audioMenuOpened() { calls += "audioMenu" }
        override fun openVideo(videoId: String) { calls += "open:$videoId" }
        override fun cancelAutoplay() { calls += "cancelAutoplay" }
        override fun retry() { calls += "retry" }
        override fun loadMoreRelated() = Unit
        override fun exit() { calls += "exit" }
    }

    private fun host() {
        compose.setContent {
            TvCanvas {
                TvPlayerScreen(
                    player = player.value, position = position, buffered = buffered, seekTarget = seekTarget,
                    info = TvWatchInfo("Test video", "Test channel", "1K views"), upNext = related,
                    autoplayEnabled = true, callbacks = callbacks,
                )
            }
        }
        compose.awaitFocus(if (player.value.error == null) TAG_PLAYER else TAG_PLAYER_RETRY)
    }

    @Test fun okTogglesPlaybackAndShowsTheSeekBarWhichSeeksTenSeconds() {
        host()
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("playPause"), calls)
        compose.awaitFocus(TAG_SEEK_BAR)
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionLeft)
        compose.press(Key.DirectionLeft)
        assertEquals(listOf("playPause", "preview:10000", "commit:10000", "preview:-10000", "commit:-10000",
            "preview:-10000", "commit:-10000"), calls)
        // OK on the seek bar toggles again.
        compose.press(Key.DirectionCenter)
        assertEquals("playPause", calls.last())
    }

    @Test fun leftAndRightOnTheVideoSeekAndRevealTheBar() {
        host()
        compose.onNodeWithTag(TAG_SEEK_BAR).assertDoesNotExist()
        compose.press(Key.DirectionLeft)
        assertEquals(listOf("preview:-10000", "commit:-10000"), calls)
        compose.awaitFocus(TAG_SEEK_BAR)
    }

    @Test fun upOpensTheSettingsRowAndPicksAQuality() {
        host()
        compose.press(Key.DirectionUp)
        compose.onNodeWithTag(TAG_SETTINGS).assertExists()
        compose.awaitFocus("$TAG_SETTING-Quality")
        compose.press(Key.DirectionCenter)
        compose.onNodeWithTag(TAG_OPTIONS).assertExists()
        // "Auto" is the current choice and takes focus.
        compose.awaitFocus("$TAG_OPTION-0")
        compose.press(Key.DirectionRight)
        compose.awaitFocus("$TAG_OPTION-1")
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("quality:1080p"), calls)
        compose.onNodeWithTag(TAG_OPTIONS).assertDoesNotExist()
        compose.awaitFocus("$TAG_SETTING-Quality")
        // Speed lives next to quality.
        compose.press(Key.DirectionRight)
        compose.awaitFocus("$TAG_SETTING-Speed")
        // Down walks back onto the seek bar and closes the row.
        compose.press(Key.DirectionDown)
        compose.awaitFocus(TAG_SEEK_BAR)
        compose.onNodeWithTag(TAG_SETTINGS).assertDoesNotExist()
    }

    @Test fun downOpensUpNextAndOkPlaysTheCard() {
        host()
        compose.press(Key.DirectionDown)
        compose.awaitFocus("$TAG_UP_NEXT_CARD-0")
        compose.press(Key.DirectionRight)
        compose.awaitFocus("$TAG_UP_NEXT_CARD-1")
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("open:r2"), calls)
        compose.awaitFocus(TAG_PLAYER)
        compose.onNodeWithTag(TAG_SEEK_BAR).assertDoesNotExist()
    }

    @Test fun backClosesTheOptionListThenTheOverlayThenLeaves() {
        host()
        compose.press(Key.DirectionUp)
        compose.awaitFocus("$TAG_SETTING-Quality")
        compose.press(Key.DirectionCenter)
        compose.awaitFocus("$TAG_OPTION-0")
        compose.back()
        compose.onNodeWithTag(TAG_OPTIONS).assertDoesNotExist()
        compose.onNodeWithTag(TAG_SETTINGS).assertExists()
        compose.back()
        compose.onNodeWithTag(TAG_SEEK_BAR).assertDoesNotExist()
        compose.awaitFocus(TAG_PLAYER)
        assertTrue("exit" !in calls)
        compose.back()
        assertEquals(listOf("exit"), calls)
    }

    @Test fun mediaKeysControlPlayback() {
        host()
        compose.press(Key.MediaPlayPause)
        compose.press(Key.MediaPause)
        compose.press(Key.MediaPlay)
        compose.press(Key.MediaFastForward)
        compose.press(Key.MediaRewind)
        compose.press(Key.MediaNext)
        compose.press(Key.MediaPrevious)
        assertEquals(listOf("playPause", "pause", "play", "preview:10000", "commit:10000",
            "preview:-10000", "commit:-10000", "next", "previous"), calls)
    }

    @Test fun aPlaybackErrorShowsAFocusedRetryAndNoCountdown() {
        position.longValue = 594_200
        player.value = player.value.copy(error = "Connection to YouTube timed out.")
        host()
        compose.awaitFocus(TAG_PLAYER_RETRY)
        compose.onNodeWithTag(TAG_AUTOPLAY).assertDoesNotExist()
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("retry"), calls)
    }

    @Test fun autoplayCardCountsDownAndOkPlaysNextWhileBackCancels() {
        position.longValue = 594_200
        host()
        compose.onNodeWithTag(TAG_AUTOPLAY).assertExists()
        compose.onNodeWithText("Related 1").assertExists()
        compose.onNodeWithText("Up next in 6 s").assertExists()
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("next"), calls)
        compose.back()
        assertEquals(listOf("next", "cancelAutoplay"), calls)
        compose.onNodeWithTag(TAG_AUTOPLAY).assertDoesNotExist()
        // The card stays away for this video; Back now leaves.
        compose.back()
        assertEquals("exit", calls.last())
    }
}
