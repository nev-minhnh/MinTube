package com.hhst.youtubelite.core.update

import android.content.Context

/**
 * Shared contract for the MinMusic-style Updates screen on phone/tablet and TV.
 * Branch feat/updates-screen implements it (DefaultUpdateCenter, registered in Koin as UpdateCenter);
 * the TV branch only calls it. Do not change these signatures without updating both UIs.
 */
interface UpdateSettings {
    /** Periodic background check (about once a day). Default on. */
    var autoCheck: Boolean
    /** System notification when the background check finds a newer version. Default on. */
    var notify: Boolean
    /** Epoch millis of the last check that reached GitHub, or null if none yet. */
    val lastSuccessfulCheck: Long?
}

sealed interface UpdateCheck {
    data class UpToDate(val latest: AppRelease?) : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck
    /** Debug or non x.y.z build: never installs. */
    data class Development(val latest: AppRelease?) : UpdateCheck
}

interface UpdateCenter {
    val channel: UpdateChannel
    /** The running version name, e.g. "1.0.10" (TV "1.0.6"). */
    val currentVersion: String
    val settings: UpdateSettings
    /** Checks GitHub now for [channel]; records [UpdateSettings.lastSuccessfulCheck] on success; throws on failure. */
    suspend fun check(): UpdateCheck
    /** Newest-first releases of [channel] (with notes and [AppRelease.publishedAt]) for the changelog list. */
    suspend fun history(limit: Int = 10): List<AppRelease>
    /** Download (SHA-256 verified) through the existing [AppUpdater]. */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): java.io.File
    /** Applies [UpdateSettings.autoCheck] to the periodic background check (schedule or cancel). */
    fun reschedule()
    /** Opens the system share sheet with the installed APK of this app. */
    fun shareInstalledApk(context: Context)
}
