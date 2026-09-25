package com.internship.scritto.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.internship.scritto.data.repository.ScrittoStore

class TaskReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        ScrittoStore.initialize(context)

        ScrittoStore.tasks
            .filterNot { it.completed }
            .forEach { task ->
                TaskReminderScheduler.schedule(context, task)
            }
    }
}
