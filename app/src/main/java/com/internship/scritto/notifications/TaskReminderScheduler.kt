package com.internship.scritto.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.internship.scritto.data.model.Task

object TaskReminderScheduler {

    private const val CHANNEL_ID = "task_due_reminders"
    private const val CHANNEL_NAME = "Task reminders"

    fun schedule(context: Context, task: Task) {
        if (task.completed || task.dueAt <= System.currentTimeMillis()) return

        createNotificationChannel(context)

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pendingIntent = pendingIntent(context, task)

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            task.dueAt,
            pendingIntent
        )
    }

    fun cancel(context: Context, taskId: String) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                Intent(context, TaskReminderReceiver::class.java).apply {
                    action = TaskReminderReceiver.ACTION_TASK_REMINDER
                    putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        )
    }

    fun channelId(): String = CHANNEL_ID

    private fun pendingIntent(
        context: Context,
        task: Task
    ): PendingIntent {
        val intent = Intent(
            context,
            TaskReminderReceiver::class.java
        ).apply {
            action = TaskReminderReceiver.ACTION_TASK_REMINDER
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, task.title)
        }

        return PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when tasks reach their due time."
                }
            )
        }
    }
}
