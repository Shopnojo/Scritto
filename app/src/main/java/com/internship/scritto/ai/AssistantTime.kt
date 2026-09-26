package com.internship.scritto.ai

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Date handling shared by the assistant tools and prompt. All local-time, all explicit. */
internal object AssistantTime {

    private const val DEFAULT_HOUR = 9

    /**
     * Accepts "2026-09-27T18:00", "2026-09-27 18:00", full ISO with offset, or
     * a bare date ("2026-09-27", which becomes 09:00).
     */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val value = text.trim().replace(' ', 'T')
        if (value.isEmpty()) return null

        runCatching {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }
        runCatching {
            return LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
        }
        runCatching {
            return LocalDate.parse(value).atTime(DEFAULT_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        }

        return null
    }

    fun startOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** "2026-09-27T18:00" — the format the model is asked to use. */
    fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(Date(millis))

    /** "Sun 27 Sep, 6:00 PM" */
    fun human(millis: Long): String =
        SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(millis))

    fun humanTime(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

    fun humanDay(millis: Long): String =
        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(millis))

    /** "Saturday, 2026-09-26 21:14 (Asia/Calcutta, UTC+05:30)" */
    fun nowDescription(now: Long = System.currentTimeMillis()): String {
        val zone = TimeZone.getDefault()
        val offset = SimpleDateFormat("XXX", Locale.US).apply { timeZone = zone }.format(Date(now))

        return SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }
            .format(Date(now)) + " (${zone.id}, UTC$offset)"
    }
}
