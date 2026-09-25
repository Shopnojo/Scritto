package com.internship.scritto.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.internship.scritto.R

class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TASK_REMINDER) return

        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TASK_TITLE)
            ?.ifBlank { "Task due" }
            ?: "Task due"

        val notification = NotificationCompat.Builder(
            context,
            TaskReminderScheduler.channelId()
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Task due")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        manager.notify(taskId.hashCode(), notification)
    }

    companion object {
        const val ACTION_TASK_REMINDER =
            "com.internship.scritto.ACTION_TASK_REMINDER"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
    }
}
