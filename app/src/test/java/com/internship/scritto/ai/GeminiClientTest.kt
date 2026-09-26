package com.internship.scritto.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class GeminiClientTest {

    private fun okBody(text: String) = """
        {"candidates":[{"content":{"role":"model","parts":[{"text":"$text"}]},"finishReason":"STOP"}]}
    """.trimIndent()

    private val contents = JSONArray().put(
        JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "hi")))
    )

    private class Recorder(val respond: (url: String, key: String) -> HttpResult) : GeminiTransport {
        val calls = mutableListOf<Pair<String, String>>() // model to key
        override fun post(url: String, apiKey: String, body: String): HttpResult {
            calls += url.substringAfterLast('/').substringBefore(':') to apiKey
            return respond(url, apiKey)
        }
    }

    @Test
    fun rotatesKeysAcrossRequests() = runBlocking {
        val transport = Recorder { _, _ -> HttpResult(200, okBody("ok")) }
        val client = GeminiClient(GeminiKeyRing(listOf("A", "B")), listOf("m1"), transport)

        repeat(4) { client.generate(null, contents) }

        assertEquals(listOf("A", "B", "A", "B"), transport.calls.map { it.second })
    }

    @Test
    fun rateLimitedKeyFallsThroughToTheNextKey() = runBlocking {
        val transport = Recorder { _, key ->
            if (key == "A") HttpResult(429, """{"error":{"status":"RESOURCE_EXHAUSTED"}}""") else HttpResult(200, okBody("from B"))
        }
        val client = GeminiClient(GeminiKeyRing(listOf("A", "B")), listOf("m1"), transport)

        val turn = client.generate(null, contents)

        assertEquals("from B", turn.text)
        assertEquals(listOf("A", "B"), transport.calls.map { it.second })

        // A is cooling down: the next request goes straight to B.
        transport.calls.clear()
        client.generate(null, contents)
        assertEquals(listOf("B"), transport.calls.map { it.second })
    }

    @Test
    fun retiredModelIsSkippedForGood() = runBlocking {
        val transport = Recorder { url, _ ->
            if ("old-model" in url) HttpResult(404, """{"error":{"message":"gone"}}""") else HttpResult(200, okBody("new"))
        }
        val client = GeminiClient(GeminiKeyRing(listOf("A", "B")), listOf("old-model", "new-model"), transport)

        assertEquals("new", client.generate(null, contents).text)
        transport.calls.clear()
        client.generate(null, contents)
        assertTrue(transport.calls.all { it.first == "new-model" })
    }

    @Test
    fun rateLimitOnOneModelMovesToTheNextModel() = runBlocking {
        val transport = Recorder { url, _ ->
            if ("big" in url) HttpResult(429, "{}") else HttpResult(200, okBody("lite"))
        }
        val client = GeminiClient(GeminiKeyRing(listOf("A", "B")), listOf("big", "lite"), transport)

        assertEquals("lite", client.generate(null, contents).text)
    }

    @Test
    fun invalidKeyIsBenchedPermanently() = runBlocking {
        val transport = Recorder { _, key ->
            if (key == "BAD") HttpResult(400, """{"error":{"message":"API key not valid","details":[{"reason":"API_KEY_INVALID"}]}}""")
            else HttpResult(200, okBody("ok"))
        }
        val client = GeminiClient(GeminiKeyRing(listOf("BAD", "GOOD")), listOf("m1"), transport)

        repeat(3) { client.generate(null, contents) }

        assertEquals(1, transport.calls.count { it.second == "BAD" })
    }

    @Test
    fun everythingRateLimitedReportsBusy() {
        val transport = Recorder { _, _ -> HttpResult(429, """{"error":{"details":[{"retryDelay":"20s"}]}}""") }
        val client = GeminiClient(GeminiKeyRing(listOf("A", "B")), listOf("m1"), transport)

        try {
            runBlocking { client.generate(null, contents) }
            fail("expected an exception")
        } catch (e: GeminiException) {
            assertEquals(GeminiException.Kind.RATE_LIMITED, e.kind)
        }
    }

    @Test
    fun offlineReportsNetworkError() {
        val transport = Recorder { _, _ -> throw IOException("no route") }
        val client = GeminiClient(GeminiKeyRing(listOf("A")), listOf("m1"), transport)

        try {
            runBlocking { client.generate(null, contents) }
            fail("expected an exception")
        } catch (e: GeminiException) {
            assertEquals(GeminiException.Kind.NETWORK, e.kind)
        }
    }

    @Test
    fun noKeysMeansNotConfigured() {
        val client = GeminiClient(GeminiKeyRing(emptyList()), listOf("m1"), Recorder { _, _ -> HttpResult(200, "") })

        try {
            runBlocking { client.generate(null, contents) }
            fail("expected an exception")
        } catch (e: GeminiException) {
            assertEquals(GeminiException.Kind.NOT_CONFIGURED, e.kind)
        }
    }

    @Test
    fun parsesFunctionCallsAndKeepsThoughtSignatures() = runBlocking {
        val body = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"functionCall":{"name":"create_task","id":"c1","args":{"title":"x"}},"thoughtSignature":"SIG"}
            ]},"finishReason":"STOP"}]}
        """.trimIndent()
        val client = GeminiClient(GeminiKeyRing(listOf("A")), listOf("m1"), Recorder { _, _ -> HttpResult(200, body) })

        val turn = client.generate(null, contents)

        assertEquals("create_task", turn.calls.single().name)
        assertEquals("c1", turn.calls.single().id)
        assertEquals("SIG", turn.content.getJSONArray("parts").getJSONObject(0).getString("thoughtSignature"))
    }
}
