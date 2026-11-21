package com.hhst.youtubelite.tv

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.hhst.youtubelite.player.PlayerUiState
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.watch.TAG_ACTION
import com.hhst.youtubelite.tv.watch.TAG_INFO_PANEL
import com.hhst.youtubelite.tv.watch.TAG_INFO_TEXT
import com.hhst.youtubelite.tv.watch.TAG_SETTING
import com.hhst.youtubelite.tv.watch.TAG_SIGN_IN_PROMPT
import com.hhst.youtubelite.tv.watch.TAG_TOAST
import com.hhst.youtubelite.tv.watch.TvPlayerCallbacks
import com.hhst.youtubelite.tv.watch.TvPlayerScreen
import com.hhst.youtubelite.tv.watch.TvWatchActions
import com.hhst.youtubelite.tv.watch.TvWatchDetails
import com.hhst.youtubelite.tv.watch.TvWatchExtras
import com.hhst.youtubelite.tv.watch.TvWatchInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Info panel, account buttons and the SponsorBlock toast on the TV player overlay. */
class TvPlayerExtrasAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private val player = mutableStateOf(PlayerUiState(
        visible = true, prepared = true, isPlaying = false, userWantsPlay = false, videoId = "v0", title = "Test video",
        author = "Test channel", durationMs = 600_000, qualities = listOf("1080p", "720p"),
        sponsorSegments = listOf(SponsorBlockManager.Segment(30_000, 60_000, "sponsor")),
    ))
    private val position = mutableLongStateOf(10_000)
    private val extras = mutableStateOf(TvWatchExtras(details = TvWatchDetails(
        title = "Test video", channel = "Test channel", date = "2 days ago", views = "1K views",
        description = (1..80).joinToString("\n") { "Description line $it" }, canSubscribe = true)))
    private var now = 0L
    private val seekTarget = mutableStateOf<Long?>(null)

    private val callbacks = object : TvPlayerCallbacks {
        override fun playPause() { calls += "playPause" }
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekPreview(offsetMs: Long) = Unit
        override fun seekCommit(offsetMs: Long) = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun quality(label: String?) = Unit
        override fun speed(speed: Float) = Unit
        override fun subtitle(trackKey: String) = Unit
        override fun subtitlesOff() = Unit
        override fun audioTrack(trackKey: String?) = Unit
        override fun audioMenuOpened() = Unit
        override fun openVideo(videoId: String) = Unit
        override fun cancelAutoplay() = Unit
        override fun retry() = Unit
        override fun loadMoreRelated() = Unit
        override fun exit() { calls += "exit" }
    }

    private val actions = object : TvWatchActions {
        override fun like() { calls += "like" }
        override fun dislike() { calls += "dislike" }
        override fun subscribe() { calls += "subscribe" }
        override fun watchLater() { calls += "watchLater" }
        override fun signIn() { calls += "signIn" }
    }

    private fun host() {
        compose.setContent {
            TvCanvas {
                TvPlayerScreen(
                    player = player.value, position = position, buffered = position, seekTarget = seekTarget,
                    info = TvWatchInfo("Test video", "Test channel", "1K views"), upNext = emptyList(),
                    autoplayEnabled = false, callbacks = callbacks, extras = extras.value, actions = actions,
                    clockMs = { now },
                )
            }
        }
        compose.awaitFocus(com.hhst.youtubelite.tv.watch.TAG_PLAYER)
    }

    /** Up opens the controls row on Quality; Right walks to [tag]. */
    private fun walkTo(tag: String) {
        compose.press(Key.DirectionUp)
        compose.awaitFocus("$TAG_SETTING-Quality")
        repeat(12) {
            if (compose.isFocused(tag)) return
            compose.press(Key.DirectionRight)
        }
        compose.awaitFocus(tag)
    }

    @Test fun signedOutLikeOpensTheSignInPanelInstead() {
        host()
        walkTo("$TAG_ACTION-Like")
        compose.press(Key.DirectionCenter)
        compose.onNodeWithTag(TAG_SIGN_IN_PROMPT).assertExists()
        compose.awaitFocus("$TAG_SIGN_IN_PROMPT-sign-in")
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("signIn"), calls)
        compose.onNodeWithTag(TAG_SIGN_IN_PROMPT).assertDoesNotExist()
    }

    @Test fun signedInButtonsCallTheirActions() {
        extras.value = extras.value.copy(signedIn = true)
        host()
        walkTo("$TAG_ACTION-Like")
        compose.press(Key.DirectionCenter)
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionCenter)
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionCenter)
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("like", "dislike", "subscribe", "watchLater"), calls)
    }

    @Test fun infoOpensAScrollableDescriptionAndBackCloses() {
        host()
        walkTo("$TAG_ACTION-Info")
        compose.press(Key.DirectionCenter)
        compose.onNodeWithTag(TAG_INFO_PANEL).assertExists()
        compose.awaitFocus(TAG_INFO_TEXT)
        compose.onNodeWithText("2 days ago", substring = true).assertExists()
        compose.press(Key.DirectionDown)
        compose.awaitFocus(TAG_INFO_TEXT)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_INFO_PANEL).assertDoesNotExist()
        // The player stays: Back closed the panel, not the screen.
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun sponsorSkipShowsAToast() {
        host()
        // Hold the clock: the toast hides itself after a few seconds.
        compose.mainClock.autoAdvance = false
        position.longValue = 30_200
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        now = 500
        position.longValue = 60_000
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag(TAG_TOAST).assertExists()
    }
}
