package com.hhst.youtubelite.tv.update

import android.content.Context
import com.hhst.youtubelite.core.update.ApkInstaller
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.AppUpdater
import com.hhst.youtubelite.core.update.UpdateChannel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Device gateway: the shared [AppUpdater] and [ApkInstaller], unchanged. */
class TvUpdateGatewayImpl(private val context: Context, http: OkHttpClient) : TvUpdateGateway {
    private val updater = AppUpdater(http, context.cacheDir)
    private val installer = ApkInstaller(context)

    override suspend fun latest(): AppRelease? = updater.latest(UpdateChannel.current())
    override fun isNewer(current: String, release: AppRelease) = updater.isNewer(current, release)
    override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File = updater.download(release, onProgress)
    override fun canInstall() = installer.canInstall()

    /** Some TV builds ship without the install-unknown-apps screen; the caller then shows manual steps. */
    override fun openPermissionSettings(): Boolean = runCatching {
        context.startActivity(installer.permissionIntent())
    }.isSuccess

    override suspend fun install(apk: File) = withContext(Dispatchers.IO) { installer.install(apk) }
}
