package com.hhst.youtubelite.tv.update

import com.hhst.youtubelite.core.time.PublishedAges
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.UpdateCenter
import com.hhst.youtubelite.core.update.UpdateCheck
import com.hhst.youtubelite.core.update.UpdateException
import com.hhst.youtubelite.ui.about.StableVersion
import java.io.File
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TvUpdateError { Check, Network, Checksum, NoChecksum, NoPackage, Install }

sealed interface TvUpdateState {
    data object Idle : TvUpdateState
    data object Checking : TvUpdateState
    data class UpToDate(val version: String) : TvUpdateState
    /** Debug or variant build: shows the latest release but never installs it. [latest] is blank when unknown. */
    data class Development(val latest: String) : TvUpdateState
    data class Available(val current: String, val release: AppRelease) : TvUpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : TvUpdateState
    /** [release] is null when the check itself failed (shown inline); otherwise the panel shows it. */
    data class Failed(val error: TvUpdateError, val release: AppRelease?) : TvUpdateState
    /** Waiting for "Install unknown apps"; [settingsOpened] is false when the device has no such screen. */
    data class NeedsPermission(val release: AppRelease, val settingsOpened: Boolean) : TvUpdateState
}

/** True when a state is shown in a progress/message panel over the page (everything else shows inline). */
val TvUpdateState.isPanel: Boolean
    get() = this is TvUpdateState.Downloading || this is TvUpdateState.NeedsPermission ||
        (this is TvUpdateState.Failed && release != null)

/** One row of the changelog list. */
data class TvChangelogEntry(
    val release: AppRelease,
    /** dd/MM/yyyy of the publish day, or null when GitHub sent no date. */
    val date: String?,
    /** Newer than the running version and installable on this build: the panel offers "Download and install". */
    val newer: Boolean,
    /** This is the running version. */
    val installed: Boolean,
)

sealed interface TvHistory {
    data object Loading : TvHistory
    data class Loaded(val entries: List<TvChangelogEntry>) : TvHistory
    data object Failed : TvHistory
}

/** Everything the Updates page and its panels show. */
data class TvUpdatesUi(
    val update: TvUpdateState = TvUpdateState.Idle,
    /** The release whose notes the release panel shows (the offer after a check, or a changelog row). */
    val detail: AppRelease? = null,
    /** The release panel offers "Download and install" (newer than the running version, installable build). */
    val detailInstallable: Boolean = false,
    val autoCheck: Boolean = true,
    val notify: Boolean = true,
    val lastCheck: Long? = null,
    val history: TvHistory = TvHistory.Loading,
) {
    val panelOpen: Boolean get() = update.isPanel || detail != null
}

/** Actions of the update UI; [TvUpdateController] implements them. */
interface TvUpdateActions {
    fun check()
    /** "Download and install" for the release panel's release. */
    fun download()
    fun retry()
    /** Later, Cancel, Close and Back: close the panel and abandon a download. */
    fun dismiss()
    fun openRelease(release: AppRelease)
    fun setAutoCheck(enabled: Boolean)
    fun setNotify(enabled: Boolean)
    fun loadHistory(force: Boolean = false)

    object None : TvUpdateActions {
        override fun check() = Unit
        override fun download() = Unit
        override fun retry() = Unit
        override fun dismiss() = Unit
        override fun openRelease(release: AppRelease) = Unit
        override fun setAutoCheck(enabled: Boolean) = Unit
        override fun setNotify(enabled: Boolean) = Unit
        override fun loadHistory(force: Boolean) = Unit
    }
}

/**
 * The TV Updates state holder: check, offer, download, install, the two toggles and the changelog list,
 * all through the shared [UpdateCenter]. No Android types, so it is unit-tested with a fake center and
 * installer. Mirrors the phone's flow; install goes through [TvApkInstall] (the existing ApkInstaller).
 */
