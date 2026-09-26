package com.internship.scritto.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.internship.scritto.R

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EVENT_REMINDER) return

        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val id = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_EVENT_TITLE)
            ?.ifBlank { "Scheduled item" } ?: "Scheduled item"
        val type = intent.getStringExtra(EXTRA_EVENT_TYPE)
        val label = if (type == "CLASS") "Class starting soon" else "Event starting soon"

        val notification = NotificationCompat.Builder(context, EventReminderScheduler.channelId())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id.hashCode(), notification)
    }

    companion object {
        const val ACTION_EVENT_REMINDER = "com.internship.scritto.ACTION_EVENT_REMINDER"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EVENT_TITLE = "event_title"
        const val EXTRA_EVENT_TYPE = "event_type"
    }
}
