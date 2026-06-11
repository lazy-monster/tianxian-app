package com.tianxian.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules the periodic review-reminder check and owns its notification channel. */
object Reminders {
    const val CHANNEL_ID = "reviews"
    const val NOTIFICATION_ID = 1001
    private const val WORK = "review-reminder"

    /** Create the notification channel (minSdk 26, so always available). Idempotent. */
    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Review reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Nudges when your spaced-repetition reviews are ready, or after a quiet day."
                }
            )
        }
    }

    /** Enqueue (or refresh) a ~twice-daily check that decides whether to notify. */
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<ReviewReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}
