package com.hhst.youtubelite.ui.about

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.hhst.youtubelite.BuildConfig
import com.hhst.youtubelite.core.update.ApkInstaller
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.AppUpdater
import com.hhst.youtubelite.core.update.UpdateChannel
import com.hhst.youtubelite.core.update.UpdateException
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.FileProvider
import com.hhst.youtubelite.R
import com.hhst.youtubelite.diagnostics.AppLog
import com.hhst.youtubelite.diagnostics.DiagnosticOutcome
import com.hhst.youtubelite.diagnostics.Documents
import com.hhst.youtubelite.extractor.Extractor
import com.hhst.youtubelite.extractor.MemCache
import com.hhst.youtubelite.ui.theme.AppTheme
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.java.KoinJavaComponent.get

class AboutActivity : ComponentActivity() {
    private var updateState by mutableStateOf<UpdateUiState>(UpdateUiState.None)
    private var pendingApk: File? = null
    private var awaitingPermission: AppRelease? = null
    private var downloadJob: Job? = null
    private val installer by lazy { ApkInstaller(this) }
    private val updater by lazy { AppUpdater(get<OkHttpClient>(OkHttpClient::class.java), cacheDir) }

    /** Only a signed release build can be replaced by a release APK; debug and variant builds cannot. */
    private fun canInstallBuild(version: String) = !BuildConfig.DEBUG && Regex("""\d+\.\d+\.\d+""").matches(version)

    private fun startDownload(release: AppRelease) {
        downloadJob?.cancel()
        updateState = UpdateUiState.Downloading(release, 0f)
        downloadJob = lifecycleScope.launch {
            var last = -1
            try {
                pendingApk = updater.download(release) { p ->
                    val percent = (p * 100).toInt()
                    if (percent != last) { last = percent; updateState = UpdateUiState.Downloading(release, p) }
                }
                install(release)
            } catch (c: CancellationException) { throw c
            } catch (t: Throwable) {
                AppLog.event(AppLog.Category.APP, "update_download_failed", failure = t)
                updateState = UpdateUiState.Failed(release, getString(when {
                    t is UpdateException && t.kind == UpdateException.Kind.ChecksumMismatch -> R.string.update_error_checksum
                    t is UpdateException && t.kind == UpdateException.Kind.MissingChecksum -> R.string.update_error_no_checksum
                    else -> R.string.update_error_network
                }))
            }
        }
    }

    /** Installs the downloaded APK, first sending the user to the "install unknown apps" switch if needed. */
    private fun install(release: AppRelease) {
        val apk = pendingApk?.takeIf { it.exists() } ?: return startDownload(release)
        if (!installer.canInstall()) {
            awaitingPermission = release
            updateState = UpdateUiState.Failed(release, getString(R.string.update_permission_needed))
            runCatching { startActivity(installer.permissionIntent()) }
            return
        }
        lifecycleScope.launch {
            updateState = runCatching { withContext(Dispatchers.IO) { installer.install(apk) } }.fold(
                { UpdateUiState.None },
                { AppLog.event(AppLog.Category.APP, "update_install_failed", failure = it)
                    UpdateUiState.Failed(release, getString(R.string.update_install_failed)) })
        }
    }

