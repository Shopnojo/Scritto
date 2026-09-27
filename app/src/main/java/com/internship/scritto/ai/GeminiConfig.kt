package com.internship.scritto.ai

import com.internship.scritto.BuildConfig

object GeminiConfig {

    /**
     * Tried in order; the next one is used when a model is unavailable or every
     * key is rate limited for it.
     */
    val MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-3.6-flash",
        "gemini-flash-latest",
        "gemini-3.8-flash"
    )

    /** Keys come from local.properties (gemini.api.keys=key1,key2) via BuildConfig. */
    val keys: List<String> =
        BuildConfig.GEMINI_API_KEYS.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** One process-wide client so rotation state is shared by chat and voice. */
    val client: GeminiClient by lazy { GeminiClient(GeminiKeyRing(keys)) }
}
