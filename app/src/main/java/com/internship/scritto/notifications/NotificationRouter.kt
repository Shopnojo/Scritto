package com.internship.scritto.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.internship.scritto.MainActivity

/** The screen a notification tap should open. */
enum class NotificationTarget(val key: String) {
    HOME("home"),
    TASKS("tasks"),
    SCHEDULE("schedule")
}

/**
 * Carries a notification tap from the system into the Compose navigation.
 *
 * The tap delivers an intent to [MainActivity]; [handle] stores the requested
 * screen here and ScrittoNavigation navigates to it, then calls [consume].
 */
object NotificationRouter {

    private const val EXTRA_TARGET = "scritto.notification_target"

    var pending: NotificationTarget? by mutableStateOf(null)
        private set

    /** A PendingIntent that opens the app on [target] when a notification is tapped. */
    fun contentIntent(
        context: Context,
        target: NotificationTarget,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_TARGET, target.key)
        }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
    }

    /** Reads a notification target from [intent]. The extra is removed so a rotation does not navigate twice. */
    fun handle(intent: Intent?) {
        if (intent == null) return
        val key = intent.getStringExtra(EXTRA_TARGET) ?: return
        intent.removeExtra(EXTRA_TARGET)

        pending = NotificationTarget.entries.firstOrNull { it.key == key }
    }

    fun consume() {
        pending = null
    }
}
