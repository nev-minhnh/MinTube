package com.hhst.youtubelite.player.surface

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.extractor.Chapter
import com.hhst.youtubelite.player.PlayerUiState
import com.hhst.youtubelite.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * QA D7: every on-video control is reachable by its content description as a labelled button, and the
 * seek bar is an adjustable slider that reports its value. Gestures are not exercised here.
 */
class PlayerControlsSemanticsAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun state() = PlayerUiState(visible = true, title = "Big Buck Bunny", author = "Blender",
        prepared = true, firstFrameRendered = true, isPlaying = true, userWantsPlay = true,
        durationMs = 600_000L, hasNext = true, hasPrevious = true, controlsVisible = true,
        chapters = listOf(Chapter("Intro", 0), Chapter("Middle", 300)))

    @Test fun everyOnVideoControlIsALabelledButtonAndTheSeekBarIsASlider() {
        val clicks = mutableListOf<String>()
        compose.setContent { AppTheme(darkTheme = true, dynamicColor = false) {
            val position = remember { mutableLongStateOf(75_000L) }
            Box(Modifier.size(640.dp, 360.dp)) {
                Column {
                    TopBar(state = state(), positionState = remember { mutableStateOf(75_000L) },
                        onBack = { clicks += "back" }, onMore = { clicks += "settings" },
                        onSubtitle = { clicks += "captions" }, onCast = { clicks += "cast" })
                    CenterControls(state(), onPlayPause = { clicks += "playPause" },
                        onPrevious = { clicks += "previous" }, onNext = { clicks += "next" })
                    BottomBar(state = state(), positionState = position,
                        bufferedPositionState = remember { mutableLongStateOf(120_000L) },
                        onSeek = { clicks += "seek:$it" }, onFullscreen = { clicks += "fullscreen" },
                        chapters = state().chapters, onOpenChapters = { clicks += "chapters" })
                }
            }
        } }
        val expected = listOf(
            R.string.action_pause to "playPause",
            R.string.action_previous to "previous",
            R.string.action_next to "next",
            R.string.subtitles to "captions",
            R.string.more_options to "settings",
            R.string.action_fullscreen to "fullscreen",
            R.string.cast to "cast",
        )
        for ((label, click) in expected) {
            compose.onNodeWithContentDescription(compose.activity.getString(label))
                .assertIsDisplayed().assert(isButton).assert(hasClickAction())
                .performClick()
            compose.runOnIdle { assertEquals(click, clicks.last()) }
        }
        // Chapters: the chip names the current chapter after the "Chapters" label.
        val chapters = compose.activity.getString(R.string.chapters)
        compose.onNodeWithContentDescription("$chapters: Intro").assertIsDisplayed().assert(isButton)
            .performClick()
        compose.runOnIdle { assertEquals("chapters", clicks.last()) }

        val bar = compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_time_bar))
        bar.assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .assert(SemanticsMatcher("value 75s of 600s") {
                val range = it.config[SemanticsProperties.ProgressBarRangeInfo]
                kotlin.math.abs(range.current - 0.125f) < 0.001f && range.range == 0f..1f
            })
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                PlayerUi.formatTime(75_000L) + " / " + PlayerUi.formatTime(600_000L)))
    }
}
