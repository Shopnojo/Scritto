package com.internship.scritto.documents

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

/** One change to a PDF. Pages are 1-based. Positions are percentages of the page, from the top-left. */
sealed class PdfEditOp {
    abstract val page: Int

    /** Covers every match of [find] on the page with [replace]. The original glyphs stay underneath. */
    data class ReplaceText(override val page: Int, val find: String, val replace: String) : PdfEditOp()

    data class AddText(
        override val page: Int,
        val text: String,
        val xPercent: Int,
        val yPercent: Int,
        val size: Int
    ) : PdfEditOp()

    data class Highlight(override val page: Int, val find: String) : PdfEditOp()
}

object PdfEditOps {

    const val MAX_OPERATIONS = 20
    private const val MAX_TEXT = 500

    /** Reads the operations the model sent. Throws IllegalArgumentException with a message the model can act on. */
    fun parse(array: JSONArray): List<PdfEditOp> {
        require(array.length() > 0) { "No edits were given. Add at least one operation." }
        require(array.length() <= MAX_OPERATIONS) {
            "Too many edits in one request ($MAX_OPERATIONS at most). Split them into smaller requests."
        }

        return (0 until array.length()).map { index ->
            val item = array.optJSONObject(index)
                ?: throw IllegalArgumentException("Operation ${index + 1} is not an object.")
            val page = item.optInt("page", 1)
            require(page >= 1) { "Operation ${index + 1} needs a page number from 1." }

            when (item.optString("op")) {
                "replace_text" -> {
                    val find = text(item, "find", index)
                    val replace = text(item, "replace", index, allowBlank = true)
                    PdfEditOp.ReplaceText(page, find, replace)
                }
                "add_text" -> PdfEditOp.AddText(
                    page = page,
                    text = text(item, "text", index),
                    xPercent = item.optInt("x_percent", 10).coerceIn(0, 100),
                    yPercent = item.optInt("y_percent", 10).coerceIn(0, 100),
                    size = item.optInt("size", 12).coerceIn(6, 40)
                )
                "highlight" -> PdfEditOp.Highlight(page, text(item, "find", index))
                else -> throw IllegalArgumentException(
                    "Operation ${index + 1} has an unknown op. Use replace_text, add_text or highlight."
                )
            }
        }
    }

    private fun text(item: JSONObject, key: String, index: Int, allowBlank: Boolean = false): String {
        val value = item.optString(key)
        if (!allowBlank) {
            require(value.isNotBlank()) { "Operation ${index + 1} needs a non-empty '$key'." }
        }
        require(value.length <= MAX_TEXT) { "Operation ${index + 1} has '$key' longer than $MAX_TEXT characters." }
        return value
    }
}

/** Text of one page, as read from the PDF. */
data class PdfPageText(val page: Int, val text: String)

data class PdfTextRead(val pageCount: Int, val pages: List<PdfPageText>, val truncated: Boolean)

data class PdfEditReport(val applied: Int, val skipped: List<String>)

/**
 * Reads and edits PDFs on the device. Nothing is sent to a server here; only the
 * text the assistant asks for leaves the phone, and it is limited by [PdfEditBudget].
 */
class PdfEditor(context: Context) {

    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    /**
     * Text of pages [fromPage]..[toPage], stopping once [maxChars] would be exceeded.
     * [truncated] is true when pages were left out.
     */
    fun readPages(input: InputStream, fromPage: Int, toPage: Int, maxChars: Int): PdfTextRead =
        PDDocument.load(input).use { document ->
            val count = document.numberOfPages
            val first = fromPage.coerceIn(1, maxOf(count, 1))
            val last = toPage.coerceIn(first, maxOf(count, 1))

            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val pages = mutableListOf<PdfPageText>()
            var used = 0
            var truncated = false

            for (page in first..last) {
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(document).trim()
                if (used + text.length > maxChars) {
                    truncated = true
                    break
                }
                pages += PdfPageText(page, text)
                used += text.length
            }

            PdfTextRead(count, pages, truncated || last < count)
        }

