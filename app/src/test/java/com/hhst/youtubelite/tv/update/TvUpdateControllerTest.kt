package com.hhst.youtubelite.tv.update

import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.UpdateCenter
import com.hhst.youtubelite.core.update.UpdateChannel
import com.hhst.youtubelite.core.update.UpdateCheck
import com.hhst.youtubelite.core.update.UpdateException
import com.hhst.youtubelite.core.update.UpdateSettings
import android.content.Context
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvUpdateControllerTest {
    private val release = AppRelease("tv-v1.0.5", "1.0.5", "https://x/a.apk", "MinTube-TV-v1.0.5.apk", 1000, "https://x/a.sha256",
        "## Notes\n- one", "https://x/page")

    private class FakeSettings : UpdateSettings {
        override var autoCheck = true
        override var notify = true
        override var lastSuccessfulCheck: Long? = null
    }

    /** The shared UpdateCenter as the TV state holder sees it, plus the installer side (permission, install). */
    private class FakeGateway(var result: UpdateCheck?) : UpdateCenter, TvApkInstall {
        var checkFailure: Throwable? = null
        var downloadFailure: Throwable? = null
        var installFailure: Throwable? = null
        var historyFailure: Throwable? = null
        var releases: List<AppRelease> = emptyList()
        var allowed = true
        var settingsAvailable = true
        var settingsOpened = 0
        var reschedules = 0
        var rescheduleSawAutoCheck = mutableListOf<Boolean>()
        val installed = mutableListOf<File>()
        var downloads = 0
        var gate: CompletableDeferred<Unit>? = null
        val file: File = File.createTempFile("tvupd", ".apk").also { it.deleteOnExit() }
        override val settings = FakeSettings()

        override val channel = UpdateChannel.Tv
        override val currentVersion = "1.0.4"
        override suspend fun check(): UpdateCheck {
            checkFailure?.let { throw it }
            settings.lastSuccessfulCheck = 1234L
            return result!!
        }
        override suspend fun history(limit: Int): List<AppRelease> { historyFailure?.let { throw it }; return releases }
        override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File {
            downloads++
            onProgress(0.5f)
            gate?.await()
            downloadFailure?.let { throw it }
            onProgress(1f)
            return file
        }
        override fun reschedule() { reschedules++; rescheduleSawAutoCheck += settings.autoCheck }
        override fun shareInstalledApk(context: Context) = Unit

        override fun canInstall() = allowed
        override fun openPermissionSettings(): Boolean { settingsOpened++; return settingsAvailable }
        override suspend fun install(apk: File) { installFailure?.let { throw it }; installed += apk }
    }

    private fun TestScope.controller(gateway: FakeGateway, version: String = "1.0.4", canInstallBuild: Boolean = true) =
        TvUpdateController(this.backgroundScope, gateway, gateway, version, canInstallBuild)

    private fun available() = FakeGateway(UpdateCheck.Available(release))
    private val TvUpdateController.update get() = ui.value.update

    @Test fun newerReleaseIsOffered() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(available())
        c.check()
        val s = c.update as TvUpdateState.Available
        assertEquals("1.0.4", s.current)
        assertEquals(release, s.release)
        // The offer opens the release panel with Download and install.
        assertEquals(release, c.ui.value.detail)
        assertTrue(c.ui.value.detailInstallable)
        assertTrue(c.ui.value.panelOpen)
    }

    @Test fun sameVersionIsUpToDate() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeGateway(UpdateCheck.UpToDate(release)), version = "1.0.5")
        c.check()
        assertEquals(TvUpdateState.UpToDate("1.0.5"), c.update)
        assertFalse(c.update.isPanel)
        assertFalse(c.ui.value.panelOpen)
    }

    @Test fun developmentBuildNeverOffersInstall() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeGateway(UpdateCheck.Development(release)), version = "1.0.4-dev", canInstallBuild = false)
        c.check()
        assertEquals(TvUpdateState.Development("1.0.5"), c.update)
        c.openRelease(release)
        assertFalse(c.ui.value.detailInstallable)
        c.download()
        assertEquals(TvUpdateState.Development("1.0.5"), c.update)
    }

    @Test fun checkFailureIsInlineAndRetryChecksAgain() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { checkFailure = java.io.IOException("offline") }
        val c = controller(g)
        c.check()
        assertEquals(TvUpdateState.Failed(TvUpdateError.Check, null), c.update)
        assertFalse(c.update.isPanel)
        g.checkFailure = null
        c.retry()
        assertTrue(c.update is TvUpdateState.Available)
    }

    @Test fun noPublishedReleaseIsUpToDate() = runTest(UnconfinedTestDispatcher()) {
        // The center reports "no installable release" as UpToDate(null); the old NoPackage error is no longer produced.
        val c = controller(FakeGateway(UpdateCheck.UpToDate(null)))
        c.check()
        assertEquals(TvUpdateState.UpToDate("1.0.4"), c.update)
    }

    @Test fun successfulCheckRefreshesTheLastCheckTime() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(available())
        assertEquals(null, c.ui.value.lastCheck)
        c.check()
        assertEquals(1234L, c.ui.value.lastCheck)
    }

    @Test fun downloadReportsProgressThenInstalls() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { gate = CompletableDeferred() }
        val c = controller(g)
        c.check()
        c.download()
        assertEquals(TvUpdateState.Downloading(release, 0.5f), c.update)
        g.gate!!.complete(Unit)
        assertEquals(listOf(g.file), g.installed)
        assertEquals(TvUpdateState.Idle, c.update)
    }

    @Test fun cancelStopsTheDownloadAndInstallsNothing() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { gate = CompletableDeferred() }
        val c = controller(g)
        c.check()
        c.download()
        c.dismiss()
        g.gate!!.complete(Unit)
        // Back on the card with the offer still known.
        assertEquals(TvUpdateState.Available("1.0.4", release), c.update)
        assertFalse(c.ui.value.panelOpen)
        assertTrue(g.installed.isEmpty())
    }

    @Test fun laterClosesThePanelButKeepsTheOffer() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(available())
        c.check()
        c.dismiss()
        assertFalse(c.ui.value.panelOpen)
        assertEquals(TvUpdateState.Available("1.0.4", release), c.update)
        // "View update" reopens it.
        c.openRelease(release)
        assertEquals(release, c.ui.value.detail)
    }

    @Test fun checksumFailureShowsErrorAndRetryDownloadsAgain() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply {
            downloadFailure = UpdateException(UpdateException.Kind.ChecksumMismatch, "bad")
        }
        val c = controller(g)
        c.check()
        c.download()
        assertEquals(TvUpdateState.Failed(TvUpdateError.Checksum, release), c.update)
        g.downloadFailure = null
        c.retry()
        assertEquals(2, g.downloads)
        assertEquals(1, g.installed.size)
    }

    @Test fun networkAndMissingChecksumMapToTheirErrors() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { downloadFailure = java.io.IOException("reset") }
        val c = controller(g)
        c.check(); c.download()
        assertEquals(TvUpdateError.Network, (c.update as TvUpdateState.Failed).error)
        g.downloadFailure = UpdateException(UpdateException.Kind.MissingChecksum, "none")
        c.retry()
        assertEquals(TvUpdateError.NoChecksum, (c.update as TvUpdateState.Failed).error)
    }

    @Test fun installFailureKeepsTheFileAndRetryReinstalls() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { installFailure = IllegalStateException("session") }
        val c = controller(g)
        c.check(); c.download()
        assertEquals(TvUpdateState.Failed(TvUpdateError.Install, release), c.update)
        g.installFailure = null
        c.retry()
        assertEquals(1, g.downloads)
        assertEquals(1, g.installed.size)
        assertEquals(TvUpdateState.Idle, c.update)
    }

    @Test fun missingPermissionOpensSettingsAndResumeInstalls() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { allowed = false }
        val c = controller(g)
        c.check(); c.download()
        assertEquals(TvUpdateState.NeedsPermission(release, settingsOpened = true), c.update)
        assertEquals(1, g.settingsOpened)
        assertTrue(g.installed.isEmpty())
        c.onResume() // still not allowed: nothing happens
        assertTrue(c.update is TvUpdateState.NeedsPermission)
        g.allowed = true
        c.onResume()
        assertEquals(1, g.installed.size)
        assertEquals(TvUpdateState.Idle, c.update)
        assertEquals(1, g.downloads)
    }

    @Test fun deviceWithoutThePermissionScreenShowsManualSteps() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { allowed = false; settingsAvailable = false }
        val c = controller(g)
        c.check(); c.download()
        assertEquals(TvUpdateState.NeedsPermission(release, settingsOpened = false), c.update)
        g.allowed = true
        c.retry()
        assertEquals(1, g.installed.size)
    }

    @Test fun dismissingThePermissionPanelStopsResumeFromInstalling() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { allowed = false }
        val c = controller(g)
        c.check(); c.download()
        c.dismiss()
        g.allowed = true
        c.onResume()
        assertTrue(g.installed.isEmpty())
        assertEquals(TvUpdateState.Available("1.0.4", release), c.update)
    }

    private fun entry(version: String, published: String) =
        AppRelease("tv-v$version", version, "u", "a.apk", 1, "s", "- notes $version", "p", published)

    @Test fun toggleWritesTheSettingThenReschedules() = runTest(UnconfinedTestDispatcher()) {
        val g = available()
        val c = controller(g)
        c.setAutoCheck(false)
        assertFalse(g.settings.autoCheck)
        assertFalse(c.ui.value.autoCheck)
        assertEquals(1, g.reschedules)
        assertEquals(listOf(false), g.rescheduleSawAutoCheck)
        c.setNotify(false)
        assertFalse(g.settings.notify)
        assertFalse(c.ui.value.notify)
        assertEquals(2, g.reschedules)
        c.setAutoCheck(true)
        assertTrue(g.settings.autoCheck)
        assertEquals(3, g.reschedules)
    }

    @Test fun pageOpenReadsTogglesLastCheckAndHistory() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply {
            settings.autoCheck = false
            settings.notify = false
            settings.lastSuccessfulCheck = 99L
            releases = listOf(entry("1.0.5", "2026-11-20"), entry("1.0.4", "2026-11-01"), entry("1.0.3", ""))
        }
        val c = controller(g)
        c.onPageOpened()
        val ui = c.ui.value
        assertFalse(ui.autoCheck)
        assertFalse(ui.notify)
        assertEquals(99L, ui.lastCheck)
        val entries = (ui.history as TvHistory.Loaded).entries
        assertEquals(listOf("1.0.5", "1.0.4", "1.0.3"), entries.map { it.release.version })
        assertEquals(listOf("20/11/2026", "01/11/2026", null), entries.map { it.date })
        assertEquals(listOf(true, false, false), entries.map { it.newer })
        assertEquals(listOf(false, true, false), entries.map { it.installed })
    }

    @Test fun historyFailureIsShownAndReloadRecovers() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { historyFailure = java.io.IOException("offline") }
        val c = controller(g)
        c.onPageOpened()
        assertEquals(TvHistory.Failed, c.ui.value.history)
        g.historyFailure = null
        g.releases = listOf(entry("1.0.5", "2026-11-20"))
        c.loadHistory(force = true)
        assertTrue(c.ui.value.history is TvHistory.Loaded)
    }

    @Test fun absentUpdateCenterNeverCrashesThePage() = runTest(UnconfinedTestDispatcher()) {
        val g = available().apply { historyFailure = IllegalStateException("no binding") }
        val c = controller(g)
        c.onPageOpened()
        c.setAutoCheck(false)
        assertEquals(TvHistory.Failed, c.ui.value.history)
        assertFalse(c.ui.value.autoCheck)
    }

    @Test fun changelogRowOfANewerReleaseOffersInstallAndDownloads() = runTest(UnconfinedTestDispatcher()) {
        val newer = entry("1.0.5", "2026-11-20")
        val g = FakeGateway(UpdateCheck.UpToDate(null))
        val c = controller(g)
        c.openRelease(newer)
        assertEquals(newer, c.ui.value.detail)
        assertTrue(c.ui.value.detailInstallable)
        c.download()
        assertEquals(1, g.downloads)
        assertEquals(1, g.installed.size)
        assertEquals(null, c.ui.value.detail)
    }

    @Test fun changelogRowOfAnOlderOrCurrentReleaseOnlyShowsNotes() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(UpdateCheck.UpToDate(null))
        val c = controller(g)
        c.openRelease(entry("1.0.4", "2026-11-01"))
        assertFalse(c.ui.value.detailInstallable)
        c.download()
        assertEquals(0, g.downloads)
        c.dismiss()
        assertFalse(c.ui.value.panelOpen)
    }

    @Test fun releaseDateFormatsGitHubTimestampsAndIgnoresBlank() {
        assertEquals("20/11/2026", TvUpdateController.releaseDate("2026-11-20"))
        assertEquals(null, TvUpdateController.releaseDate(""))
        assertEquals(null, TvUpdateController.releaseDate("soon"))
    }
}
