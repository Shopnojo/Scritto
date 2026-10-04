package com.internship.scritto.ai

import android.content.Context
import java.time.LocalDate

/**
 * Caps how much of the free Gemini allowance one PDF can use in a day.
 *
 * Every character of PDF text sent to the model counts (about 4 characters per token), and every
 * edit request counts once. The limits are deliberately small: one file should cost a few percent
 * of the free daily quota, and a file over budget gets a plain message instead of another model call.
 * Counts reset at local midnight.
 */
object PdfEditBudget {

    /** Text the model may read from one PDF per day (about 6k tokens). */
    const val DAILY_TEXT_CHARS = 24_000

    /** Text returned by a single read (about 2k tokens). */
    const val MAX_TEXT_PER_READ = 8_000

    /** Edit requests per PDF per day. */
    const val DAILY_EDITS = 4

    private const val PREFS = "pdf_edit_budget"

    private class Usage(var chars: Int = 0, var edits: Int = 0)

    /** Characters that may still be read from [fileUri] today, capped at one read's size. */
    fun readAllowance(context: Context, fileUri: String): Int {
        val used = usage(context, fileUri).chars
        return (DAILY_TEXT_CHARS - used).coerceIn(0, MAX_TEXT_PER_READ)
    }

    fun canEdit(context: Context, fileUri: String): Boolean =
        usage(context, fileUri).edits < DAILY_EDITS

    fun spendText(context: Context, fileUri: String, chars: Int) {
        update(context, fileUri) { it.chars += chars }
    }

    fun spendEdit(context: Context, fileUri: String) {
        update(context, fileUri) { it.edits++ }
    }

    private fun usage(context: Context, fileUri: String): Usage {
        val raw = prefs(context).getString(key(fileUri), null) ?: return Usage()
        val parts = raw.split(',')
        return Usage(parts.getOrNull(0)?.toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun update(context: Context, fileUri: String, change: (Usage) -> Unit) {
        val usage = usage(context, fileUri)
        change(usage)
        prefs(context).edit().putString(key(fileUri), "${usage.chars},${usage.edits}").apply()
    }

    /** Keys include the date, so yesterday's counts are simply never read again. */
    private fun key(fileUri: String): String = LocalDate.now().toString() + "|" + fileUri

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
