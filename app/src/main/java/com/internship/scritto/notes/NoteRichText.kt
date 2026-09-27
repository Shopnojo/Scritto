package com.internship.scritto.notes

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import com.internship.scritto.data.model.NoteSpan

/**
 * Rich-text helpers for the note editor.
 *
 * Formatting is modelled as one bit-mask per character (bold / italic /
 * underline / strike). That gives a single canonical form:
 *
 *  - toggling a style never layers "Normal"/"None" overlay spans, so the
 *    toolbar state, the rendered text and the persisted [NoteSpan]s always agree;
 *  - [reconcile] re-maps styles when the text changes, because Compose's text
 *    input pipeline hands back plain text (no spans) after every edit.
 */
object NoteRichText {

    const val BOLD = 1
    const val ITALIC = 2
    const val UNDERLINE = 4
    const val STRIKE = 8

    private const val BULLET = "• "

    /** A value plus the "pending" style to apply to the next typed characters. */
    data class Result(
        val value: TextFieldValue,
        val pending: Int?
    )

    // ------------------------------------------------------------------
    // AnnotatedString <-> per-character flags <-> NoteSpan
    // ------------------------------------------------------------------

    fun flagsOf(value: AnnotatedString): IntArray {
        val flags = IntArray(value.text.length)

        value.spanStyles.forEach { range ->
            var mask = 0
            if (range.item.fontWeight == FontWeight.Bold) mask = mask or BOLD
            if (range.item.fontStyle == FontStyle.Italic) mask = mask or ITALIC

            val decoration = range.item.textDecoration
            if (decoration != null) {
                if (decoration.contains(TextDecoration.Underline)) mask = mask or UNDERLINE
                if (decoration.contains(TextDecoration.LineThrough)) mask = mask or STRIKE
            }

            if (mask != 0) {
                val start = range.start.coerceIn(0, flags.size)
                val end = range.end.coerceIn(start, flags.size)
                for (i in start until end) flags[i] = flags[i] or mask
            }
        }

        return flags
    }

    fun build(text: String, flags: IntArray): AnnotatedString {
        val builder = AnnotatedString.Builder(text)

        forEachRun(flags) { start, end, mask ->
            builder.addStyle(styleFor(mask), start, end)
        }

        return builder.toAnnotatedString()
    }

    fun fromSpans(text: String, spans: List<NoteSpan>): AnnotatedString {
        val flags = IntArray(text.length)

        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            val mask = maskOf(span)

            for (i in start until end) flags[i] = flags[i] or mask
        }

