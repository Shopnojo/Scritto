package com.internship.scritto.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.internship.scritto.R
import com.internship.scritto.data.repository.ScrittoStore
import java.util.concurrent.TimeUnit

/**
 * Local "come back to Scritto" reminders. A periodic worker checks the store and nudges the
 * user when tasks are overdue, or when the app has not been opened for a while.
 *
 * Everything runs on the device, so it needs no server. Real remote push (FCM) would need a
 * Firebase project and a backend that sends the messages.
 */
object FollowUpReminder {

    private const val WORK_NAME = "scritto_follow_up"
    private const val PREFS_NAME = "scritto_follow_up"
    private const val KEY_LAST_OPENED = "last_opened_at"
    private const val KEY_LAST_OVERDUE_NUDGE = "last_overdue_nudge_at"
    private const val KEY_LAST_INACTIVE_NUDGE = "last_inactive_nudge_at"

    private const val CHANNEL_ID = "follow_up_reminders"
    private const val CHANNEL_NAME = "Follow-up reminders"

    private const val OVERDUE_NOTIFICATION_ID = 4101
    private const val INACTIVE_NOTIFICATION_ID = 4102

    private const val CHECK_INTERVAL_HOURS = 6L
    private val INACTIVE_AFTER_MS = TimeUnit.HOURS.toMillis(24)
    private val OVERDUE_COOLDOWN_MS = TimeUnit.HOURS.toMillis(12)
    private val INACTIVE_COOLDOWN_MS = TimeUnit.HOURS.toMillis(24)

    /** Registers the periodic check. KEEP leaves an existing schedule alone on every launch. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<FollowUpWorker>(
            CHECK_INTERVAL_HOURS,
            TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Called whenever the app comes to the foreground, so the inactivity clock restarts. */
    fun markAppOpened(context: Context) {
        prefs(context).edit()
            .putLong(KEY_LAST_OPENED, System.currentTimeMillis())
            .apply()
    }

    /** Decides whether a nudge is due and posts it. Runs from [FollowUpWorker]. */
    fun checkAndNotify(context: Context) {
        ScrittoStore.initialize(context.applicationContext)

        if (!canPostNotifications(context)) return

        val now = System.currentTimeMillis()
        val prefs = prefs(context)
        val open = ScrittoStore.tasks.filterNot { it.completed }
        val overdue = open.filter { it.dueAt in 1L..now }

        val lastOpened = prefs.getLong(KEY_LAST_OPENED, now)
        val inactive = now - lastOpened >= INACTIVE_AFTER_MS

        if (overdue.isNotEmpty() &&
            now - prefs.getLong(KEY_LAST_OVERDUE_NUDGE, 0L) >= OVERDUE_COOLDOWN_MS
        ) {
            val count = overdue.size
            post(
                context = context,
                id = OVERDUE_NOTIFICATION_ID,
                title = "$count overdue task${if (count == 1) "" else "s"}",
                text = "\"${overdue.first().title.ifBlank { "Untitled task" }}\" is past due. Open Scritto to finish it.",
                target = NotificationTarget.TASKS
            )
            prefs.edit().putLong(KEY_LAST_OVERDUE_NUDGE, now).apply()
        } else if (inactive &&
            now - prefs.getLong(KEY_LAST_INACTIVE_NUDGE, 0L) >= INACTIVE_COOLDOWN_MS
        ) {
            val count = open.size
            if (count > 0) {
                post(
                    context = context,
                    id = INACTIVE_NOTIFICATION_ID,
                    title = "You still have $count unfinished task${if (count == 1) "" else "s"}",
                    text = "Open Scritto and pick up where you left off.",
                    target = NotificationTarget.TASKS
                )
            } else {
                post(
                    context = context,
                    id = INACTIVE_NOTIFICATION_ID,
                    title = "Missing your notes?",
                    text = "Jot down an idea or plan your next task in Scritto.",
                    target = NotificationTarget.HOME
                )
            }
            prefs.edit().putLong(KEY_LAST_INACTIVE_NUDGE, now).apply()
        }
    }

    private fun post(
        context: Context,
        id: Int,
        title: String,
        text: String,
        target: NotificationTarget
    ) {
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(NotificationRouter.contentIntent(context, target, id))
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Nudges when tasks are overdue or Scritto has been left unopened."
                }
            )
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
