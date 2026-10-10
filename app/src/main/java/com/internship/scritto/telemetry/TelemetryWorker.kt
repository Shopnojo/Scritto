package com.internship.scritto.telemetry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.internship.scritto.BuildConfig

/** Background job: registers this installation once, then uploads queued usage events. */
class TelemetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The process may have been started just for this job, so make sure the queue is loaded.
        Telemetry.init(applicationContext)

        val outcome = runCatching { Telemetry.flush(ScrittoApi(BuildConfig.SCRITTO_API_URL)) }
            .getOrDefault(FlushResult.DONE)

        return if (outcome == FlushResult.RETRY) Result.retry() else Result.success()
    }
}
