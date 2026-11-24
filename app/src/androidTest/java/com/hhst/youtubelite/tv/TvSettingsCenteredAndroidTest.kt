package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvUiScale
import com.hhst.youtubelite.tv.settings.TAG_SETTINGS_LIST
import com.hhst.youtubelite.tv.settings.TAG_SETTINGS_SCREEN
import com.hhst.youtubelite.tv.settings.TvAccountState
import com.hhst.youtubelite.tv.settings.TvSettingsCallbacks
import com.hhst.youtubelite.tv.settings.TvSettingsCatalog
import com.hhst.youtubelite.tv.settings.TvSettingsChoices
import com.hhst.youtubelite.tv.settings.TvSettingsScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Settings column is horizontally centred in its area at every Interface size. */
class TvSettingsCenteredAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val callbacks = object : TvSettingsCallbacks {
        override fun toggle(prefKey: String, enabled: Boolean) {}
        override fun signIn() {}
        override fun selectAccount(id: String) {}
        override fun useDevice() {}
        override fun signOut() {}
        override fun language(index: Int) {}
        override fun captionSize(index: Int) {}
        override fun captionBackground(index: Int) {}
    }

    @After fun restoreScale() {
        TvUiScale.set(InstrumentationRegistry.getInstrumentation().targetContext, 1f)
    }

    private fun assertCentered(scale: Float) {
        TvUiScale.set(InstrumentationRegistry.getInstrumentation().targetContext, scale)
        compose.setContent {
            TvCanvas {
                val account = TvAccountState()
                val rows = TvSettingsCatalog.rows(emptyMap(), account, "English", "Version 1.0.0")
                TvSettingsScreen(rows, account, TvSettingsChoices(), callbacks)
            }
        }
        compose.waitForIdle()
        val area = compose.onNodeWithTag(TAG_SETTINGS_SCREEN).getUnclippedBoundsInRoot()
        val list = compose.onNodeWithTag(TAG_SETTINGS_LIST).getUnclippedBoundsInRoot()
        val left = (list.left - area.left).value
        val right = (area.right - list.right).value
        assertTrue("scale $scale: list has real width", (list.right - list.left) > 100.dp)
        assertEquals("scale $scale: left $left vs right $right", left, right, 3f)
    }

    @Test fun centredAtHalfSize() = assertCentered(0.5f)

    @Test fun centredAtFullSize() = assertCentered(1f)
}
