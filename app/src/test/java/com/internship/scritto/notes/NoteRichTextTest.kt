package com.internship.scritto.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.internship.scritto.data.model.NoteSpan
import com.internship.scritto.notes.NoteRichText.BOLD
import com.internship.scritto.notes.NoteRichText.ITALIC
import com.internship.scritto.notes.NoteRichText.STRIKE
import com.internship.scritto.notes.NoteRichText.UNDERLINE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRichTextTest {

    private fun value(text: String, spans: List<NoteSpan> = emptyList(), sel: TextRange = TextRange(text.length)) =
        NoteRichText.valueFrom(text, spans).copy(selection = sel)

    /** What Compose's text field actually emits after an edit: plain text, no spans. */
    private fun typed(text: String, caret: Int) =
        TextFieldValue(text = text, selection = TextRange(caret))

    private fun bold(start: Int, end: Int) = NoteSpan(start, end, bold = true)

    @Test
    fun spansSurviveTypingAtTheEnd() {
        val old = value("hello world", listOf(bold(0, 5)))
        val result = NoteRichText.reconcile(old, typed("hello world!", 12), null)

        assertEquals("hello world!", result.value.text)
        assertEquals(listOf(bold(0, 5)), NoteRichText.toSpans(result.value.annotatedString))
    }

    @Test
    fun typingInsideBoldStaysBold() {
        val old = value("hello", listOf(bold(0, 5)), TextRange(2))
        val result = NoteRichText.reconcile(old, typed("heXllo", 3), null)

        assertEquals(listOf(bold(0, 6)), NoteRichText.toSpans(result.value.annotatedString))
    }

    @Test
    fun typingBeforeBoldShiftsTheSpan() {
        val old = value("bold", listOf(bold(0, 4)), TextRange(0))
        val result = NoteRichText.reconcile(old, typed(">bold", 1), null)

        assertEquals(listOf(bold(1, 5)), NoteRichText.toSpans(result.value.annotatedString))
    }

    @Test
    fun backspaceShrinksAndShiftsSpans() {
        val old = value("ab cd", listOf(bold(3, 5)), TextRange(2))
        val result = NoteRichText.reconcile(old, typed("a cd", 1), null)

        assertEquals(listOf(bold(2, 4)), NoteRichText.toSpans(result.value.annotatedString))
    }

    @Test
    fun caretDisambiguatesRepeatedCharacters() {
        // "aaa" with the last 'a' bold; typing an 'a' before it must not un-bold it.
        val old = value("aaa", listOf(bold(2, 3)), TextRange(2))
        val result = NoteRichText.reconcile(old, typed("aaaa", 3), null)

        assertEquals(listOf(bold(3, 4)), NoteRichText.toSpans(result.value.annotatedString))
    }

    @Test
    fun replacingABoldWordKeepsItBold() {
        val old = value("say hello now", listOf(bold(4, 9)), TextRange(9))
        // IME autocorrect swaps "hello" for "hi"
        val result = NoteRichText.reconcile(old, typed("say hi now", 6), null)

        assertEquals(listOf(bold(4, 6)), NoteRichText.toSpans(result.value.annotatedString))
    }

    @Test
    fun selectionOnlyChangeKeepsSpansAndDropsPending() {
        val old = value("hello", listOf(bold(0, 5)), TextRange(5))
        val moved = NoteRichText.reconcile(old, typed("hello", 2), BOLD)

        assertEquals(listOf(bold(0, 5)), NoteRichText.toSpans(moved.value.annotatedString))
        assertNull(moved.pending)
    }

    @Test
    fun untoggleThenRetoggleWorksAndPersists() {
        val start = value("0123456789", listOf(bold(0, 10)), TextRange(2, 5))

        assertTrue(NoteRichText.isActive(start, BOLD, null))

        val off = NoteRichText.toggle(start, BOLD, null).value
        assertFalse(NoteRichText.isActive(off, BOLD, null))
        // The persisted form has the hole — no stray "Normal" overlay dropped on save.
        assertEquals(listOf(bold(0, 2), bold(5, 10)), NoteRichText.toSpans(off.annotatedString))

        val on = NoteRichText.toggle(off, BOLD, null).value
        assertTrue(NoteRichText.isActive(on, BOLD, null))
        assertEquals(listOf(bold(0, 10)), NoteRichText.toSpans(on.annotatedString))
    }

    @Test
    fun mixedSelectionTurnsStyleOnFirst() {
        val start = value("abcd", listOf(bold(0, 2)), TextRange(0, 4))

        assertFalse(NoteRichText.isActive(start, BOLD, null))
        val on = NoteRichText.toggle(start, BOLD, null).value
        assertEquals(listOf(bold(0, 4)), NoteRichText.toSpans(on.annotatedString))
    }

    @Test
    fun underlineAndStrikeCombineAndRoundTrip() {
        val span = NoteSpan(0, 4, underline = true, strike = true)
        val v = value("test", listOf(span), TextRange(0, 4))

        assertTrue(NoteRichText.isActive(v, UNDERLINE, null))
        assertTrue(NoteRichText.isActive(v, STRIKE, null))
        assertEquals(listOf(span), NoteRichText.toSpans(v.annotatedString))

        val noUnderline = NoteRichText.toggle(v, UNDERLINE, null).value
        assertEquals(
            listOf(NoteSpan(0, 4, strike = true)),
            NoteRichText.toSpans(noUnderline.annotatedString)
        )
    }

    @Test
    fun allStylesRoundTripThroughStorageFormat() {
        val spans = listOf(
            NoteSpan(0, 3, bold = true, italic = true),
            NoteSpan(5, 8, underline = true),
            NoteSpan(9, 12, strike = true, bold = true)
        )
        val v = value("abc de fgh ijk", spans)
        assertEquals(spans, NoteRichText.toSpans(v.annotatedString))
    }

    @Test
    fun pendingStyleAppliesToNextTypedTextOnly() {
        val old = value("hi ", emptyList(), TextRange(3))

        val armed = NoteRichText.toggle(old, BOLD, null)
        assertEquals(BOLD, armed.pending)
        assertTrue(NoteRichText.isActive(armed.value, BOLD, armed.pending))

        val afterTyping = NoteRichText.reconcile(armed.value, typed("hi yo", 5), armed.pending)
        assertEquals(listOf(bold(3, 5)), NoteRichText.toSpans(afterTyping.value.annotatedString))
        assertNull(afterTyping.pending)
    }

    @Test
    fun clearFormattingRemovesEverythingInTheSelection() {
        val span = NoteSpan(0, 6, bold = true, italic = true, underline = true)
        val v = value("format", listOf(span), TextRange(0, 3))

        val cleared = NoteRichText.clearFormatting(v).value
        assertEquals(
            listOf(NoteSpan(3, 6, bold = true, italic = true, underline = true)),
            NoteRichText.toSpans(cleared.annotatedString)
        )
    }

    @Test
    fun bulletToggleAddsAndRemovesOnEveryTouchedLine() {
        val v = value("one\ntwo\nthree", emptyList(), TextRange(0, 9))

        val added = NoteRichText.toggleBullet(v)
        assertEquals("• one\n• two\n• three", added.text)

        val removed = NoteRichText.toggleBullet(added.copy(selection = TextRange(0, added.text.length)))
        assertEquals("one\ntwo\nthree", removed.text)
    }

    @Test
    fun bulletKeepsCaretAfterTheMarker() {
        val v = value("abc", emptyList(), TextRange(2))
        val added = NoteRichText.toggleBullet(v)

        assertEquals("• abc", added.text)
        assertEquals(TextRange(4), added.selection)
    }

    @Test
    fun enterOnABulletContinuesTheList() {
        val old = value("• item", emptyList(), TextRange(6))
        val result = NoteRichText.reconcile(old, typed("• item\n", 7), null)

        assertEquals("• item\n• ", result.value.text)
        assertEquals(TextRange(9), result.value.selection)
    }

    @Test
    fun enterOnAnEmptyBulletEndsTheList() {
        val old = value("• item\n• ", emptyList(), TextRange(9))
        val result = NoteRichText.reconcile(old, typed("• item\n• \n", 10), null)

        assertEquals("• item\n", result.value.text)
        assertEquals(TextRange(7), result.value.selection)
    }

    @Test
    fun boldSpanStaysAttachedThroughBulletContinuation() {
        val old = value("• bold", listOf(bold(2, 6)), TextRange(6))
        val result = NoteRichText.reconcile(old, typed("• bold\n", 7), null)

        assertEquals("• bold\n• ", result.value.text)
        // The newline inherits bold (so bold carries on to the next line); the bullet marker is plain.
        assertEquals(listOf(bold(2, 7)), NoteRichText.toSpans(result.value.annotatedString))
    }

    @Test
    fun markupProducesSpansAndBullets() {
        val (text, spans) = NoteRichText.parseMarkup("# Title\n- **bold** and *it*\n~~gone~~ __under__")

        assertEquals("Title\n• bold and it\ngone under", text)
        val titleSpan = spans.first()
        assertEquals(NoteSpan(0, 5, bold = true), titleSpan)
        assertTrue(spans.any { it.bold && text.substring(it.start, it.end) == "bold" })
        assertTrue(spans.any { it.italic && text.substring(it.start, it.end) == "it" })
        assertTrue(spans.any { it.strike && text.substring(it.start, it.end) == "gone" })
        assertTrue(spans.any { it.underline && text.substring(it.start, it.end) == "under" })
    }

    @Test
    fun markupLeavesLoneAsterisksAlone() {
        val (text, spans) = NoteRichText.parseMarkup("2 * 3 = 6 and a*b")

        assertEquals("2 * 3 = 6 and a*b", text)
        assertTrue(spans.isEmpty())
    }

    @Test
    fun stripMarkupForVoice() {
        assertEquals("Done. Added Milk", NoteRichText.stripMarkup("**Done.** Added *Milk*"))
    }

    @Test
    fun staleSpansBeyondTextAreClamped() {
        val v = value("abc", listOf(NoteSpan(1, 99, bold = true), NoteSpan(50, 60, italic = true)))
        assertEquals(listOf(bold(1, 3)), NoteRichText.toSpans(v.annotatedString))
    }

    @Test
    fun italicMaskIsIndependentOfBold() {
        val v = value("ab", listOf(NoteSpan(0, 2, italic = true)), TextRange(0, 2))
        assertTrue(NoteRichText.isActive(v, ITALIC, null))
        assertFalse(NoteRichText.isActive(v, BOLD, null))
    }

    @Test
    fun inlineCodeBackticksAreDropped() {
        assertEquals("The date in budget_notes.txt is 12 March.", NoteRichText.stripMarkup("The date in `budget_notes.txt` is **12 March**."))
    }
}
