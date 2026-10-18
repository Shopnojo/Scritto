package com.internship.scritto.telemetry

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.internship.scritto.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Where [TelemetryCore] keeps its few values. Backed by SharedPreferences in the app, a map in tests. */
interface TelemetryStore {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

/**
 * Anonymous usage statistics.
 *
 * - An event is only a short fixed name such as "note_created" plus a timestamp. Never any text the
 *   user wrote, file names, contact details or advertising ids.
 * - The installation id is a random UUID created on first use. It is not derived from the device or any account.
 * - Events wait in a small on-device queue ([MAX_QUEUE]) until [TelemetryWorker] can upload them.
 *
 * What is collected must stay in sync with PRIVACY_POLICY.md and docs/PLAY_DATA_SAFETY.md.
 */
class TelemetryCore(
    private val store: TelemetryStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {

    /** The random installation id, created the first time it is needed. */
    @Synchronized
    fun installationId(): String {
        store.get(KEY_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        return newId().also { store.set(KEY_ID, it) }
    }

    /** Queues [name] for upload. Does nothing when [name] isn't a plain event name. */
    @Synchronized
    fun track(name: String) {
        if (!NAME_PATTERN.matches(name)) return

        val events = readQueue()
        events.put(JSONObject().put("name", name).put("at", now()))

        val kept = if (events.length() > MAX_QUEUE) {
            JSONArray().also { trimmed ->
                for (index in events.length() - MAX_QUEUE until events.length()) trimmed.put(events.get(index))
            }
        } else {
            events
        }

        store.set(KEY_QUEUE, kept.toString())
    }

    @Synchronized
    fun pendingCount(): Int = readQueue().length()

    /** Up to [max] oldest events, still queued. Call [remove] once they were uploaded. */
    @Synchronized
    fun peek(max: Int): List<JSONObject> {
        val events = readQueue()
        return (0 until minOf(max, events.length())).map { events.getJSONObject(it) }
    }

    @Synchronized
    fun remove(count: Int) {
        val events = readQueue()
        if (count <= 0 || events.length() == 0) return

        val rest = JSONArray()
        for (index in count until events.length()) rest.put(events.get(index))
        store.set(KEY_QUEUE, if (rest.length() == 0) null else rest.toString())
    }

    @Synchronized
    fun isRegistered(): Boolean = store.get(KEY_REGISTERED) == "1"

    @Synchronized
    fun markRegistered() {
        store.set(KEY_REGISTERED, "1")
    }

    private fun readQueue(): JSONArray =
        runCatching { JSONArray(store.get(KEY_QUEUE) ?: "[]") }.getOrDefault(JSONArray())

    companion object {
        const val MAX_QUEUE = 200
        val NAME_PATTERN = Regex("[a-z][a-z0-9_]{0,39}")

        private const val KEY_ID = "installation_id"
        private const val KEY_REGISTERED = "registered"
        private const val KEY_QUEUE = "queue"
    }
}

enum class FlushResult { DONE, RETRY }

/** The upload step, kept free of Android classes so it can be unit tested. */
object TelemetryFlusher {

    private const val BATCH_SIZE = 50

    // Synchronized: the launch-time and the periodic job can start together and must not upload the same events twice.
    @Synchronized
    fun flush(core: TelemetryCore, api: ScrittoApi, appVersion: String, androidSdk: Int): FlushResult {
        if (!api.isConfigured) return FlushResult.DONE

        val id = core.installationId()

        if (!core.isRegistered()) {
            val body = JSONObject()
                .put("installation_id", id)
                .put("app_version", appVersion)
                .put("android_sdk", androidSdk)

            when (api.postJson("/v1/devices", body)) {
                ApiOutcome.OK -> core.markRegistered()
                ApiOutcome.REJECTED -> return FlushResult.DONE
                ApiOutcome.RETRY -> return FlushResult.RETRY
            }
        }

        while (true) {
            val batch = core.peek(BATCH_SIZE)
            if (batch.isEmpty()) return FlushResult.DONE

            val body = JSONObject()
                .put("installation_id", id)
                .put("events", JSONArray(batch))

            when (api.postJson("/v1/events", body)) {
                // A batch the server refuses outright would block the queue forever, so it is dropped.
                ApiOutcome.OK, ApiOutcome.REJECTED -> core.remove(batch.size)
                ApiOutcome.RETRY -> return FlushResult.RETRY
            }
        }
    }
}

/**
 * App-wide entry point: `Telemetry.track("note_created")`.
 *
 * It does nothing before [init], and nothing at all in builds without a backend URL
 * (`SCRITTO_API_URL` blank), so such builds neither queue nor send anything.
 */
object Telemetry {

    private const val PREFS = "scritto_telemetry"
    private const val PERIODIC_WORK = "scritto_telemetry_upload"
    private const val NOW_WORK = "scritto_telemetry_upload_now"

    @Volatile
    private var core: TelemetryCore? = null

    fun init(context: Context) {
        if (core != null || !ScrittoApi(BuildConfig.SCRITTO_API_URL).isConfigured) return

        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        core = TelemetryCore(object : TelemetryStore {
            override fun get(key: String): String? = prefs.getString(key, null)
            override fun set(key: String, value: String?) {
                prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            }
        })

        schedule(app)
    }

    fun track(name: String) {
        core?.track(name)
    }

    /** Uploads queued events. Used by [TelemetryWorker]; the result says whether to try again later. */
    fun flush(api: ScrittoApi): FlushResult {
        val current = core ?: return FlushResult.DONE
        return TelemetryFlusher.flush(current, api, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT)
    }

    private fun schedule(context: Context) {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = WorkManager.getInstance(context)

        work.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TelemetryWorker>(12, TimeUnit.HOURS)
                .setConstraints(online)
                .build()
        )

        // Also try soon after launch, so a new install doesn't wait half a day for its first upload.
        work.enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TelemetryWorker>().setConstraints(online).build()
        )
    }
}
