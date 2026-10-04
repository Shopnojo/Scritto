package com.internship.scritto.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Periodic background check that may post a follow-up notification. */
class FollowUpWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // A failed check should not retry aggressively; the next period tries again.
        runCatching { FollowUpReminder.checkAndNotify(applicationContext) }
        return Result.success()
    }
}
