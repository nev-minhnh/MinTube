package com.hhst.youtubelite.watch

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.watch.data.FixtureWatchRepository
import com.hhst.youtubelite.watch.domain.WatchContent
import com.hhst.youtubelite.watch.domain.WatchUiState
import com.hhst.youtubelite.watch.presentation.WatchScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WatchNowPlayingStripAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun ready(): WatchUiState {
        val page = FixtureWatchRepository.samplePage("bunny")
        return WatchUiState(videoId = "strip", content = WatchContent.Ready(page.copy(
            video = page.video.copy(id = "strip"),
            related = List(24) { page.related.first().copy(id = "strip-related-$it") },
        )))
    }

    private fun scrollPosition() = compose.onNodeWithTag("watch-scroll").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun stripIsHiddenAtTheTopShowsPastTheTitleAndTapScrollsBackUp() {
        val state = ready()
        compose.setContent {
            WatchScreen(state, {}, {}, modifier = Modifier.requiredSize(390.dp, 844.dp),
                playerSlot = { Box(it.background(Color.Black)) })
        }
        compose.onNodeWithTag("watch-now-playing-strip").assertDoesNotExist()
        compose.onNodeWithTag("watch-scroll").performScrollToIndex(2)
        compose.waitForIdle()
        val video = (state.content as WatchContent.Ready).page.video
        compose.onNodeWithTag("watch-now-playing-strip").assertIsDisplayed()
        // One focusable element that names the video and channel.
        compose.onNodeWithContentDescription(compose.activity.getString(
            R.string.watch_strip_now_playing_a11y, video.title, video.channel)).assertIsDisplayed().assertHasClickAction()
        // The sticky chip row stacks under the strip instead of hiding beneath it.
        val strip = compose.onNodeWithTag("watch-now-playing-strip").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("watch-scroll").performScrollToIndex(5)
        compose.waitForIdle()
        val chips = compose.onNodeWithTag("watch-related-chip-row").fetchSemanticsNode().boundsInRoot
        assertTrue("chips ${chips.top} must start below the strip ${strip.top}", chips.top >= strip.top + 40f)
        assertTrue(scrollPosition() > 0f)
        compose.onNodeWithContentDescription(compose.activity.getString(
            R.string.watch_strip_now_playing_a11y, video.title, video.channel)).performClick()
        compose.waitForIdle()
        assertEquals(0f, scrollPosition(), 0f)
        compose.onNodeWithTag("watch-now-playing-strip").assertDoesNotExist()
    }
}
