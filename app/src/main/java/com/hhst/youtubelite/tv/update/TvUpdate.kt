package com.hhst.youtubelite.tv.update

import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.UpdateException
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the TV update flow needs from the outside world (AppUpdater + ApkInstaller on a device; fakes in tests). */
interface TvUpdateGateway {
    suspend fun latest(): AppRelease?
    fun isNewer(current: String, release: AppRelease): Boolean
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File
    fun canInstall(): Boolean
    /** Opens "Install unknown apps" for this app; false when the device has no such screen. */
    fun openPermissionSettings(): Boolean
    suspend fun install(apk: File)
}

enum class TvUpdateError { Check, Network, Checksum, NoChecksum, NoPackage, Install }

sealed interface TvUpdateState {
    data object Idle : TvUpdateState
    data object Checking : TvUpdateState
    data class UpToDate(val version: String) : TvUpdateState
    /** Debug or variant build: shows the latest release but never installs it. */
    data class Development(val latest: String) : TvUpdateState
    data class Available(val current: String, val release: AppRelease) : TvUpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : TvUpdateState
    /** [release] is null when the check itself failed (shown inline); otherwise the panel shows it. */
    data class Failed(val error: TvUpdateError, val release: AppRelease?) : TvUpdateState
    /** Waiting for "Install unknown apps"; [settingsOpened] is false when the device has no such screen. */
    data class NeedsPermission(val release: AppRelease, val settingsOpened: Boolean) : TvUpdateState
}

/** True when a state is shown in a panel over Settings (everything else shows inline). */
val TvUpdateState.isPanel: Boolean
    get() = this is TvUpdateState.Available || this is TvUpdateState.Downloading || this is TvUpdateState.NeedsPermission ||
        (this is TvUpdateState.Failed && release != null)

/**
 * The TV update state machine: check, offer, download, install. No Android types, so it is unit-tested
 * with a fake [TvUpdateGateway]. Mirrors the phone's AboutActivity flow.
 */
class TvUpdateController(
    private val scope: CoroutineScope,
    private val gateway: TvUpdateGateway,
    private val currentVersion: String,
    /** Only a signed release build with an `x.y.z` version can be replaced by a release APK. */
    private val canInstallBuild: Boolean,
) {
    private val mutable = MutableStateFlow<TvUpdateState>(TvUpdateState.Idle)
    val state: StateFlow<TvUpdateState> = mutable
    private var job: Job? = null
    private var apk: File? = null
    private var awaitingPermission: AppRelease? = null

    fun check() {
        if (mutable.value is TvUpdateState.Checking || mutable.value is TvUpdateState.Downloading) return
        job?.cancel()
        mutable.value = TvUpdateState.Checking
        job = scope.launch {
            mutable.value = try {
                val release = gateway.latest()
                when {
                    release == null -> TvUpdateState.Failed(TvUpdateError.NoPackage, null)
                    !canInstallBuild -> TvUpdateState.Development(release.version)
                    gateway.isNewer(currentVersion, release) -> TvUpdateState.Available(currentVersion, release)
                    else -> TvUpdateState.UpToDate(currentVersion)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                TvUpdateState.Failed(TvUpdateError.Check, null)
            }
        }
    }

    /** "Download and install" on the available panel. */
    fun download() {
        val release = (mutable.value as? TvUpdateState.Available)?.release ?: return
        startDownload(release)
    }

    private fun startDownload(release: AppRelease) {
        job?.cancel()
        awaitingPermission = null
        mutable.value = TvUpdateState.Downloading(release, 0f)
        job = scope.launch {
            var last = -1
            try {
                apk = gateway.download(release) { p ->
                    val percent = (p * 100).toInt()
                    val now = mutable.value
                    if (percent != last && now is TvUpdateState.Downloading) {
                        last = percent
                        mutable.value = TvUpdateState.Downloading(release, p)
                    }
                }
                install(release)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                mutable.value = TvUpdateState.Failed(when {
                    t is UpdateException && t.kind == UpdateException.Kind.ChecksumMismatch -> TvUpdateError.Checksum
                    t is UpdateException && t.kind == UpdateException.Kind.MissingChecksum -> TvUpdateError.NoChecksum
                    else -> TvUpdateError.Network
                }, release)
            }
        }
    }

    /** Installs the downloaded file, first sending the user to "Install unknown apps" when it is off. */
    private fun install(release: AppRelease) {
        val file = apk?.takeIf { it.exists() } ?: return startDownload(release)
        if (!gateway.canInstall()) {
            awaitingPermission = release
            mutable.value = TvUpdateState.NeedsPermission(release, runCatching { gateway.openPermissionSettings() }.getOrDefault(false))
            return
        }
        awaitingPermission = null
        mutable.value = TvUpdateState.Downloading(release, 1f)
        job = scope.launch {
            mutable.value = try {
                gateway.install(file)
                TvUpdateState.Idle
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                TvUpdateState.Failed(TvUpdateError.Install, release)
            }
        }
    }

    /** Focusable Retry: repeats whatever failed. */
    fun retry() {
        when (val s = mutable.value) {
            is TvUpdateState.Failed -> if (s.release == null) check() else install(s.release)
            is TvUpdateState.NeedsPermission -> install(s.release)
            else -> Unit
        }
    }

    /** Called from the activity's onResume: continues an install the user just allowed. */
    fun onResume() {
        val release = awaitingPermission ?: return
        if (gateway.canInstall()) install(release)
    }

    /** Later, Cancel and Back: abandon the offer or the download. */
    fun dismiss() {
        job?.cancel()
        awaitingPermission = null
        mutable.value = TvUpdateState.Idle
    }
}