        return build(text, flags)
    }

    fun toSpans(value: AnnotatedString): List<NoteSpan> {
        val spans = ArrayList<NoteSpan>()

        forEachRun(flagsOf(value)) { start, end, mask ->
            spans += NoteSpan(
                start = start,
                end = end,
                bold = mask and BOLD != 0,
                italic = mask and ITALIC != 0,
                underline = mask and UNDERLINE != 0,
                strike = mask and STRIKE != 0
            )
        }

        return spans
    }

    fun valueFrom(text: String, spans: List<NoteSpan>): TextFieldValue =
        TextFieldValue(
            annotatedString = fromSpans(text, spans),
            selection = TextRange(text.length)
        )

    // ------------------------------------------------------------------
    // Toolbar state + toggles
    // ------------------------------------------------------------------

    fun isActive(value: TextFieldValue, style: Int, pending: Int?): Boolean {
        val selection = value.selection
        val flags = flagsOf(value.annotatedString)

        if (selection.collapsed) {
            val current = pending ?: flagsBefore(flags, selection.start)
            return current and style != 0
        }

        val start = selection.min.coerceIn(0, flags.size)
        val end = selection.max.coerceIn(start, flags.size)
        if (start == end) return false

        for (i in start until end) {
            if (flags[i] and style == 0) return false
        }
        return true
    }

    /**
     * Toggles [style]. With a range selected it edits the range; with just a
     * caret it sets a pending style for the next typed characters.
     */
    fun toggle(value: TextFieldValue, style: Int, pending: Int?): Result {
        val selection = value.selection
        val flags = flagsOf(value.annotatedString)

        if (selection.collapsed) {
            val base = pending ?: flagsBefore(flags, selection.start)
            return Result(value, base xor style)
        }

        val start = selection.min.coerceIn(0, flags.size)
        val end = selection.max.coerceIn(start, flags.size)
        if (start == end) return Result(value, pending)

        val turnOn = (start until end).any { flags[it] and style == 0 }

        for (i in start until end) {
            flags[i] = if (turnOn) flags[i] or style else flags[i] and style.inv()
        }

        return Result(
            value.copy(
                annotatedString = build(value.text, flags),
                selection = selection
            ),
            null
        )
    }

    fun clearFormatting(value: TextFieldValue): Result {
        val selection = value.selection
        if (selection.collapsed) return Result(value, 0)

        val flags = flagsOf(value.annotatedString)
        val start = selection.min.coerceIn(0, flags.size)
        val end = selection.max.coerceIn(start, flags.size)
        for (i in start until end) flags[i] = 0

        return Result(
            value.copy(
                annotatedString = build(value.text, flags),
                selection = selection
            ),
            null
        )
    }

    // ------------------------------------------------------------------
    // Keeping styles alive while the user types
    // ------------------------------------------------------------------

    /**
     * Called with every value the text field emits. When the text changed the
     * emitted value carries no spans, so they are re-mapped from [old].
     */
    fun reconcile(old: TextFieldValue, new: TextFieldValue, pending: Int?): Result {
        val oldText = old.text
        val newText = new.text

        if (oldText == newText) {
            // Selection-only change: keep our spans. A moved caret drops any
            // pending style, as in every mainstream editor.
            val moved = new.selection != old.selection
            return Result(
                TextFieldValue(old.annotatedString, new.selection, new.composition),
                if (moved) null else pending
            )
        }

        val edit = diff(oldText, newText, new.selection)
        val oldFlags = flagsOf(old.annotatedString)
        val newFlags = IntArray(newText.length)

        System.arraycopy(oldFlags, 0, newFlags, 0, edit.start)
        System.arraycopy(
            oldFlags,
            edit.start + edit.removed,
            newFlags,
            edit.start + edit.inserted,
            oldFlags.size - (edit.start + edit.removed)
        )

        val inherited = pending ?: when {
            edit.removed > 0 -> oldFlags[edit.start]
            edit.start > 0 -> oldFlags[edit.start - 1]
            else -> 0
        }
        for (i in edit.start until edit.start + edit.inserted) newFlags[i] = inherited

        val typed = TextFieldValue(
            annotatedString = build(newText, newFlags),
            selection = new.selection,
            composition = new.composition
        )

        return Result(continueBullet(typed, newFlags, edit) ?: typed, null)
    }

    private class Edit(val start: Int, val removed: Int, val inserted: Int)

    private fun diff(oldText: String, newText: String, selection: TextRange): Edit {
        val delta = newText.length - oldText.length

        // Use the caret to disambiguate (e.g. typing "a" inside "aaa").
        if (selection.collapsed) {
            val caret = selection.start

            if (delta > 0) {
                val start = caret - delta
                if (start in 0..oldText.length &&
                    newText.startsWith(oldText.substring(0, start)) &&
                    newText.substring(caret) == oldText.substring(start)
                ) {
                    return Edit(start, 0, delta)
                }
            } else if (delta < 0) {
                val removed = -delta
                if (caret + removed <= oldText.length &&
                    newText.startsWith(oldText.substring(0, caret)) &&
                    newText.substring(caret) == oldText.substring(caret + removed)
                ) {
                    return Edit(caret, removed, 0)
                }
            }
        }

        var prefix = 0
        val maxPrefix = minOf(oldText.length, newText.length)
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++

        var suffix = 0
        val maxSuffix = maxPrefix - prefix
        while (suffix < maxSuffix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++

        return Edit(
            start = prefix,
            removed = oldText.length - prefix - suffix,
            inserted = newText.length - prefix - suffix
        )
    }

    /** Pressing Enter on a bullet line starts a new bullet; on an empty one it ends the list. */
    private fun continueBullet(
        typed: TextFieldValue,
        flags: IntArray,
        edit: Edit
    ): TextFieldValue? {
        val text = typed.text
        val caret = typed.selection.start

        if (edit.removed != 0 || edit.inserted != 1) return null
        if (!typed.selection.collapsed || caret != edit.start + 1) return null
        if (text[edit.start] != '\n') return null

        val lineStart = text.lastIndexOf('\n', edit.start - 1).let { if (edit.start == 0) 0 else it + 1 }
        val line = text.substring(lineStart, edit.start)
        if (!line.startsWith(BULLET)) return null

        return if (line == BULLET) {
            // Empty bullet + Enter: drop the bullet and the newline.
            replace(text, flags, lineStart, edit.start + 1, "", 0, lineStart)
        } else {
            replace(text, flags, edit.start + 1, edit.start + 1, BULLET, 0, edit.start + 1 + BULLET.length)
        }
    }

    private fun replace(
        text: String,
        flags: IntArray,
        start: Int,
        end: Int,
        replacement: String,
        replacementMask: Int,
        caret: Int
    ): TextFieldValue {
        val newText = text.substring(0, start) + replacement + text.substring(end)
        val newFlags = IntArray(newText.length)

        System.arraycopy(flags, 0, newFlags, 0, start)
        for (i in start until start + replacement.length) newFlags[i] = replacementMask
        System.arraycopy(flags, end, newFlags, start + replacement.length, flags.size - end)

        return TextFieldValue(build(newText, newFlags), TextRange(caret))
    }

    // ------------------------------------------------------------------
    // Bullets
    // ------------------------------------------------------------------

    /** Toggles "• " on every line touched by the selection. */
    fun toggleBullet(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selection = value.selection
        val flags = flagsOf(value.annotatedString)

        val from = selection.min.coerceIn(0, text.length)
        val to = selection.max.coerceIn(from, text.length)

        val starts = ArrayList<Int>()
        var lineStart = if (from == 0) 0 else text.lastIndexOf('\n', from - 1) + 1
        while (true) {
            starts += lineStart
            val next = text.indexOf('\n', lineStart)
            if (next == -1 || next + 1 >= to) break
            lineStart = next + 1
        }

        val allBulleted = starts.all { text.startsWith(BULLET, it) }

        val out = StringBuilder()
        val outFlags = ArrayList<Int>()
        val edits = ArrayList<Pair<Int, Int>>() // (position in old text, delta)
        var cursor = 0

        fun copyUntil(end: Int) {
            for (i in cursor until end) {
                out.append(text[i])
                outFlags += flags[i]
            }
            cursor = end
        }

        starts.forEach { lineAt ->
            copyUntil(lineAt)
            if (allBulleted) {
                cursor = lineAt + BULLET.length
                edits += lineAt to -BULLET.length
            } else if (!text.startsWith(BULLET, lineAt)) {
                out.append(BULLET)
                repeat(BULLET.length) { outFlags += 0 }
                edits += lineAt to BULLET.length
            }
        }
        copyUntil(text.length)

        fun mapPosition(position: Int): Int {
            var mapped = position
            edits.forEach { (at, delta) ->
                if (delta > 0 && position >= at) mapped += delta
                if (delta < 0 && position >= at + BULLET.length) mapped += delta
                if (delta < 0 && position > at && position < at + BULLET.length) mapped += at - position
            }
            return mapped.coerceIn(0, out.length)
        }

        return TextFieldValue(
            annotatedString = build(out.toString(), outFlags.toIntArray()),
            selection = TextRange(mapPosition(selection.start), mapPosition(selection.end))
        )
    }

    // ------------------------------------------------------------------
    // Lightweight markup (used for AI-written notes and chat bubbles)
    // ------------------------------------------------------------------

    /**
     * Converts **bold**, *italic*, __underline__, ~~strike~~, "- " / "* " bullets
     * and "# " headings into plain text + [NoteSpan]s.
     */
    fun parseMarkup(source: String, allowUnderline: Boolean = true): Pair<String, List<NoteSpan>> {
        val out = StringBuilder()
        val flags = ArrayList<Int>()

        source.replace("\r\n", "\n").split('\n').forEachIndexed { index, rawLine ->
            if (index > 0) {
                out.append('\n')
                flags += 0
            }

            var line = rawLine
            var lineMask = 0

            val heading = Regex("^#{1,6}\\s+").find(line)
            if (heading != null) {
                line = line.substring(heading.value.length)
                lineMask = BOLD
            }

            val bullet = Regex("^(\\s*)[-*•]\\s+").find(line)
            if (bullet != null) {
                line = bullet.groupValues[1] + BULLET + line.substring(bullet.value.length)
            }

            var mask = 0
            var i = 0
            while (i < line.length) {
                // Inline-code backticks are just noise in a note or chat bubble.
                if (line[i] == '`') {
                    i++
                    continue
                }

                val token = when {
                    line.startsWith("**", i) -> "**" to BOLD
                    allowUnderline && line.startsWith("__", i) -> "__" to UNDERLINE
                    line.startsWith("~~", i) -> "~~" to STRIKE
                    line[i] == '*' -> "*" to ITALIC
                    else -> null
                }

                if (token != null) {
                    val (marker, bit) = token
                    val after = i + marker.length
                    val active = mask and bit != 0
                    val opens = !active &&
                        after < line.length &&
                        !line[after].isWhitespace() &&
                        closerExists(line, marker, after)
                    val closes = active && i > 0 && !line[i - 1].isWhitespace()

                    if (opens || closes) {
                        mask = mask xor bit
                        i = after
                        continue
                    }
                }

                out.append(line[i])
                flags += mask or lineMask
                i++
            }
        }

        val text = out.toString()
        val spans = ArrayList<NoteSpan>()
        forEachRun(flags.toIntArray()) { start, end, mask ->
            spans += NoteSpan(
                start,
                end,
                bold = mask and BOLD != 0,
                italic = mask and ITALIC != 0,
                underline = mask and UNDERLINE != 0,
                strike = mask and STRIKE != 0
            )
        }

        return text to spans
    }

    /** Markup rendered straight to an [AnnotatedString] (chat bubbles). */
    fun renderMarkup(source: String): AnnotatedString {
        val (text, spans) = parseMarkup(source, allowUnderline = false)
        return fromSpans(text, spans)
    }

    /** Plain text with all markup removed (voice, previews). */
    fun stripMarkup(source: String): String = parseMarkup(source, allowUnderline = false).first

    private fun closerExists(line: String, marker: String, from: Int): Boolean {
        var at = line.indexOf(marker, from)
        while (at != -1) {
            if (at > from && !line[at - 1].isWhitespace()) return true
            at = line.indexOf(marker, at + marker.length)
        }
        return false
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun flagsBefore(flags: IntArray, caret: Int): Int =
        if (caret > 0 && caret <= flags.size) flags[caret - 1] else 0

    private fun maskOf(span: NoteSpan): Int =
        (if (span.bold) BOLD else 0) or
            (if (span.italic) ITALIC else 0) or
            (if (span.underline) UNDERLINE else 0) or
            (if (span.strike) STRIKE else 0)

    private fun styleFor(mask: Int): SpanStyle {
        val underline = mask and UNDERLINE != 0
        val strike = mask and STRIKE != 0

        return SpanStyle(
            fontWeight = if (mask and BOLD != 0) FontWeight.Bold else null,
            fontStyle = if (mask and ITALIC != 0) FontStyle.Italic else null,
            textDecoration = when {
                underline && strike -> TextDecoration.combine(
                    listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                )
                underline -> TextDecoration.Underline
                strike -> TextDecoration.LineThrough
                else -> null
            }
        )
    }

    private inline fun forEachRun(flags: IntArray, action: (Int, Int, Int) -> Unit) {
        var i = 0
        while (i < flags.size) {
            val mask = flags[i]
            var j = i + 1
            while (j < flags.size && flags[j] == mask) j++
            if (mask != 0) action(i, j, mask)
            i = j
        }
    }
}
