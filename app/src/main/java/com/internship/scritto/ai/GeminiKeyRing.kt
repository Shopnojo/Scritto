package com.internship.scritto.ai

/**
 * Rotates a set of Gemini API keys.
 *
 * - Every request starts from the next key in line, so load is spread evenly.
 * - A key that is rate limited is benched for a while *for that model only*
 *   (quotas are per model), and the request falls through to the next key.
 * - A key the API rejects as invalid is benched for good.
 */
class GeminiKeyRing(
    keys: List<String>,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val keys: List<String> = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private val coolingUntil = HashMap<String, Long>()
    private val invalid = HashSet<String>()
    private var cursor = 0

    val size: Int get() = keys.size

    val isEmpty: Boolean get() = keys.isEmpty()

    /** Keys usable for [model], starting at the rotation cursor. Advances the cursor. */
    @Synchronized
    fun candidates(model: String): List<String> {
        if (keys.isEmpty()) return emptyList()

        val start = cursor % keys.size
        cursor = (cursor + 1) % keys.size

        val now = clock()

        return List(keys.size) { keys[(start + it) % keys.size] }
            .filter { it !in invalid && (coolingUntil[slot(it, model)] ?: 0L) <= now }
    }

    @Synchronized
    fun reportRateLimited(key: String, model: String, retryAfterMs: Long) {
        coolingUntil[slot(key, model)] = clock() + retryAfterMs.coerceIn(1_000L, MAX_COOLDOWN_MS)
    }

    @Synchronized
    fun reportInvalid(key: String) {
        invalid += key
    }

    @Synchronized
    fun reportSuccess(key: String, model: String) {
        coolingUntil.remove(slot(key, model))
    }

    /** Milliseconds until some key can serve [model] again, or null if none ever will. */
    @Synchronized
    fun millisUntilAvailable(model: String): Long? {
        val now = clock()

        return keys
            .filter { it !in invalid }
            .minOfOrNull { ((coolingUntil[slot(it, model)] ?: 0L) - now).coerceAtLeast(0L) }
    }

    /** "AQ.Ab8R…" — safe to log or show. */
    fun mask(key: String): String = key.take(8) + "…"

    private fun slot(key: String, model: String) = "$key|$model"

    private companion object {
        const val MAX_COOLDOWN_MS = 60 * 60_000L
    }
}
