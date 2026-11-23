package com.hhst.youtubelite.tv.update

import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.UpdateException
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

    private class FakeGateway(var latest: AppRelease?) : TvUpdateGateway {
        var checkFailure: Throwable? = null
        var downloadFailure: Throwable? = null
        var installFailure: Throwable? = null
        var allowed = true
        var settingsAvailable = true
        var settingsOpened = 0
        val installed = mutableListOf<File>()
        var downloads = 0
        var gate: CompletableDeferred<Unit>? = null
        val file: File = File.createTempFile("tvupd", ".apk").also { it.deleteOnExit() }

        override suspend fun latest(): AppRelease? { checkFailure?.let { throw it }; return latest }
        override fun isNewer(current: String, release: AppRelease) =
            com.hhst.youtubelite.ui.about.StableVersion.isNewer(current, release.version)
        override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File {
            downloads++
            onProgress(0.5f)
            gate?.await()
            downloadFailure?.let { throw it }
            onProgress(1f)
            return file
        }
        override fun canInstall() = allowed
        override fun openPermissionSettings(): Boolean { settingsOpened++; return settingsAvailable }
        override suspend fun install(apk: File) { installFailure?.let { throw it }; installed += apk }
    }

    private fun TestScope.controller(gateway: FakeGateway, version: String = "1.0.4", canInstallBuild: Boolean = true) =
        TvUpdateController(this.backgroundScope, gateway, version, canInstallBuild)

    @Test fun newerReleaseIsOffered() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeGateway(release))
        c.check()
        val s = c.state.value as TvUpdateState.Available
        assertEquals("1.0.4", s.current)
        assertEquals(release, s.release)
        assertTrue(c.state.value.isPanel)
    }

    @Test fun sameVersionIsUpToDate() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeGateway(release), version = "1.0.5")
        c.check()
        assertEquals(TvUpdateState.UpToDate("1.0.5"), c.state.value)
        assertFalse(c.state.value.isPanel)
    }

    @Test fun developmentBuildNeverOffersInstall() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeGateway(release), version = "1.0.4-dev", canInstallBuild = false)
        c.check()
        assertEquals(TvUpdateState.Development("1.0.5"), c.state.value)
    }

    @Test fun checkFailureIsInlineAndRetryChecksAgain() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(release).apply { checkFailure = java.io.IOException("offline") }
        val c = controller(g)
        c.check()
        assertEquals(TvUpdateState.Failed(TvUpdateError.Check, null), c.state.value)
        assertFalse(c.state.value.isPanel)
        g.checkFailure = null
        c.retry()
        assertTrue(c.state.value is TvUpdateState.Available)
    }

    @Test fun emptyReleaseListIsNoPackage() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeGateway(null))
        c.check()
        assertEquals(TvUpdateState.Failed(TvUpdateError.NoPackage, null), c.state.value)
    }

    @Test fun downloadReportsProgressThenInstalls() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(release).apply { gate = CompletableDeferred() }
        val c = controller(g)
        c.check()
        c.download()
        assertEquals(TvUpdateState.Downloading(release, 0.5f), c.state.value)
        g.gate!!.complete(Unit)
        assertEquals(listOf(g.file), g.installed)
        assertEquals(TvUpdateState.Idle, c.state.value)
    }

    @Test fun cancelStopsTheDownloadAndInstallsNothing() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(release).apply { gate = CompletableDeferred() }
        val c = controller(g)
        c.check()
        c.download()
        c.dismiss()
        g.gate!!.complete(Unit)
        assertEquals(TvUpdateState.Idle, c.state.value)
        assertTrue(g.installed.isEmpty())
    }

    @Test fun laterClosesTheOffer() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeGateway(release))
        c.check()
        c.dismiss()
        assertEquals(TvUpdateState.Idle, c.state.value)
    }

    @Test fun checksumFailureShowsErrorAndRetryDownloadsAgain() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(release).apply {
            downloadFailure = UpdateException(UpdateException.Kind.ChecksumMismatch, "bad")
        }
        val c = controller(g)
        c.check()
        c.download()
        assertEquals(TvUpdateState.Failed(TvUpdateError.Checksum, release), c.state.value)
        g.downloadFailure = null
        c.retry()
        assertEquals(2, g.downloads)
        assertEquals(1, g.installed.size)
    }

    @Test fun networkAndMissingChecksumMapToTheirErrors() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(release).apply { downloadFailure = java.io.IOException("reset") }
        val c = controller(g)
        c.check(); c.download()
        assertEquals(TvUpdateError.Network, (c.state.value as TvUpdateState.Failed).error)
        g.downloadFailure = UpdateException(UpdateException.Kind.MissingChecksum, "none")
        c.retry()
        assertEquals(TvUpdateError.NoChecksum, (c.state.value as TvUpdateState.Failed).error)
    }

    @Test fun installFailureKeepsTheFileAndRetryReinstalls() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(release).apply { installFailure = IllegalStateException("session") }
        val c = controller(g)
        c.check(); c.download()
        assertEquals(TvUpdateState.Failed(TvUpdateError.Install, release), c.state.value)
        g.installFailure = null
        c.retry()
        assertEquals(1, g.downloads)
        assertEquals(1, g.installed.size)
        assertEquals(TvUpdateState.Idle, c.state.value)
    }

    @Test fun missingPermissionOpensSettingsAndResumeInstalls() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(release).apply { allowed = false }
        val c = controller(g)
        c.check(); c.download()
        assertEquals(TvUpdateState.NeedsPermission(release, settingsOpened = true), c.state.value)
        assertEquals(1, g.settingsOpened)
        assertTrue(g.installed.isEmpty())
        c.onResume() // still not allowed: nothing happens
        assertTrue(c.state.value is TvUpdateState.NeedsPermission)
        g.allowed = true
        c.onResume()
        assertEquals(1, g.installed.size)
        assertEquals(TvUpdateState.Idle, c.state.value)
        assertEquals(1, g.downloads)
    }

    @Test fun deviceWithoutThePermissionScreenShowsManualSteps() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(release).apply { allowed = false; settingsAvailable = false }
        val c = controller(g)
        c.check(); c.download()
        assertEquals(TvUpdateState.NeedsPermission(release, settingsOpened = false), c.state.value)
        g.allowed = true
        c.retry()
        assertEquals(1, g.installed.size)
    }

    @Test fun dismissingThePermissionPanelStopsResumeFromInstalling() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(release).apply { allowed = false }
        val c = controller(g)
        c.check(); c.download()
        c.dismiss()
        g.allowed = true
        c.onResume()
        assertTrue(g.installed.isEmpty())
        assertEquals(TvUpdateState.Idle, c.state.value)
    }
}
