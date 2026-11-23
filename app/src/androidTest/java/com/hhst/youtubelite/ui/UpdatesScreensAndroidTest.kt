package com.hhst.youtubelite.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.ui.design.AppTheme
import com.hhst.youtubelite.ui.updates.AppUpdateScreen
import com.hhst.youtubelite.ui.updates.HistoryState
import com.hhst.youtubelite.ui.updates.InstallState
import com.hhst.youtubelite.ui.updates.ReleaseNotesScreen
import com.hhst.youtubelite.ui.updates.UpdateStatus
import com.hhst.youtubelite.ui.updates.UpdatesScreen
import com.hhst.youtubelite.ui.updates.UpdatesUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdatesScreensAndroidTest {
    @get:Rule val compose = createComposeRule()

    private val newer = AppRelease("v1.0.10", "1.0.10", "u", "a.apk", 12_000_000, "s", "## Changes\n- Faster start", "p",
        "2026-11-22T10:00:00Z")
    private val older = AppRelease("v1.0.8", "1.0.8", "u", "a.apk", 12_000_000, "s", "- Old fix", "p", "2026-11-10T10:00:00Z")

    private fun state(
        status: UpdateStatus = UpdateStatus.UpToDate("1.0.9"),
        history: HistoryState = HistoryState.Loaded(listOf(newer, older)),
        install: InstallState = InstallState.Idle,
        auto: Boolean = true,
        notify: Boolean = false,
        lastCheck: Long? = 1_700_000_000_000L,
    ) = UpdatesUiState("1.0.9", status, lastCheck, auto, notify, history, install)

    @Test fun updatesPageShowsRowsTogglesAndChangelogs() {
        var app = 0; var auto: Boolean? = null; var notify: Boolean? = null; var settings = 0; var share = 0
        var opened: AppRelease? = null
        compose.setContent {
            AppTheme {
                UpdatesScreen(state(), {}, { app++ }, { opened = it }, { auto = it }, { notify = it }, { settings++ },
                    { share++ }, {})
            }
        }
        compose.onNodeWithTag("updates-app-card").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-toggle-updates-auto").assertIsDisplayed()
        compose.onNodeWithTag("settings-toggle-state-updates-auto").assertIsOn()
        compose.onNodeWithTag("settings-toggle-state-updates-notify").assertIsOff()
        compose.onNodeWithTag("settings-toggle-updates-auto").performClick()
        compose.onNodeWithTag("settings-toggle-updates-notify").performClick()
        compose.onNodeWithTag("updates-notification-settings").performClick()
        compose.onNodeWithTag("updates-share-apk").performScrollTo().performClick()
        compose.onNodeWithTag("updates-release-1.0.8").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, app); assertEquals(false, auto); assertEquals(true, notify)
        assertEquals(1, settings); assertEquals(1, share); assertEquals("1.0.8", opened?.version)
    }

    @Test fun updatesPageShowsChangelogLoadingAndRetry() {
        var retries = 0
        compose.setContent {
            AppTheme { UpdatesScreen(state(history = HistoryState.Failed), {}, {}, {}, {}, {}, {}, {}, { retries++ }) }
        }
        compose.onNodeWithTag("updates-changelogs-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("updates-changelogs-retry").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun appUpdateUpToDateShowsVersionAndLastCheck() {
        var checks = 0
        compose.setContent { AppTheme { AppUpdateScreen(state(), {}, { checks++ }, {}, {}, {}) } }
        compose.onNodeWithTag("app-update-status").assertIsDisplayed()
        compose.onNode(hasText("MinTube version 1.0.9")).assertIsDisplayed()
        compose.onNodeWithTag("app-update-last-check").assertIsDisplayed()
        compose.onNodeWithTag("app-update-check").assertIsDisplayed().performClick()
        assertEquals(1, checks)
    }

    @Test fun appUpdateAvailableShowsNotesAndInstall() {
        var installs = 0
        compose.setContent {
            AppTheme { AppUpdateScreen(state(status = UpdateStatus.Available(newer)), {}, {}, { installs++ }, {}, {}) }
        }
        compose.onNodeWithTag("update-version").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-notes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-install").performScrollTo().performClick()
        assertEquals(1, installs)
    }

    @Test fun appUpdateDownloadingShowsProgressAndCancel() {
        var cancels = 0
        compose.setContent {
            AppTheme {
                AppUpdateScreen(state(status = UpdateStatus.Available(newer), install = InstallState.Downloading(newer, 0.42f)),
                    {}, {}, {}, { cancels++ }, {})
            }
        }
        compose.onNodeWithTag("update-progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-percent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-cancel").performScrollTo().performClick()
        assertEquals(1, cancels)
    }

    @Test fun appUpdateFailureShowsMessageWithRetry() {
        var retries = 0
        compose.setContent {
            AppTheme {
                AppUpdateScreen(state(status = UpdateStatus.Available(newer), install = InstallState.Failed(newer, "boom")),
                    {}, {}, {}, {}, { retries++ })
            }
        }
        compose.onNodeWithTag("update-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-retry").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test fun appUpdateDevelopmentAndFailedStatesShowHeadline() {
        compose.setContent {
            AppTheme { AppUpdateScreen(state(status = UpdateStatus.Development("1.0.9")), {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("app-update-development").assertIsDisplayed()
        compose.onNodeWithTag("update-install").assertDoesNotExist()
    }

    @Test fun newerChangelogEntryOffersInstall() {
        var installs = 0
        compose.setContent {
            AppTheme { ReleaseNotesScreen(newer, "1.0.9", newer = true, installable = true, install = InstallState.Idle,
                onBack = {}, onInstall = { installs++ }, onCancel = {}, onRetry = {}) }
        }
        compose.onNodeWithTag("update-notes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-install").performScrollTo().performClick()
        assertEquals(1, installs)
    }

    @Test fun olderChangelogEntryShowsNotesWithoutInstall() {
        compose.setContent {
            AppTheme { ReleaseNotesScreen(older, "1.0.9", newer = false, installable = true, install = InstallState.Idle,
                onBack = {}, onInstall = {}, onCancel = {}, onRetry = {}) }
        }
        compose.onNodeWithTag("update-notes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("release-installed-note").assertIsDisplayed()
        compose.onNodeWithTag("update-install").assertDoesNotExist()
    }
}
