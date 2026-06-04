package com.scholar.app.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scholar.app.MainActivity
import com.scholar.app.data.user.UserDatabase

/**
 * Runs periodically (see [Reminders.schedule]) and posts a reminder when reviews are waiting or the
 * user has gone quiet for a day. Best-effort: stays silent (and never errors) if there's nothing to
 * say or notifications aren't permitted.
 */
class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = UserDatabase.get(ctx)
        val now = System.currentTimeMillis()
        val due = db.cardDao().dueCount(now)
        val last = db.reviewLogDao().lastReviewMillis() ?: 0L

        val content: Pair<String, String>? = when {
            due > 0 -> "$due review${if (due == 1) "" else "s"} ready" to
                "Your cultivation awaits — a few minutes keeps the gains."
            last > 0L && now - last >= DAY_MS -> "Don't break your streak" to
                "It's been a day since your last review. A short session keeps your momentum."
            else -> null
        }
        content?.let { (title, body) -> notify(ctx, title, body) }
        return Result.success()
    }

    private fun notify(ctx: Context, title: String, body: String) {
        Reminders.ensureChannel(ctx)
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return   // auto-granted < API 33; explicit ≥ 33

        val tap = Intent(ctx, MainActivity::class.java).apply {
            putExtra("route", "review")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true).setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(ctx).notify(Reminders.NOTIFICATION_ID, n)
    }

    companion object { private const val DAY_MS = 24 * 60 * 60 * 1000L }
}
