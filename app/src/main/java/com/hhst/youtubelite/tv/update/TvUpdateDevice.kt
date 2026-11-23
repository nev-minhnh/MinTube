package com.hhst.youtubelite.tv.update

import android.content.Context
import com.hhst.youtubelite.core.update.ApkInstaller
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.UpdateCenter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.getKoin

/** Install side of the TV update flow: the shared [ApkInstaller] and its permission screen (fakes in tests). */
interface TvApkInstall {
    fun canInstall(): Boolean
    /** Opens "Install unknown apps" for this app; false when the device has no such screen. */
    fun openPermissionSettings(): Boolean
    suspend fun install(apk: File)
}

/** Device installer: the shared [ApkInstaller], unchanged. */
class TvApkInstallImpl(private val context: Context) : TvApkInstall {
    private val installer = ApkInstaller(context)

    override fun canInstall() = installer.canInstall()

    /** Some TV builds ship without the install-unknown-apps screen; the caller then shows manual steps. */
    override fun openPermissionSettings(): Boolean = runCatching {
        context.startActivity(installer.permissionIntent())
    }.isSuccess

    override suspend fun install(apk: File) = withContext(Dispatchers.IO) { installer.install(apk) }
}

/**
 * The app's [UpdateCenter] from Koin, resolved on first use so Settings never touches it until the
 * user opens Updates (and a build without the binding fails only there, inside the state holder's guards).
 */
class KoinUpdateCenter : UpdateCenter {
    private val center: UpdateCenter by lazy { getKoin().get<UpdateCenter>() }

    override val channel get() = center.channel
    override val currentVersion get() = center.currentVersion
    override val settings get() = center.settings
    override suspend fun check() = center.check()
    override suspend fun history(limit: Int): List<AppRelease> = center.history(limit)
    override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File = center.download(release, onProgress)
    override fun reschedule() = center.reschedule()
    override fun shareInstalledApk(context: Context) = center.shareInstalledApk(context)
}
