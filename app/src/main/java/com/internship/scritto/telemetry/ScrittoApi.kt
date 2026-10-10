package com.internship.scritto.telemetry

import com.internship.scritto.BuildConfig
import com.internship.scritto.ai.HttpResult
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What an upload attempt means for the caller. */
enum class ApiOutcome {
    /** The server accepted it (2xx). */
    OK,

    /** The server refused it for good (4xx other than 429): retrying the same data won't help. */
    REJECTED,

    /** Offline, timed out, rate limited or a server error: try again later. */
    RETRY
}

fun interface ApiTransport {
    @Throws(IOException::class)
    fun post(url: String, body: String): HttpResult
}

object UrlConnectionApiTransport : ApiTransport {
    override fun post(url: String, body: String): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

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
 * Tiny JSON-over-HTTPS client for the Scritto backend ([BuildConfig.SCRITTO_API_URL]).
 * Plain `http://` is only accepted in debug builds, so release builds never send anything unencrypted.
 */
class ScrittoApi(
    baseUrl: String,
    private val transport: ApiTransport = UrlConnectionApiTransport,
    allowInsecure: Boolean = BuildConfig.DEBUG
) {

    private val base = baseUrl.trim().trimEnd('/')

    val isConfigured: Boolean =
        base.startsWith("https://", ignoreCase = true) ||
            (allowInsecure && base.startsWith("http://", ignoreCase = true))

    /** POSTs [body] to [path] (e.g. "/v1/events"). Never throws. */
    fun postJson(path: String, body: JSONObject): ApiOutcome {
        if (!isConfigured) return ApiOutcome.REJECTED

        val result = try {
            transport.post(base + path, body.toString())
        } catch (e: IOException) {
            return ApiOutcome.RETRY
        } catch (e: RuntimeException) {
            // e.g. a malformed URL: retrying can't fix it.
            return ApiOutcome.REJECTED
        }

        return when (result.code) {
            in 200..299 -> ApiOutcome.OK
            408, 429, in 500..599 -> ApiOutcome.RETRY
            else -> ApiOutcome.REJECTED
        }
    }
}