class TvUpdateController(
    private val scope: CoroutineScope,
    private val center: UpdateCenter,
    private val installer: TvApkInstall,
    private val currentVersion: String,
    /** Only a signed release build with an `x.y.z` version can be replaced by a release APK. */
    private val canInstallBuild: Boolean,
) : TvUpdateActions {
    private val mutable = MutableStateFlow(TvUpdatesUi())
    val ui: StateFlow<TvUpdatesUi> = mutable
    private var job: Job? = null
    private var historyJob: Job? = null
    private var apk: File? = null
    private var awaitingPermission: AppRelease? = null
    /** The last check's outcome, shown on the card again when a panel closes. */
    private var lastResult: TvUpdateState? = null

    private fun settle(): TvUpdateState = lastResult ?: TvUpdateState.Idle
    private fun setUpdate(state: TvUpdateState) = mutable.update { it.copy(update = state) }

    /** The Updates page opened: reads the stored toggles and last check time (the center may be absent on a stripped build). */
    fun onPageOpened() {
        refreshSettings()
        loadHistory()
    }

    private fun refreshSettings() {
        runCatching { center.settings }.onSuccess { s ->
            mutable.update { it.copy(autoCheck = s.autoCheck, notify = s.notify, lastCheck = s.lastSuccessfulCheck) }
        }
    }

    override fun check() {
        val now = mutable.value.update
        if (now is TvUpdateState.Checking || now.isPanel) return
        job?.cancel()
        setUpdate(TvUpdateState.Checking)
        job = scope.launch {
            try {
                when (val result = center.check()) {
                    is UpdateCheck.Available -> {
                        val state = TvUpdateState.Available(currentVersion, result.release)
                        lastResult = state
                        mutable.update { it.copy(update = state, detail = result.release, detailInstallable = canInstallBuild) }
                    }
                    is UpdateCheck.UpToDate -> finishCheck(TvUpdateState.UpToDate(currentVersion))
                    is UpdateCheck.Development -> finishCheck(TvUpdateState.Development(result.latest?.version.orEmpty()))
                }
                refreshSettings()
                loadHistory(force = true)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                setUpdate(TvUpdateState.Failed(TvUpdateError.Check, null))
            }
        }
    }

    private fun finishCheck(state: TvUpdateState) {
        lastResult = state
        setUpdate(state)
    }

    /** Opens the release panel for a changelog row or the offered release. */
    override fun openRelease(release: AppRelease) {
        if (mutable.value.update.isPanel) return
        val installable = canInstallBuild && StableVersion.isNewer(currentVersion, release.version)
        mutable.update { it.copy(detail = release, detailInstallable = installable) }
    }

    /** "Download and install" in the release panel. */
    override fun download() {
        val ui = mutable.value
        val release = ui.detail?.takeIf { ui.detailInstallable } ?: return
        startDownload(release)
    }

    private fun startDownload(release: AppRelease) {
        job?.cancel()
        awaitingPermission = null
        mutable.update { it.copy(update = TvUpdateState.Downloading(release, 0f), detail = null) }
        job = scope.launch {
            var last = -1
            try {
                apk = center.download(release) { p ->
                    val percent = (p * 100).toInt()
                    if (percent != last && mutable.value.update is TvUpdateState.Downloading) {
                        last = percent
                        setUpdate(TvUpdateState.Downloading(release, p))
                    }
                }
                install(release)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                setUpdate(TvUpdateState.Failed(when {
                    t is UpdateException && t.kind == UpdateException.Kind.ChecksumMismatch -> TvUpdateError.Checksum
                    t is UpdateException && t.kind == UpdateException.Kind.MissingChecksum -> TvUpdateError.NoChecksum
                    else -> TvUpdateError.Network
                }, release))
            }
        }
    }

    /** Installs the downloaded file, first sending the user to "Install unknown apps" when it is off. */
    private fun install(release: AppRelease) {
        val file = apk?.takeIf { it.exists() } ?: return startDownload(release)
        if (!installer.canInstall()) {
            awaitingPermission = release
            setUpdate(TvUpdateState.NeedsPermission(release, runCatching { installer.openPermissionSettings() }.getOrDefault(false)))
            return
        }
        awaitingPermission = null
        setUpdate(TvUpdateState.Downloading(release, 1f))
        job = scope.launch {
            try {
                installer.install(file)
                setUpdate(TvUpdateState.Idle)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                setUpdate(TvUpdateState.Failed(TvUpdateError.Install, release))
            }
        }
    }

    /** Focusable Retry: repeats whatever failed. */
    override fun retry() {
        when (val s = mutable.value.update) {
            is TvUpdateState.Failed -> if (s.release == null) check() else install(s.release)
            is TvUpdateState.NeedsPermission -> install(s.release)
            else -> Unit
        }
    }

    /** Called from the activity's onResume: continues an install the user just allowed. */
    fun onResume() {
        val release = awaitingPermission ?: return
        if (installer.canInstall()) install(release)
    }

    override fun dismiss() {
        val panelled = mutable.value.update.isPanel
        if (panelled) job?.cancel()
        awaitingPermission = null
        mutable.update { it.copy(update = if (panelled) settle() else it.update, detail = null) }
    }

    override fun setAutoCheck(enabled: Boolean) {
        mutable.update { it.copy(autoCheck = enabled) }
        runCatching {
            center.settings.autoCheck = enabled
            center.reschedule()
        }
    }

    override fun setNotify(enabled: Boolean) {
        mutable.update { it.copy(notify = enabled) }
        runCatching {
            center.settings.notify = enabled
            center.reschedule()
        }
    }

    override fun loadHistory(force: Boolean) {
        val current = mutable.value.history
        if (!force && current is TvHistory.Loaded) return
        if (historyJob?.isActive == true) return
        if (current !is TvHistory.Loaded) mutable.update { it.copy(history = TvHistory.Loading) }
        historyJob = scope.launch {
            try {
                val entries = mapHistory(center.history(HISTORY_LIMIT))
                mutable.update { it.copy(history = TvHistory.Loaded(entries)) }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                if (mutable.value.history !is TvHistory.Loaded) mutable.update { it.copy(history = TvHistory.Failed) }
            }
        }
    }

    internal fun mapHistory(releases: List<AppRelease>): List<TvChangelogEntry> = releases.map { release ->
        TvChangelogEntry(
            release = release,
            date = releaseDate(release.publishedAt),
            newer = canInstallBuild && StableVersion.isNewer(currentVersion, release.version),
            installed = release.version == currentVersion,
        )
    }

    companion object {
        const val HISTORY_LIMIT = 10

        /** dd/MM/yyyy of a GitHub `published_at`, or null when blank or unreadable. */
        fun releaseDate(publishedAt: String): String? =
            PublishedAges.parse(publishedAt.takeIf(String::isNotBlank))?.date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
}
