package com.internship.scritto.telemetry

import com.internship.scritto.ai.HttpResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TelemetryTest {

    private class MapStore : TelemetryStore {
        val values = HashMap<String, String>()
        override fun get(key: String) = values[key]
        override fun set(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    private fun core(store: MapStore = MapStore()): TelemetryCore {
        var ids = 0
        var clock = 1_000L
        return TelemetryCore(store, now = { clock++ }, newId = { "id-${++ids}" })
    }

    // ---- identity and queue ------------------------------------------------

    @Test
    fun installationIdIsCreatedOnceAndKept() {
        val store = MapStore()

        assertEquals("id-1", core(store).installationId())
        assertEquals("id-1", core(store).installationId())
        assertEquals(1, store.values.size)
    }

    @Test
    fun trackingQueuesWithoutAnySwitch() {
        val core = core()

        core.track("note_created")

        assertEquals(1, core.pendingCount())
        assertEquals("note_created", core.peek(10).single().getString("name"))
    }

    // ---- what may be recorded -------------------------------------------

    @Test
    fun onlyPlainEventNamesAreAccepted() {
        val core = core()

        core.track("")
        core.track("Note Created")
        core.track("note created: my secret title")
        core.track("a".repeat(41))
        core.track("note_created")

        assertEquals(listOf("note_created"), core.peek(10).map { it.getString("name") })
    }

    @Test
    fun queueKeepsOnlyTheNewestEvents() {
        val core = core()

        repeat(TelemetryCore.MAX_QUEUE + 25) { core.track("e$it") }

        assertEquals(TelemetryCore.MAX_QUEUE, core.pendingCount())
        assertEquals("e25", core.peek(1).single().getString("name"))
    }

    @Test
    fun removeDropsTheOldestFirst() {
        val core = core()
        listOf("a", "b", "c").forEach(core::track)

        core.remove(2)

        assertEquals(listOf("c"), core.peek(10).map { it.getString("name") })
    }

    // ---- uploading ------------------------------------------------------

    private class FakeServer(private val respond: (path: String) -> Int) : ApiTransport {
        val requests = mutableListOf<Pair<String, JSONObject>>()
        override fun post(url: String, body: String): HttpResult {
            val path = url.substringAfter("example.test")
            requests += path to JSONObject(body)
            return HttpResult(respond(path), "")
        }
    }

    private fun api(transport: ApiTransport) = ScrittoApi("https://example.test/", transport, allowInsecure = false)

    @Test
    fun blankOrInsecureUrlsAreNotConfigured() {
        val never = ApiTransport { _, _ -> error("must not be called") }

        assertFalse(ScrittoApi("", never, allowInsecure = false).isConfigured)
        assertFalse(ScrittoApi("http://example.test", never, allowInsecure = false).isConfigured)
        assertTrue(ScrittoApi("http://10.0.2.2:8080", never, allowInsecure = true).isConfigured)
        assertTrue(ScrittoApi("https://example.test", never, allowInsecure = false).isConfigured)
        assertEquals(ApiOutcome.REJECTED, ScrittoApi("", never).postJson("/v1/events", JSONObject()))
    }

    @Test
    fun outcomesFollowTheStatusCode() {
        fun outcome(code: Int) = api(FakeServer { code }).postJson("/x", JSONObject())

        assertEquals(ApiOutcome.OK, outcome(204))
        assertEquals(ApiOutcome.RETRY, outcome(429))
        assertEquals(ApiOutcome.RETRY, outcome(503))
        assertEquals(ApiOutcome.REJECTED, outcome(400))
        assertEquals(
            ApiOutcome.RETRY,
            api(ApiTransport { _, _ -> throw IOException("offline") }).postJson("/x", JSONObject())
        )
    }

    @Test
    fun flushRegistersOnceThenUploadsInBatchesAndClearsTheQueue() {
        val core = core()
        repeat(120) { core.track("e") }
        val server = FakeServer { 200 }

        assertEquals(FlushResult.DONE, TelemetryFlusher.flush(core, api(server), "1.5", 36))

        assertEquals(
            listOf("/v1/devices", "/v1/events", "/v1/events", "/v1/events"),
            server.requests.map { it.first }
        )
        assertEquals(50, server.requests[1].second.getJSONArray("events").length())
        assertEquals(20, server.requests[3].second.getJSONArray("events").length())
        assertEquals("id-1", server.requests[0].second.getString("installation_id"))
        assertEquals(0, core.pendingCount())
        assertTrue(core.isRegistered())

        // Already registered: the next flush only sends events.
        core.track("e")
        server.requests.clear()
        TelemetryFlusher.flush(core, api(server), "1.5", 36)
        assertEquals(listOf("/v1/events"), server.requests.map { it.first })
    }

    @Test
    fun failedUploadKeepsEventsForALaterRetry() {
        val core = core()
        listOf("a", "b").forEach(core::track)

        val result = TelemetryFlusher.flush(core, api(FakeServer { path -> if (path == "/v1/devices") 200 else 503 }), "1.5", 36)

        assertEquals(FlushResult.RETRY, result)
        assertEquals(2, core.pendingCount())
        assertTrue(core.isRegistered())
    }

    @Test
    fun aBatchTheServerRefusesIsDroppedSoItCannotBlockTheQueue() {
        val core = core()
        core.track("a")

        val result = TelemetryFlusher.flush(core, api(FakeServer { path -> if (path == "/v1/devices") 200 else 422 }), "1.5", 36)

        assertEquals(FlushResult.DONE, result)
        assertEquals(0, core.pendingCount())
    }

    @Test
    fun nothingIsSentWhenThereIsNoBackend() {
        val server = FakeServer { 200 }
        val core = core()
        core.track("a")

        assertEquals(
            FlushResult.DONE,
            TelemetryFlusher.flush(core, ScrittoApi("", server, allowInsecure = false), "1.5", 36)
        )
        assertEquals(1, core.pendingCount())
        assertTrue(server.requests.isEmpty())
    }
}
