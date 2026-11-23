package com.hhst.youtubelite.core.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hhst.youtubelite.R
import com.hhst.youtubelite.diagnostics.AppLog
import com.hhst.youtubelite.ui.updates.UpdatesActivity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.koin.java.KoinJavaComponent

/** Daily background check on WorkManager, only on a connected network. */
class WorkUpdateScheduler(private val context: Context) : UpdateScheduler {
    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object { const val WORK_NAME = "app-update-check" }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val center = KoinJavaComponent.get<DefaultUpdateCenter>(DefaultUpdateCenter::class.java)
        if (!center.settings.autoCheck) return Result.success()
        return try {
            val result = center.check()
            if (result is UpdateCheck.Available && center.settings.notify &&
                center.notifiedVersion != result.release.version) {
                if (UpdateNotifier.post(applicationContext, result.release)) center.markNotified(result.release.version)
            }
            Result.success()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppLog.event(AppLog.Category.APP, "update_background_check_failed", failure = t)
            // A failed check records nothing; the next period tries again.
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}

object UpdateNotifier {
    const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 7410

    /** Posts the "update available" notification; false when notifications are blocked. Never throws. */
    fun post(context: Context, release: AppRelease): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, UpdatesActivity.intent(context, openApp = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_play)
            .setContentTitle(context.getString(R.string.update_notification_title, release.version))
            .setContentText(context.getString(R.string.update_notification_text))
            .setContentIntent(open).setAutoCancel(true).build()
        @Suppress("MissingPermission") manager.notify(NOTIFICATION_ID, notification)
        true
    }.getOrDefault(false)
}
