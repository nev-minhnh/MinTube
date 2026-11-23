package com.hhst.youtubelite.ui.updates

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.update.ApkInstaller
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.DefaultUpdateCenter
import com.hhst.youtubelite.core.update.UpdateException
import com.hhst.youtubelite.diagnostics.AppLog
import com.hhst.youtubelite.ui.NativeActivity
import com.hhst.youtubelite.ui.about.StableVersion
import com.hhst.youtubelite.ui.design.setAppContent
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Hosts Updates -> App update -> release notes. Owns the check, download and install flow that
 * used to live in About; the screens in [UpdatesScreens] stay stateless.
 */
class UpdatesActivity : NativeActivity() {
    private val center: DefaultUpdateCenter by inject()
    private val installer by lazy { ApkInstaller(this) }

    private var status by mutableStateOf<UpdateStatus>(UpdateStatus.Unknown)
    private var lastCheck by mutableStateOf<Long?>(null)
    private var autoCheck by mutableStateOf(true)
    private var notify by mutableStateOf(true)
    private var history by mutableStateOf<HistoryState>(HistoryState.Loading)
    private var install by mutableStateOf<InstallState>(InstallState.Idle)

    private var pendingApk: File? = null
    private var awaitingPermission: AppRelease? = null
    private var downloadJob: Job? = null
    private var checkJob: Job? = null
    private var releaseAfterPermission: AppRelease? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshSettings()
        val release = releaseAfterPermission
        releaseAfterPermission = null
        if (release != null) {
            // Install flow: the answer never blocks the download.
            startDownload(release)
        } else {
            center.settings.notify = granted
            refreshSettings()
        }
    }

    private fun notificationsAllowed() = NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun needsPermission() = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun refreshSettings() {
        autoCheck = center.settings.autoCheck
        notify = center.settings.notify && notificationsAllowed()
        lastCheck = center.settings.lastSuccessfulCheck
    }

    private fun changeNotify(enabled: Boolean) {
        if (enabled && needsPermission()) {
            center.permissionAsked = true
            releaseAfterPermission = null
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            center.settings.notify = enabled
            refreshSettings()
            if (enabled && !notificationsAllowed()) openNotificationSettings()
        }
    }

    private fun changeAutoCheck(enabled: Boolean) {
        center.settings.autoCheck = enabled
        center.reschedule()
        refreshSettings()
    }

    private fun check() {
        if (status == UpdateStatus.Checking) return
        status = UpdateStatus.Checking
        checkJob = lifecycleScope.launch {
            status = try {
                center.check().toStatus()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                AppLog.event(AppLog.Category.APP, "update_check_failed", failure = t)
                UpdateStatus.Failed
            }
            refreshSettings()
        }
    }

    private fun loadHistory() {
        history = HistoryState.Loading
        lifecycleScope.launch {
            history = try {
                HistoryState.Loaded(center.history())
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                AppLog.event(AppLog.Category.APP, "update_history_failed", failure = t)
                HistoryState.Failed
            }
        }
    }

    /** Asks for the notification permission once, only when the user taps install, then downloads regardless. */
    private fun requestInstall(release: AppRelease) {
        if (needsPermission() && !center.permissionAsked) {
            center.permissionAsked = true
            releaseAfterPermission = release
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startDownload(release)
    }

    private fun startDownload(release: AppRelease) {
        downloadJob?.cancel()
        install = InstallState.Downloading(release, 0f)
        downloadJob = lifecycleScope.launch {
            var last = -1
            try {
                pendingApk = center.download(release) { p ->
                    val percent = (p * 100).toInt()
                    if (percent != last) { last = percent; install = InstallState.Downloading(release, p) }
                }
                installApk(release)
            } catch (c: CancellationException) { throw c
            } catch (t: Throwable) {
                AppLog.event(AppLog.Category.APP, "update_download_failed", failure = t)
                install = InstallState.Failed(release, getString(when {
                    t is UpdateException && t.kind == UpdateException.Kind.ChecksumMismatch -> R.string.update_error_checksum
                    t is UpdateException && t.kind == UpdateException.Kind.MissingChecksum -> R.string.update_error_no_checksum
                    else -> R.string.update_error_network
                }))
            }
        }
    }

    /** Installs the downloaded APK, first sending the user to the "install unknown apps" switch if needed. */
    private fun installApk(release: AppRelease) {
        // A build that is not a signed x.y.z release can never be replaced by a release APK.
        if (center.isDevelopment) { install = InstallState.Idle; return }
        val apk = pendingApk?.takeIf { it.exists() } ?: return startDownload(release)
        if (!installer.canInstall()) {
            awaitingPermission = release
            install = InstallState.Failed(release, getString(R.string.update_permission_needed))
            runCatching { startActivity(installer.permissionIntent()) }
            return
        }
        lifecycleScope.launch {
            install = runCatching { withContext(Dispatchers.IO) { installer.install(apk) } }.fold(
                { InstallState.Idle },
                {
                    AppLog.event(AppLog.Category.APP, "update_install_failed", failure = it)
                    InstallState.Failed(release, getString(R.string.update_install_failed))
                })
        }
    }

    private fun retry() {
        (install as? InstallState.Failed)?.let { installApk(it.release) }
    }

    override fun onResume() {
        super.onResume()
        refreshSettings()
        val release = awaitingPermission ?: return
        awaitingPermission = null
        if (installer.canInstall()) installApk(release)
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
    }

    private fun shareApk() {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { center.shareInstalledApk(this@UpdatesActivity) } }.onFailure {
                if (it is CancellationException) throw it
                AppLog.event(AppLog.Category.APP, "update_share_apk_failed", failure = it)
                Toast.makeText(this@UpdatesActivity, R.string.updates_share_apk_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.light(Color.WHITE, Color.WHITE), SystemBarStyle.light(Color.WHITE, Color.WHITE))
        refreshSettings()
        if (savedInstanceState == null) check()
        loadHistory()
        val initial = if (intent.getBooleanExtra(EXTRA_OPEN_APP, false)) PAGE_APP else PAGE_UPDATES
        setAppContent {
            var page by rememberSaveable { mutableStateOf(initial) }
            var releaseTag by rememberSaveable { mutableStateOf("") }
            val state = UpdatesUiState(center.currentVersion, status, lastCheck, autoCheck, notify, history, install)
            val frame = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            val release = (history as? HistoryState.Loaded)?.releases?.firstOrNull { it.tag == releaseTag }
            BackHandler(enabled = page != PAGE_UPDATES) { page = PAGE_UPDATES }
            when {
                page == PAGE_APP -> AppUpdateScreen(state, onBack = { page = PAGE_UPDATES }, onCheck = ::check,
                    onInstall = ::requestInstall, onCancel = { downloadJob?.cancel(); install = InstallState.Idle },
                    onRetry = ::retry, modifier = frame)
                page == PAGE_RELEASE && release != null -> ReleaseNotesScreen(release, center.currentVersion,
                    newer = StableVersion.isNewer(center.currentVersion, release.version),
                    installable = !center.isDevelopment, install = install,
                    onBack = { page = PAGE_UPDATES }, onInstall = ::requestInstall,
                    onCancel = { downloadJob?.cancel(); install = InstallState.Idle }, onRetry = ::retry, modifier = frame)
                else -> UpdatesScreen(state, onBack = { finish() }, onOpenApp = { page = PAGE_APP },
                    onOpenRelease = { releaseTag = it.tag; page = PAGE_RELEASE },
                    onAutoCheck = ::changeAutoCheck, onNotify = ::changeNotify,
                    onNotificationSettings = ::openNotificationSettings, onShareApk = ::shareApk,
                    onRetryHistory = ::loadHistory, modifier = frame)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_APP = "updates_open_app"
        private const val PAGE_UPDATES = "updates"
        private const val PAGE_APP = "app"
        private const val PAGE_RELEASE = "release"

        /** [openApp] lands directly on the App update page (the update notification does). */
        fun intent(context: Context, openApp: Boolean = false): Intent =
            Intent(context, UpdatesActivity::class.java).putExtra(EXTRA_OPEN_APP, openApp)
    }
}
