package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.shell.TvComingSoon
import com.hhst.youtubelite.tv.shell.TvDestination
import com.hhst.youtubelite.tv.shell.TvShell
import com.hhst.youtubelite.tv.shell.TvShellState
import com.hhst.youtubelite.tv.shell.railItemTag
import com.hhst.youtubelite.tv.shell.rememberTvShellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Rail ↔ content navigation with the D-pad, and the shell's Back ladder. */
class TvShellAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var exits = 0
    private lateinit var shell: TvShellState

    private fun host() {
        compose.setContent {
            TvCanvas {
                shell = rememberTvShellState()
                TvShell(shell, onExit = { exits++ }) { destination ->
                    if (destination == TvDestination.Home) {
                        Row(Modifier.padding(40.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            repeat(3) { index -> TvButton("Card $index", {}, Modifier.testTag("card-$index")) }
                        }
                    } else {
                        TvComingSoon(destination, onHome = { shell.select(TvDestination.Home) })
                    }
                }
            }
        }
    }

    @Test fun initialFocusIsContentAndLeftReachesTheExpandedRail() {
        host()
        compose.awaitFocus("card-0")
        compose.press(Key.DirectionRight)
        compose.awaitFocus("card-1")
        compose.press(Key.DirectionLeft)
        compose.press(Key.DirectionLeft)
        compose.awaitFocus(railItemTag(TvDestination.Home))
        // Focus expands the rail: labels appear.
        compose.onNodeWithText("Subscriptions").assertExists()
        // Right returns to the card focus left from.
        compose.press(Key.DirectionRight)
        compose.awaitFocus("card-0")
    }

    @Test fun backWalksContentToRailThenExits() {
        host()
        compose.awaitFocus("card-0")
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionRight)
        compose.awaitFocus("card-2")
        compose.back()
        compose.awaitFocus(railItemTag(TvDestination.Home))
        assertEquals(0, exits)
        // Back into the content lands on the remembered card.
        compose.press(Key.DirectionRight)
        compose.awaitFocus("card-2")
        compose.back()
        compose.back()
        assertEquals(1, exits)
    }

    @Test fun railSelectsADestinationAndBackReturnsHome() {
        host()
        compose.awaitFocus("card-0")
        compose.back()
        compose.awaitFocus(railItemTag(TvDestination.Home))
        compose.press(Key.DirectionDown)
        compose.awaitFocus(railItemTag(TvDestination.Search))
        compose.press(Key.DirectionCenter)
        // Selecting moves focus into the new destination's content.
        compose.awaitFocus("tv-coming-soon-home")
        assertEquals(TvDestination.Search, shell.selected)
        compose.onNodeWithTag(railItemTag(TvDestination.Search)).assertIsSelected()
        compose.back()
        compose.awaitFocus(railItemTag(TvDestination.Search))
        compose.back()
        compose.awaitFocus("card-0")
        assertEquals(TvDestination.Home, shell.selected)
        assertEquals(0, exits)
    }
}
