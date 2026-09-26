package com.internship.scritto.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.internship.scritto.data.model.ScheduleEvent

object EventReminderScheduler {
    private const val CHANNEL_ID = "schedule_event_reminders"
    private const val CHANNEL_NAME = "Schedule reminders"

    fun schedule(context: Context, event: ScheduleEvent) {
        val reminderAt = event.startAt - event.reminderMinutes * 60_000L
        if (reminderAt <= System.currentTimeMillis()) return

        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminderAt,
            pendingIntent(context, event)
        )
    }

    fun cancel(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                eventId.hashCode(),
                Intent(context, EventReminderReceiver::class.java).apply {
                    action = EventReminderReceiver.ACTION_EVENT_REMINDER
                    putExtra(EventReminderReceiver.EXTRA_EVENT_ID, eventId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        )
    }

    fun channelId(): String = CHANNEL_ID

    private fun pendingIntent(context: Context, event: ScheduleEvent): PendingIntent {
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            action = EventReminderReceiver.ACTION_EVENT_REMINDER
            putExtra(EventReminderReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(EventReminderReceiver.EXTRA_EVENT_TITLE, event.title)
            putExtra(EventReminderReceiver.EXTRA_EVENT_TYPE, event.type.name)
        }

        return PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for scheduled events and classes."
                }
            )
        }
    }
}
