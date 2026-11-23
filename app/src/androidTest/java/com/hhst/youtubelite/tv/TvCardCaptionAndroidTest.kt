package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvCard
import com.hhst.youtubelite.tv.design.TvVideo
import androidx.compose.foundation.layout.Row
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The card caption reads "Channel · views · age" and the cards stay D-pad reachable. */
class TvCardCaptionAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val opened = mutableListOf<String>()

    @Test fun captionShowsChannelViewsAndAgeAndDpadStillMovesAndOpens() {
        compose.setContent {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { first.requestFocus() }
            TvCanvas {
                Row {
                    TvCard(TvVideo("a", "First", "Chan", "1.2M views", published = "5 days ago"), { opened += "a" },
                        Modifier.focusRequester(first).testTag("card-a"))
                    TvCard(TvVideo("b", "Second", "Chan", published = "2 hours ago"), { opened += "b" }, Modifier.testTag("card-b"))
                }
            }
        }
        compose.onNodeWithText("Chan · 1.2M views · 5 days ago").assertIsDisplayed()
        compose.onNodeWithText("Chan · 2 hours ago").assertIsDisplayed()
        compose.awaitFocus("card-a")
        compose.press(Key.DirectionRight)
        compose.awaitFocus("card-b")
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("b"), opened)
    }
}
