package com.hhst.youtubelite.core.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hhst.youtubelite.extension.PrefStore
import com.hhst.youtubelite.ui.about.StableVersion
import java.io.File

/** Where releases come from; [AppUpdater] in production, a fake in tests. */
interface ReleaseSource {
    suspend fun latest(channel: UpdateChannel): AppRelease?
    suspend fun history(channel: UpdateChannel, limit: Int): List<AppRelease>
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File
}

class AppUpdaterSource(private val updater: AppUpdater) : ReleaseSource {
    override suspend fun latest(channel: UpdateChannel) = updater.latest(channel)
    override suspend fun history(channel: UpdateChannel, limit: Int) = updater.history(channel, limit)
    override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit) = updater.download(release, onProgress)
}

/** Seam over WorkManager so scheduling is testable. */
interface UpdateScheduler {
    fun schedule()
    fun cancel()
}

/** Update preferences on the app's preference store. */
class PreferenceUpdateSettings(private val prefs: PrefStore) : UpdateSettings {
    override var autoCheck: Boolean
        get() = prefs.getBool(KEY_AUTO, true)
        set(value) = prefs.putBool(KEY_AUTO, value)
    override var notify: Boolean
        get() = prefs.getBool(KEY_NOTIFY, true)
        set(value) = prefs.putBool(KEY_NOTIFY, value)
    override val lastSuccessfulCheck: Long?
        get() = if (prefs.contains(KEY_LAST)) prefs.getLong(KEY_LAST, 0L) else null

    fun recordCheck(at: Long, latestVersion: String?) {
        prefs.putLong(KEY_LAST, at)
        prefs.putString(KEY_LATEST, latestVersion.orEmpty())
    }

    /** Version name found by the last successful check; blank when none. */
    val cachedLatest: String get() = prefs.getString(KEY_LATEST, "")

    /** Version a notification was already posted for, so the daily check does not repeat it. */
    var notifiedVersion: String
        get() = prefs.getString(KEY_NOTIFIED, "")
        set(value) = prefs.putString(KEY_NOTIFIED, value)

    /** Set once the user has been asked for the Android 13+ notification permission. */
    var permissionAsked: Boolean
        get() = prefs.getBool(KEY_ASKED, false)
        set(value) = prefs.putBool(KEY_ASKED, value)

    companion object {
        const val KEY_AUTO = "updates:auto-check"
        const val KEY_NOTIFY = "updates:notify"
        const val KEY_LAST = "updates:last-check"
        const val KEY_LATEST = "updates:latest-version"
        const val KEY_NOTIFIED = "updates:notified-version"
        const val KEY_ASKED = "updates:permission-asked"
    }
}

/** Status line shown by Settings, from the last stored check. */
enum class CachedUpdateStatus { UpToDate, Available, Development }

class DefaultUpdateCenter(
    private val source: ReleaseSource,
    private val prefsSettings: PreferenceUpdateSettings,
    private val scheduler: UpdateScheduler,
    override val channel: UpdateChannel,
    override val currentVersion: String,
    private val debugBuild: Boolean,
    private val sourceApk: () -> File? = { null },
    private val shareDir: () -> File? = { null },
    private val authority: () -> String = { "" },
    private val now: () -> Long = System::currentTimeMillis,
) : UpdateCenter {
    override val settings: UpdateSettings get() = prefsSettings

    /** Debug and non x.y.z builds are never replaced by a release. */
    val isDevelopment: Boolean
        get() = debugBuild || !Regex("""\d+\.\d+\.\d+""").matches(currentVersion) || StableVersion.isDevelopment(currentVersion)

    override suspend fun check(): UpdateCheck {
        val latest = source.latest(channel)
        prefsSettings.recordCheck(now(), latest?.version)
        return when {
            isDevelopment -> UpdateCheck.Development(latest)
            latest != null && StableVersion.isNewer(currentVersion, latest.version) -> UpdateCheck.Available(latest)
            else -> UpdateCheck.UpToDate(latest)
        }
    }

    override suspend fun history(limit: Int): List<AppRelease> = source.history(channel, limit)

    override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File =
        source.download(release, onProgress)

    override fun reschedule() {
        if (settings.autoCheck) scheduler.schedule() else scheduler.cancel()
    }

    /** Status from the last stored check, without network. */
    fun cachedStatus(): CachedUpdateStatus {
        val latest = prefsSettings.cachedLatest
        return when {
            isDevelopment -> CachedUpdateStatus.Development
            latest.isNotBlank() && StableVersion.isNewer(currentVersion, latest) -> CachedUpdateStatus.Available
            else -> CachedUpdateStatus.UpToDate
        }
    }

    val cachedLatestVersion: String get() = prefsSettings.cachedLatest
    val notifiedVersion: String get() = prefsSettings.notifiedVersion
    fun markNotified(version: String) { prefsSettings.notifiedVersion = version }
    var permissionAsked: Boolean
        get() = prefsSettings.permissionAsked
        set(value) { prefsSettings.permissionAsked = value }

    /** Copies the installed APK to cache and opens the share sheet. Call off the main thread: it copies tens of MB. */
    override fun shareInstalledApk(context: Context) {
        val apk = checkNotNull(sourceApk()) { "No installed APK" }
        val dir = checkNotNull(shareDir()) { "No share directory" }
        dir.deleteRecursively()
        check(dir.mkdirs() || dir.isDirectory) { "Cannot create ${dir.path}" }
        val copy = File(dir, "MinTube-$currentVersion.apk")
        apk.copyTo(copy, overwrite = true)
        val uri = FileProvider.getUriForFile(context, authority(), copy)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = APK_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("apk", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val SHARE_DIR = "shared-apk"
    }
}
