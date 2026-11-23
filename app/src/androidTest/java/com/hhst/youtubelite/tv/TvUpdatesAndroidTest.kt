package com.hhst.youtubelite.tv

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvPanelOption
import com.hhst.youtubelite.tv.settings.TAG_ROW
import com.hhst.youtubelite.tv.settings.TAG_SETTINGS_LIST
import com.hhst.youtubelite.tv.settings.TvAccountState
import com.hhst.youtubelite.tv.settings.TvSettingsCallbacks
import com.hhst.youtubelite.tv.settings.TvSettingsCatalog
import com.hhst.youtubelite.tv.settings.TvSettingsChoices
import com.hhst.youtubelite.tv.settings.TvSettingsScreen
import com.hhst.youtubelite.tv.update.TAG_UPDATES_AUTO
import com.hhst.youtubelite.tv.update.TAG_UPDATES_CHECK
import com.hhst.youtubelite.tv.update.TAG_UPDATES_NOTIFY
import com.hhst.youtubelite.tv.update.TAG_UPDATES_STATUS
import com.hhst.youtubelite.tv.update.TAG_UPDATES_VIEW
import com.hhst.youtubelite.tv.update.TAG_UPDATE_DOWNLOAD
import com.hhst.youtubelite.tv.update.TAG_UPDATE_LATER
import com.hhst.youtubelite.tv.update.TAG_UPDATE_NOTES
import com.hhst.youtubelite.tv.update.TAG_UPDATE_PANEL
import com.hhst.youtubelite.tv.update.TvChangelogEntry
import com.hhst.youtubelite.tv.update.TvHistory
import com.hhst.youtubelite.tv.update.TvUpdateActions
import com.hhst.youtubelite.tv.update.TvUpdateState
import com.hhst.youtubelite.tv.update.TvUpdatesScreen
import com.hhst.youtubelite.tv.update.TvUpdatesUi
import com.hhst.youtubelite.tv.update.logTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** D-pad through the TV Updates page and the Settings row that opens it (fake state, no network). */
@OptIn(ExperimentalTestApi::class)
class TvUpdatesAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()

    private fun release(version: String, notes: String = (1..40).joinToString("\n") { "- note $it" }) =
        AppRelease("tv-v$version", version, "u", "MinTube-TV-v$version.apk", 12_000_000, "s", notes, "p", "2026-11-20")

    private val newer = release("1.0.6")
    private val installed = release("1.0.5")
    private val entries = listOf(
        TvChangelogEntry(newer, "20/11/2026", newer = true, installed = false),
        TvChangelogEntry(installed, "20/11/2026", newer = false, installed = true),
    )
    private val ui = mutableStateOf(TvUpdatesUi(history = TvHistory.Loaded(entries)))

    private val actions = object : TvUpdateActions {
        override fun check() {
            calls += "check"
            ui.value = ui.value.copy(update = TvUpdateState.Available("1.0.5", newer), detail = newer, detailInstallable = true)
        }
        override fun download() { calls += "download" }
        override fun retry() { calls += "retry" }
        override fun dismiss() { calls += "dismiss"; ui.value = ui.value.copy(detail = null) }
        override fun openRelease(release: AppRelease) {
            calls += "open:${release.version}"
            ui.value = ui.value.copy(detail = release, detailInstallable = release.version == newer.version)
        }
        override fun setAutoCheck(enabled: Boolean) { calls += "auto:$enabled"; ui.value = ui.value.copy(autoCheck = enabled) }
        override fun setNotify(enabled: Boolean) { calls += "notify:$enabled"; ui.value = ui.value.copy(notify = enabled) }
        override fun loadHistory(force: Boolean) { calls += "history:$force" }
    }

    private fun hostPage() {
        compose.setContent {
            TvCanvas { TvUpdatesScreen(ui.value, "1.0.5", actions, onBack = { calls += "back" }) }
        }
    }

    private fun systemBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    @Test fun initialFocusIsCheckForUpdatesAndOkChecks() {
        hostPage()
        compose.awaitFocus(TAG_UPDATES_CHECK)
        compose.onNodeWithTag(TAG_UPDATES_STATUS).assertIsDisplayed()
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("check"), calls)
        // An offer opens the release panel with Download and install focused.
        compose.awaitFocus(TAG_UPDATE_DOWNLOAD)
        compose.onNodeWithTag(TAG_UPDATE_NOTES).assertExists()
        systemBack()
        assertEquals(listOf("check", "dismiss"), calls)
        compose.onNodeWithTag(TAG_UPDATE_PANEL).assertDoesNotExist()
        compose.awaitFocus(TAG_UPDATES_CHECK)
    }

    @Test fun offerKeepsAViewUpdateButtonRightOfCheck() {
        ui.value = ui.value.copy(update = TvUpdateState.Available("1.0.5", newer))
        hostPage()
        compose.awaitFocus(TAG_UPDATES_CHECK)
        compose.press(Key.DirectionRight)
        compose.awaitFocus(TAG_UPDATES_VIEW)
        compose.press(Key.DirectionCenter)
        compose.awaitFocus(TAG_UPDATE_DOWNLOAD)
        compose.press(Key.DirectionRight)
        compose.awaitFocus(TAG_UPDATE_LATER)
        compose.press(Key.DirectionCenter)
        compose.awaitFocus(TAG_UPDATES_VIEW)
    }

    @Test fun togglesFlipWithOkAndKeepFocus() {
        hostPage()
        compose.awaitFocus(TAG_UPDATES_CHECK)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(TAG_UPDATES_AUTO)
        compose.press(Key.DirectionCenter)
        compose.awaitFocus(TAG_UPDATES_AUTO)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(TAG_UPDATES_NOTIFY)
        compose.press(Key.DirectionCenter)
        compose.awaitFocus(TAG_UPDATES_NOTIFY)
        assertEquals(listOf("auto:false", "notify:false"), calls.filter { it.startsWith("auto") || it.startsWith("notify") })
    }

    @Test fun changelogRowOpensItsNotesAndBackReturnsToTheRow() {
        hostPage()
        compose.awaitFocus(TAG_UPDATES_CHECK)
        val older = logTag(entries[1])
        compose.onNodeWithTag(com.hhst.youtubelite.tv.update.TAG_UPDATES_LIST).performScrollToKey("log:${installed.tag}")
        compose.onNodeWithTag(older).requestFocus()
        compose.awaitFocus(older)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("open:1.0.5"), calls)
        // Already installed: nothing to download, the panel offers only Close (tagged like Later).
        compose.awaitFocus(TAG_UPDATE_LATER)
        compose.onNodeWithTag(TAG_UPDATE_DOWNLOAD).assertDoesNotExist()
        systemBack()
        compose.onNodeWithTag(TAG_UPDATE_PANEL).assertDoesNotExist()
        compose.awaitFocus(older)
    }

    @Test fun newerChangelogRowOffersDownload() {
        hostPage()
        compose.awaitFocus(TAG_UPDATES_CHECK)
        val row = logTag(entries[0])
        compose.onNodeWithTag(com.hhst.youtubelite.tv.update.TAG_UPDATES_LIST).performScrollToKey("log:${newer.tag}")
        compose.onNodeWithTag(row).requestFocus()
        compose.awaitFocus(row)
        compose.press(Key.DirectionCenter)
        compose.awaitFocus(TAG_UPDATE_DOWNLOAD)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("open:1.0.6", "download"), calls)
    }

    @Test fun backWithNoPanelLeavesThePage() {
        hostPage()
        compose.awaitFocus(TAG_UPDATES_CHECK)
        compose.back()
        assertEquals(listOf("back"), calls)
    }

    @Test fun settingsUpdatesRowOpensThePage() {
        val settingsCalls = mutableListOf<String>()
        val callbacks = object : TvSettingsCallbacks {
            override fun toggle(prefKey: String, enabled: Boolean) = Unit
            override fun signIn() = Unit
            override fun selectAccount(id: String) = Unit
            override fun useDevice() = Unit
            override fun signOut() = Unit
            override fun language(index: Int) = Unit
            override fun captionSize(index: Int) = Unit
            override fun captionBackground(index: Int) = Unit
            override fun openUpdates() { settingsCalls += "openUpdates" }
        }
        compose.setContent {
            TvCanvas {
                val rows = TvSettingsCatalog.rows(emptyMap(), TvAccountState(), "English", "Version 1.0.5",
                    updatesSummary = "MinTube TV is up to date")
                TvSettingsScreen(rows, TvAccountState(), TvSettingsChoices(languages = listOf(TvPanelOption("English", true))),
                    callbacks, initialFocusKey = TvSettingsCatalog.KEY_UPDATES)
            }
        }
        val tag = "$TAG_ROW-${TvSettingsCatalog.KEY_UPDATES}"
        compose.onNodeWithTag(TAG_SETTINGS_LIST).performScrollToKey(TvSettingsCatalog.KEY_UPDATES)
        compose.awaitFocus(tag)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("openUpdates"), settingsCalls)
    }
}
