package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.update.TAG_UPDATE_CANCEL
import com.hhst.youtubelite.tv.update.TAG_UPDATE_DOWNLOAD
import com.hhst.youtubelite.tv.update.TAG_UPDATE_LATER
import com.hhst.youtubelite.tv.update.TAG_UPDATE_PERCENT
import com.hhst.youtubelite.tv.update.TvUpdateActions
import com.hhst.youtubelite.tv.update.TvUpdatePanel
import com.hhst.youtubelite.tv.update.TvUpdateState
import com.hhst.youtubelite.tv.update.TvUpdatesUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** D-pad through the TV update panels (fake state, no network). */
@OptIn(ExperimentalTestApi::class)
class TvUpdateAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private val release = AppRelease("tv-v1.0.5", "1.0.5", "u", "MinTube-TV-v1.0.5.apk", 12_000_000, "s",
        (1..40).joinToString("\n") { "- note $it" }, "p")
    private val state = mutableStateOf(TvUpdatesUi(update = TvUpdateState.Available("1.0.4", release),
        detail = release, detailInstallable = true))

    private val actions = object : TvUpdateActions {
        override fun check() { calls += "check" }
        override fun download() { calls += "download"; state.value = TvUpdatesUi(update = TvUpdateState.Downloading(release, 0.4f)) }
        override fun retry() { calls += "retry" }
        override fun dismiss() { calls += "dismiss"; state.value = TvUpdatesUi() }
        override fun openRelease(release: AppRelease) = Unit
        override fun setAutoCheck(enabled: Boolean) = Unit
        override fun setNotify(enabled: Boolean) = Unit
        override fun loadHistory(force: Boolean) = Unit
    }

    private fun host() {
        compose.setContent { TvCanvas { TvUpdatePanel(state.value, "1.0.4", actions) } }
    }

    @Test fun availablePanelFocusesDownloadRightReachesLaterAndBackCloses() {
        host()
        compose.awaitFocus(TAG_UPDATE_DOWNLOAD)
        compose.press(Key.DirectionRight)
        compose.awaitFocus(TAG_UPDATE_LATER)
        compose.press(Key.DirectionLeft)
        compose.awaitFocus(TAG_UPDATE_DOWNLOAD)
        // The panel is a dialog window: Back reaches it as the remote's key, like the Info panel test.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test fun okOnDownloadShowsProgressWithFocusedCancel() {
        host()
        compose.awaitFocus(TAG_UPDATE_DOWNLOAD)
        compose.press(Key.DirectionCenter)
        compose.awaitFocus(TAG_UPDATE_CANCEL)
        compose.onNodeWithTag(TAG_UPDATE_PERCENT).assertIsDisplayed()
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("download", "dismiss"), calls)
    }
}