    override fun onResume() {
        super.onResume()
        val release = awaitingPermission ?: return
        awaitingPermission = null
        if (installer.canInstall()) install(release)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        com.hhst.youtubelite.ui.PhoneOrientation.lockOnPhone(this)
        setContent { AppTheme {
            val scope = rememberCoroutineScope()
            var status by rememberSaveable { mutableStateOf("") }
            var pendingArchive by rememberSaveable { mutableStateOf<String?>(null) }
            var busy by remember { mutableStateOf(pendingArchive != null) }
            val saveLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip"),
            ) { uri ->
                val archive = pendingArchive?.let(::File)
                pendingArchive = null
                val operation = AppLog.operation(AppLog.Category.STORAGE, "diagnostics_save")
                if (uri == null) {
                    operation.finish(DiagnosticOutcome.CANCELLED, "file_picker_cancelled")
                    busy = false
                    status = getString(R.string.about_save_cancelled)
                } else {
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                checkNotNull(archive) { "Missing diagnostic archive" }
                                Documents.save(archive) { contentResolver.openOutputStream(uri, "w") }
                            }
                        }
                        operation.finish(if (result.isSuccess) DiagnosticOutcome.SUCCESS else DiagnosticOutcome.FAILURE,
                            if (result.isSuccess) "diagnostics_saved" else "file_write_failed", result.exceptionOrNull())
                        status = getString(if (result.isSuccess) R.string.about_save_done else R.string.about_save_failed)
                        busy = false
                    }
                }
            }
            val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
            fun action(name: String, work: suspend () -> String) {
                if (busy) return
                busy = true; status = getString(R.string.about_working)
                scope.launch {
                    val operation = AppLog.operation(AppLog.Category.APP, name)
                    status = runCatching { work().also { operation.finish(DiagnosticOutcome.SUCCESS) } }.getOrElse {
                        operation.finish(if (it is CancellationException) DiagnosticOutcome.CANCELLED else DiagnosticOutcome.FAILURE, "operation_failed", it)
                        getString(R.string.about_operation_failed)
                    }
                    busy = false
                }
            }
            AboutScreen(applicationInfo.loadLabel(packageManager).toString(), version, { finish() }, busy, status,
                onSource = { openUrl(SOURCE) },
                onUpstream = { openUrl(UPSTREAM) },
                onUpdate = { action("check_update") {
                    val release = updater.latest(UpdateChannel.current())
                        ?: return@action getString(R.string.update_no_package)
                    when {
                        StableVersion.isDevelopment(version) -> getString(R.string.about_development_version, release.version)
                        updater.isNewer(version, release) -> {
                            if (canInstallBuild(version)) updateState = UpdateUiState.Available(version, release)
                            getString(R.string.about_update_available, release.version)
                        }
                        else -> getString(R.string.about_up_to_date, release.version)
                    }
                } },
                onRelease = { openUrl("$SOURCE/releases/latest") },
                update = updateState,
                onUpdateInstall = { (updateState as? UpdateUiState.Available)?.let { startDownload(it.release) } },
                onUpdateLater = { updateState = UpdateUiState.None },
                onUpdateCancel = { downloadJob?.cancel(); updateState = UpdateUiState.None },
                onUpdateRetry = { (updateState as? UpdateUiState.Failed)?.let { install(it.release) } },
                onClear = { action("clear_cache") {
                    // clearCache removes HTTP cache only; cookies and WebStorage keep the account.
                    WebView(this@AboutActivity).let { it.clearCache(true); it.destroy() }
                    withContext(Dispatchers.IO) {
                        val repositories = get<com.hhst.youtubelite.data.media.ContentRepositories>(
                            com.hhst.youtubelite.data.media.ContentRepositories::class.java)
                        // Cache owners cleared individually; playback SimpleCache, downloads,
                        // local library, account and queue data are deliberately not
                        // touched: SimpleCache may be in use and the rest is user data.
                        com.hhst.youtubelite.core.YoutubeThumbnail.clearMemory()
                        get<com.hhst.youtubelite.dearrow.DeArrowService>(com.hhst.youtubelite.dearrow.DeArrowService::class.java).clear()
                        repositories.avatars.clear()
                        repositories.watch.clearMemory()
                        repositories.snapshots?.clear()
                        get<MemCache>(MemCache::class.java).clear()
                        val kv = get<com.hhst.youtubelite.core.PreferenceStore>(com.hhst.youtubelite.core.PreferenceStore::class.java)
                        kv.keys().filter { it.startsWith("extractor:metadata:") || it.startsWith("extractor:stream:") || it.startsWith("extractor:segment:") }.forEach(kv::remove)
                        listOf("gallery", "thumbnails").forEach { File(cacheDir, it).deleteRecursively() }
                        AppLog.event(AppLog.Category.STORAGE, "cache_cleared", mapOf(
                            "owners" to "image_memory,display_decoration,avatar_directory,page_snapshots,mem_cache,watch_cache,gallery_files"))
                    }
                    getString(R.string.about_cache_cleared)
                } },
                onExport = { action("export_share") {
                    val archive = withContext(Dispatchers.IO) {
                        AppLog.export(this@AboutActivity)
                    }
                    val uri = FileProvider.getUriForFile(this@AboutActivity, "$packageName.download.fileprovider", archive)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("diagnostics", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, getString(R.string.about_export_logs)))
                    getString(R.string.about_export_ready)
                } },
                onSave = {
                    if (!busy) {
                        busy = true
                        status = getString(R.string.about_working)
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { AppLog.export(this@AboutActivity) }
                            }
                            result.onSuccess { archive ->
                                pendingArchive = archive.absolutePath
                                runCatching { saveLauncher.launch(archive.name) }.onFailure { failure ->
                                    AppLog.event(AppLog.Category.STORAGE, "diagnostics_picker_failed", failure = failure)
                                    pendingArchive = null
                                    busy = false
                                    status = getString(R.string.about_save_failed)
                                }
                            }.onFailure { failure ->
                                AppLog.event(AppLog.Category.STORAGE, "diagnostics_export_failed", failure = failure)
                                busy = false
                                status = getString(R.string.about_save_failed)
                            }
                        }
                    }
                })
        } }
    }
    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, R.string.application_not_found, Toast.LENGTH_SHORT).show() }
    }
    companion object {
        private const val SOURCE = "https://github.com/nev-minhnh/MinTube"
        /** The project MinTube is forked from (Litube 4.0.0, GPL-3.0). */
        private const val UPSTREAM = "https://github.com/HydeYYHH/litube"
    }
}
