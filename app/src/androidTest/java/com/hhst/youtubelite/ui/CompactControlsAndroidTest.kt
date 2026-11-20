package com.hhst.youtubelite.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.ui.components.*
import com.hhst.youtubelite.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompactControlsAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Signal palette: selected controls fill with the cyan accent and acquire no other tint. */
    @Test fun selectedControlsUseTheAccentInBothThemes() {
        var dark by mutableStateOf(false)
        compose.setContent {
            AppTheme(darkTheme = dark) {
                Column(Modifier.testTag("controls")) {
                    AppSwitch(true, {})
                    AppRadioButton(true, {})
                    AppCheckbox(true, {})
                    AppSlider(.5f, {}, Modifier.width(240.dp))
                }
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark = isDark }
            val image = compose.onNodeWithTag("controls").captureToImage().asAndroidBitmap()
            var colored = 0
            var offHue = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val pixel = image.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                val channels = listOf(red, green, blue)
                if (channels.max() - channels.min() > 10) {
                    colored++
                    // Cyan accent and its blends over neutrals keep blue and green above red.
                    if (red > blue || red > green) offHue++
                }
            }
            assertTrue("Selected controls must show the accent (dark=$isDark)", colored > 0)
            assertTrue("Selected controls must not acquire a non-accent tint: $offHue", offHue == 0)
        }
    }

    @Test fun singleOptionDialogKeepsItsFooterCloseAtBothFontScales() {
        var scale by mutableFloatStateOf(1f)
        var density = 1f
        compose.setContent {
            val system = LocalDensity.current
            density = system.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                AppTheme {
                    AppAlertDialog({}, modifier = Modifier.testTag("choice-dialog"),
                        title = { Text("Download quality") },
                        text = { Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("choice-row")) { Text("360p") } },
                        confirmButton = { AppTextButton({}, Modifier.testTag("choice-cancel")) { Text("Cancel") } })
                }
            }
        }
        for (font in listOf(1f, 2f)) {
            compose.runOnIdle { scale = font }
            compose.onNodeWithText("Download quality").assertIsDisplayed()
            compose.onNodeWithText("360p").assertIsDisplayed()
            val option = compose.onNodeWithTag("choice-row").fetchSemanticsNode().boundsInRoot
            val footer = compose.onNodeWithTag("choice-cancel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val dialog = compose.onNodeWithTag("choice-dialog").fetchSemanticsNode().boundsInRoot
            // Mint dialogs: 12dp above the button row and 12dp under it, plus the text button inset.
            assertTrue("Footer gap is too large: ${footer.top - option.bottom}", footer.top - option.bottom <= 16 * density)
            assertTrue("Footer has excessive bottom padding: ${dialog.bottom - footer.bottom}", dialog.bottom - footer.bottom <= 16 * density)
        }
    }
}
