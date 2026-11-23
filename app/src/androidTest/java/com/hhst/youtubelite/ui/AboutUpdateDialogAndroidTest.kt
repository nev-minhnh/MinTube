package com.hhst.youtubelite.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.ui.about.AboutScreen
import com.hhst.youtubelite.ui.about.AboutUpdateDialog
import com.hhst.youtubelite.ui.about.UpdateUiState
import com.hhst.youtubelite.ui.design.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AboutUpdateDialogAndroidTest {
    @get:Rule val compose = createComposeRule()
    private val release = AppRelease("v1.0.8", "1.0.8", "u", "a.apk", 12_000_000, "s", "## Changes\n- Faster start", "p")

    @Test fun availableShowsVersionNotesAndActions() {
        var installs = 0; var later = 0
        compose.setContent { AppTheme { AboutUpdateDialog(UpdateUiState.Available("1.0.7", release), { installs++ }, { later++ }, {}) } }
        compose.onNodeWithTag("update-version").assertIsDisplayed()
        compose.onNodeWithTag("update-notes").assertIsDisplayed()
        compose.onNodeWithTag("update-install").performClick()
        compose.onNodeWithTag("update-later").performClick()
        assertEquals(1, installs); assertEquals(1, later)
    }

    @Test fun downloadingShowsProgressAndCancel() {
        var cancels = 0
        compose.setContent { AppTheme { AboutUpdateDialog(UpdateUiState.Downloading(release, 0.42f), {}, {}, { cancels++ }) } }
        compose.onNodeWithTag("update-progress").assertIsDisplayed()
        compose.onNodeWithTag("update-percent").assertIsDisplayed()
        compose.onNodeWithTag("update-cancel").performClick()
        assertEquals(1, cancels)
    }

    @Test fun failureShowsStatusLineWithRetry() {
        var retries = 0
        compose.setContent {
            AppTheme {
                AboutScreen("MinTube", "1.0.7", {}, false, "", {}, {}, {}, {}, {},
                    update = UpdateUiState.Failed(release, "boom"), onUpdateRetry = { retries++ })
            }
        }
        compose.onNodeWithTag("update-retry").assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }
}