    /** Applies [operations] and writes the edited PDF to [output]. The input is never changed. */
    fun applyEdits(input: InputStream, operations: List<PdfEditOp>, output: OutputStream): PdfEditReport =
        PDDocument.load(input).use { document ->
            val font = PdfFonts.load(document)
            val glyphs = HashMap<Int, List<TextPosition>>()
            val skipped = mutableListOf<String>()
            var applied = 0

            operations.forEachIndexed { index, op ->
                val label = "Edit ${index + 1}"

                if (op.page > document.numberOfPages) {
                    skipped += "$label: the PDF has only ${document.numberOfPages} pages."
                    return@forEachIndexed
                }

                val page = document.getPage(op.page - 1)
                val height = page.mediaBox.height
                val width = page.mediaBox.width

                when (op) {
                    is PdfEditOp.AddText -> {
                        val x = width * op.xPercent / 100f
                        val baseline = height * (1f - op.yPercent / 100f)
                        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                            writeText(stream, font, PdfFonts.sanitize(font, op.text), x, baseline, op.size.toFloat())
                        }
                        applied++
                    }

                    is PdfEditOp.Highlight, is PdfEditOp.ReplaceText -> {
                        val find = if (op is PdfEditOp.Highlight) op.find else (op as PdfEditOp.ReplaceText).find
                        val pageGlyphs = glyphs.getOrPut(op.page) { glyphsOf(document, op.page) }
                        val matches = findMatches(pageGlyphs, find)

                        if (matches.isEmpty()) {
                            skipped += "$label: \"${find.take(40)}\" was not found on page ${op.page}."
                            return@forEachIndexed
                        }

                        matches.forEach { match ->
                            val first = pageGlyphs[match.first]
                            val last = pageGlyphs[match.last]
                            val left = first.xDirAdj
                            val right = last.xDirAdj + last.widthDirAdj
                            val size = first.heightDir.takeIf { it > 0f } ?: 10f
                            val baseline = height - first.yDirAdj

                            when (op) {
                                is PdfEditOp.Highlight -> {
                                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.PREPEND, true, true).use { stream ->
                                        stream.setNonStrokingColor(1f, 0.92f, 0.23f)
                                        stream.addRect(left, baseline - size * 0.25f, right - left, size * 1.25f)
                                        stream.fill()
                                    }
                                }
                                is PdfEditOp.ReplaceText -> {
                                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                                        stream.setNonStrokingColor(1f, 1f, 1f)
                                        stream.addRect(left - 1f, baseline - size * 0.25f, right - left + 2f, size * 1.25f)
                                        stream.fill()

                                        if (op.replace.isNotEmpty()) {
                                            val clean = PdfFonts.sanitize(font, op.replace)
                                            val natural = font.getStringWidth(clean) / 1000f * size
                                            val fitted = if (natural > right - left && natural > 0f) {
                                                (size * (right - left) / natural).coerceAtLeast(4f)
                                            } else {
                                                size
                                            }
                                            writeText(stream, font, clean, left, baseline, fitted)
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        }
                        applied++
                    }
                }
            }

            document.save(output)
            PdfEditReport(applied, skipped)
        }

    private fun writeText(
        stream: PDPageContentStream,
        font: PDFont,
        text: String,
        x: Float,
        baseline: Float,
        size: Float
    ) {
        stream.beginText()
        stream.setNonStrokingColor(0f, 0f, 0f)
        stream.setFont(font, size)
        stream.newLineAtOffset(x, baseline)
        stream.showText(text)
        stream.endText()
    }

    /** Glyphs of one page in reading order, collected while the text is extracted. */
    private fun glyphsOf(document: PDDocument, page: Int): List<TextPosition> {
        val collector = GlyphCollector().apply {
            sortByPosition = true
            startPage = page
            endPage = page
        }
        collector.getText(document)
        return collector.glyphs
    }

    /** Index ranges of glyphs that spell [needle], ignoring spaces and case. */
    internal fun findMatches(glyphs: List<TextPosition>, needle: String): List<IntRange> {
        val target = needle.filterNot { it.isWhitespace() }
        if (target.isEmpty()) return emptyList()

        val text = StringBuilder()
        val owner = ArrayList<Int>()
        glyphs.forEachIndexed { index, glyph ->
            glyph.unicode?.forEach { char ->
                if (!char.isWhitespace()) {
                    text.append(char)
                    owner += index
                }
            }
        }

        val matches = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val at = text.indexOf(target, from, ignoreCase = true)
            if (at < 0) break
            val end = at + target.length - 1
            matches += owner[at]..owner[end]
            from = end + 1
        }
        return matches
    }

    private class GlyphCollector : PDFTextStripper() {
        val glyphs = ArrayList<TextPosition>()

        override fun processTextPosition(text: TextPosition) {
            glyphs += text
        }
    }
}

/** A font that can show the text. Built-in fonts only cover Latin; a system Unicode font is used when present. */
object PdfFonts {

    private val systemFonts = listOf(
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf"
    )

    fun load(document: PDDocument): PDFont {
        for (path in systemFonts) {
            val file = File(path)
            if (!file.exists()) continue
            val font = runCatching { FileInputStream(file).use { PDType0Font.load(document, it) } }.getOrNull()
            if (font != null) return font
        }
        return PDType1Font.HELVETICA
    }

    /** Built-in fonts cannot draw characters outside Latin; those become "?" instead of failing. */
    fun sanitize(font: PDFont, text: String): String {
        if (font !is PDType1Font) return text
        return buildString(text.length) {
            text.forEach { char ->
                append(if (char.code in 32..126 || char.code in 160..255) char else '?')
            }
        }
    }
}
