package com.internship.scritto.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class GeminiException(
    message: String,
    val kind: Kind,
    val detail: String? = null
) : Exception(message) {
    enum class Kind { NOT_CONFIGURED, RATE_LIMITED, NETWORK, BAD_REQUEST, BLOCKED, SERVER }
}

data class GeminiFunctionCall(
    val name: String,
    val id: String?,
    val args: JSONObject
)

/** One model response. [content] is the raw model turn, safe to append to the history as-is. */
data class GeminiTurn(
    val content: JSONObject,
    val text: String,
    val calls: List<GeminiFunctionCall>,
    val finishReason: String?
)

data class HttpResult(val code: Int, val body: String)

fun interface GeminiTransport {
    @Throws(IOException::class)
    fun post(url: String, apiKey: String, body: String): HttpResult
}

object UrlConnectionTransport : GeminiTransport {
    override fun post(url: String, apiKey: String, body: String): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey)

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            return HttpResult(code, text)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Minimal Gemini `generateContent` client with automatic failover:
 * keys are rotated per request and, when a key is rate limited or a model is
 * unavailable, the request moves on to the next key / the next model.
 */
class GeminiClient(
    private val keyRing: GeminiKeyRing,
    private val models: List<String> = GeminiConfig.MODELS,
    private val transport: GeminiTransport = UrlConnectionTransport
) {

    private val unavailableModels: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    val isConfigured: Boolean get() = !keyRing.isEmpty

    suspend fun generate(
        systemInstruction: String?,
        contents: JSONArray,
        tools: JSONArray? = null
    ): GeminiTurn {
        val body = JSONObject().apply {
            if (systemInstruction != null) {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                )
            }
            put("contents", contents)
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put(
                    "toolConfig",
                    JSONObject().put("functionCallingConfig", JSONObject().put("mode", "AUTO"))
                )
            }
            put("generationConfig", JSONObject().put("temperature", 0.4))
        }.toString()

        return withContext(Dispatchers.IO) { generateWithFailover(body) }
    }

    /** Sends a file to Gemini and returns the text it produces (OCR, transcript, summary...). */
    suspend fun readFile(bytes: ByteArray, mimeType: String, instruction: String): String {
        val part = JSONObject().put(
            "inlineData",
            JSONObject()
                .put("mimeType", mimeType)
                .put("data", Base64.getEncoder().encodeToString(bytes))
        )
        val contents = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(part).put(JSONObject().put("text", instruction)))
        )

        return generate(null, contents).text
    }

    private fun generateWithFailover(body: String): GeminiTurn {
        if (keyRing.isEmpty) {
            throw GeminiException(
                "Scritto AI has no API key configured.",
                GeminiException.Kind.NOT_CONFIGURED
            )
        }

        var rateLimited = false
        var network = false
        var server = false
        var lastBadRequest: String? = null
        var lastServerDetail: String? = null

        for (model in models) {
            if (model in unavailableModels) continue

            val url = "$ENDPOINT/$model:generateContent"

            for (key in keyRing.candidates(model)) {
                val result = try {
                    transport.post(url, key, body)
                } catch (e: IOException) {
                    network = true
                    lastServerDetail = e.message
                    continue
                }

                when {
                    result.code in 200..299 -> {
                        val turn = parse(result.body)
                        keyRing.reportSuccess(key, model)
                        return turn
                    }

                    result.code == 429 -> {
                        rateLimited = true
                        keyRing.reportRateLimited(key, model, retryDelayMs(result.body))
                    }

                    result.code == 404 -> {
                        // Model retired or not offered to this project: skip it entirely.
                        unavailableModels += model
                        lastServerDetail = errorMessage(result.body)
                        break
                    }

                    result.code == 401 || result.code == 403 || isInvalidKey(result) -> {
                        lastServerDetail = errorMessage(result.body)
                        if (isInvalidKey(result)) {
                            keyRing.reportInvalid(key)
                        } else {
                            keyRing.reportRateLimited(key, model, 10 * 60_000L)
                        }
                    }

                    result.code == 400 -> {
                        // The request itself is the problem; another model may still accept it.
                        lastBadRequest = errorMessage(result.body)
                        break
                    }

                    else -> {
                        server = true
                        lastServerDetail = errorMessage(result.body)
                    }
                }
            }
        }

        throw when {
            lastBadRequest != null -> GeminiException(
                "Scritto AI couldn't process that request.",
                GeminiException.Kind.BAD_REQUEST,
                lastBadRequest
            )

            rateLimited -> {
                val wait = models
                    .filter { it !in unavailableModels }
                    .mapNotNull { keyRing.millisUntilAvailable(it) }
                    .minOrNull()
                    ?.let { (it / 1000L).coerceAtLeast(1L) }

                GeminiException(
                    if (wait != null) {
                        "Scritto AI is busy right now. Try again in about $wait seconds."
                    } else {
                        "Scritto AI is busy right now. Try again in a moment."
                    },
                    GeminiException.Kind.RATE_LIMITED,
                    lastServerDetail
                )
            }

            network && !server -> GeminiException(
                "I can't reach Scritto AI. Check your internet connection and try again.",
                GeminiException.Kind.NETWORK,
                lastServerDetail
            )

            else -> GeminiException(
                "Scritto AI is unavailable right now. Please try again shortly.",
                GeminiException.Kind.SERVER,
                lastServerDetail
            )
        }
    }

    private fun parse(body: String): GeminiTurn {
        val root = JSONObject(body)
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)

        if (candidate == null) {
            val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()

            throw GeminiException(
                "That request was blocked by Gemini's safety filters.",
                GeminiException.Kind.BLOCKED,
                reason
            )
        }

        val content = candidate.optJSONObject("content")
            ?: JSONObject().put("role", "model").put("parts", JSONArray())
        content.put("role", "model")

        val parts = content.optJSONArray("parts") ?: JSONArray().also { content.put("parts", it) }
        val text = StringBuilder()
        val calls = ArrayList<GeminiFunctionCall>()

        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            val call = part.optJSONObject("functionCall")

            if (call != null) {
                calls += GeminiFunctionCall(
                    name = call.optString("name"),
                    id = call.optString("id").takeIf { it.isNotEmpty() },
                    args = call.optJSONObject("args") ?: JSONObject()
                )
            } else if (part.has("text") && !part.optBoolean("thought", false)) {
                text.append(part.optString("text"))
            }
        }

        return GeminiTurn(
            content = content,
            text = text.toString().trim(),
            calls = calls,
            finishReason = candidate.optString("finishReason").takeIf { it.isNotEmpty() }
        )
    }

    private fun isInvalidKey(result: HttpResult): Boolean =
        result.code == 400 &&
            (result.body.contains("API_KEY_INVALID") || result.body.contains("API key not valid"))

    private fun errorMessage(body: String): String =
        runCatching { JSONObject(body).getJSONObject("error").optString("message") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: body.take(200)

    private fun retryDelayMs(body: String): Long {
        // A daily cap reports a short retryDelay too, but retrying in a minute is pointless.
        if (body.contains("PerDay")) return DAILY_QUOTA_COOLDOWN_MS

        val seconds = Regex("\"retryDelay\"\\s*:\\s*\"(\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
        return (seconds ?: 30L) * 1000L
    }

    private companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DAILY_QUOTA_COOLDOWN_MS = 30 * 60_000L
    }
}
