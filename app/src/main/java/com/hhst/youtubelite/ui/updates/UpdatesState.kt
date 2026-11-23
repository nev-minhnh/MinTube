package com.hhst.youtubelite.ui.updates

import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.UpdateCheck
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Outcome of the latest check on the App update page. */
sealed interface UpdateStatus {
    data object Unknown : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val latest: String?) : UpdateStatus
    data class Available(val release: AppRelease) : UpdateStatus
    data class Development(val latest: String?) : UpdateStatus
    data object Failed : UpdateStatus
}

fun UpdateCheck.toStatus(): UpdateStatus = when (this) {
    is UpdateCheck.UpToDate -> UpdateStatus.UpToDate(latest?.version)
    is UpdateCheck.Available -> UpdateStatus.Available(release)
    is UpdateCheck.Development -> UpdateStatus.Development(latest?.version)
}

/** Download and install progress of one release. */
sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val release: AppRelease, val progress: Float) : InstallState
    data class Failed(val release: AppRelease, val message: String) : InstallState
}

sealed interface HistoryState {
    data object Loading : HistoryState
    data class Loaded(val releases: List<AppRelease>) : HistoryState
    data object Failed : HistoryState
}

data class UpdatesUiState(
    val currentVersion: String,
    val status: UpdateStatus = UpdateStatus.Unknown,
    val lastCheck: Long? = null,
    val autoCheck: Boolean = true,
    val notify: Boolean = true,
    val history: HistoryState = HistoryState.Loading,
    val install: InstallState = InstallState.Idle,
)

object UpdateFormat {
    /** Local time when [at] is today, otherwise local date and time. */
    fun lastCheck(at: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()): String {
        val moment = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = Date.from(moment.toInstant())
        return if (moment.toLocalDate() == today) {
            DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(date)
        } else {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                .apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(date)
        }
    }

    /** `2026-11-22` from a GitHub `published_at`; null when blank or malformed. */
    fun releaseDate(publishedAt: String): String? =
        Regex("""^(\d{4}-\d{2}-\d{2})""").find(publishedAt.trim())?.groupValues?.get(1)
            ?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
}
