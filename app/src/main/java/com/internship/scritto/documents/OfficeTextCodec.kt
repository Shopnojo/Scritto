package com.internship.scritto.documents

import com.internship.scritto.data.model.NoteSpan
import com.internship.scritto.notes.NoteRichText
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.InputStream
import java.io.OutputStream

/** One top-level paragraph of a Word document, with what the page needs to draw it. */
data class DocxParagraph(
    /** Position among the document's top-level paragraphs; used to write edits back. */
    val index: Int,
    val text: String,
    val style: String,
    /** "•" for list items, otherwise empty. */
    val bullet: String = "",
    /** "left", "center", "right" or "both" (justified). */
    val align: String = "left",
    /** True when the paragraph holds a picture, which the page shows as a marker. */
    val hasImage: Boolean = false,
    /** Bold, italic, underline and strike ranges, in the same form the note editor uses. */
    val spans: List<NoteSpan> = emptyList()
) {
    val isHeading: Boolean get() = style.contains("Heading", true) || style.contains("Title", true)
}

data class DocxTable(val index: Int, val rows: List<List<String>>)

/** A block in reading order: a paragraph or a table. */
sealed interface DocxBlock {
    data class Paragraph(val paragraph: DocxParagraph) : DocxBlock
    data class Table(val table: DocxTable) : DocxBlock
}

/** A whole Word document as a page: header, body in order, footer. */
data class DocxPage(
    val header: String,
    val footer: String,
    val blocks: List<DocxBlock>,
    val paragraphs: List<DocxParagraph>,
    val tables: List<DocxTable>
)

/** One worksheet: its name and every row, cell by cell as text. */
data class SheetData(val name: String, val rows: List<List<String>>)

/**
 * Converts between Office files and plain text / rows / pages. Kept free of Android types so the
 * round trips can be unit-tested on the JVM.
 *
 * Read and write use the same model, so opening a file and saving it again changes only
 * what the user edited.
 */
object OfficeTextCodec {

    private val plainNumber = Regex("^-?(0|[1-9]\\d*)(\\.\\d+)?$")

    // ------------------------------------------------------------------ DOCX

    /** The text of every top-level paragraph, one line each. */
    fun docxToText(input: InputStream): String =
        XWPFDocument(input).use { doc ->
            doc.paragraphs.joinToString("\n") { it.text }
        }

    /** The whole document in reading order, with headers, footers, lists and tables. */
    fun docxPage(input: InputStream): DocxPage =
        XWPFDocument(input).use { doc ->
            val paragraphs = mutableListOf<DocxParagraph>()
            val tables = mutableListOf<DocxTable>()
            val blocks = mutableListOf<DocxBlock>()

            doc.bodyElements.forEach { element ->
                when (element) {
                    is XWPFParagraph -> {
                        val paragraph = paragraphOf(element, paragraphs.size)
                        paragraphs += paragraph
                        blocks += DocxBlock.Paragraph(paragraph)
                    }
                    is XWPFTable -> {
                        val table = DocxTable(
                            index = tables.size,
                            rows = element.rows.map { row -> row.tableCells.map { it.text } }
                        )
                        tables += table
                        blocks += DocxBlock.Table(table)
                    }
                    else -> Unit
                }
            }

            DocxPage(
                header = doc.headerList.joinToString("\n") { it.text }.trim(),
                footer = doc.footerList.joinToString("\n") { it.text }.trim(),
                blocks = blocks,
                paragraphs = paragraphs,
                tables = tables
            )
        }

    private fun paragraphOf(paragraph: XWPFParagraph, index: Int): DocxParagraph =
        DocxParagraph(
            index = index,
            text = paragraph.text,
            style = paragraph.style.orEmpty(),
            bullet = if (paragraph.numID != null) "•" else "",
            align = alignOf(paragraph),
            hasImage = paragraph.runs.any { it.embeddedPictures.isNotEmpty() },
            spans = spansOf(paragraph)
        )

    private fun alignOf(paragraph: XWPFParagraph): String =
        when (paragraph.alignment?.toString()) {
            "CENTER" -> "center"
            "RIGHT", "END" -> "right"
            "BOTH", "DISTRIBUTE" -> "both"
            else -> "left"
        }

    private fun alignmentFor(align: String): ParagraphAlignment = when (align) {
        "center" -> ParagraphAlignment.CENTER
        "right" -> ParagraphAlignment.RIGHT
        "both" -> ParagraphAlignment.BOTH
        else -> ParagraphAlignment.LEFT
    }

    /** Bold / italic / underline / strike of each run, as the same spans the note editor uses. */
    fun spansOf(paragraph: XWPFParagraph): List<NoteSpan> {
        val runs = paragraph.runs
        val text = paragraph.text
        if (runs.sumOf { it.text().length } != text.length) return emptyList()

        var offset = 0
        val raw = ArrayList<NoteSpan>()
        runs.forEach { run ->
            val length = run.text().length
            if (length > 0 && (run.isBold || run.isItalic || run.underline != UnderlinePatterns.NONE || run.isStrikeThrough)) {
                raw += NoteSpan(
                    start = offset,
                    end = offset + length,
                    bold = run.isBold,
                    italic = run.isItalic,
                    underline = run.underline != UnderlinePatterns.NONE,
                    strike = run.isStrikeThrough
                )
            }
            offset += length
        }
        return canonical(text, raw)
    }

    /** The same form the note editor produces, so an unchanged paragraph compares equal. */
    private fun canonical(text: String, spans: List<NoteSpan>): List<NoteSpan> =
        NoteRichText.toSpans(NoteRichText.fromSpans(text, spans))

    /**
     * Writes one edited paragraph. Text-only edits replace the text in place. A change of
     * formatting rebuilds the runs, keeping each character's font and size. Paragraphs holding
     * pictures only get text edits, so the pictures stay.
     */
    private fun applyParagraph(paragraph: XWPFParagraph, edit: DocxParagraph) {
        if (edit.align != alignOf(paragraph)) paragraph.alignment = alignmentFor(edit.align)

        val currentSpans = spansOf(paragraph)
        val formatChanged = edit.spans != currentSpans
        val textChanged = edit.text != paragraph.text
        if (!formatChanged && !textChanged) return

        val hasPictures = paragraph.runs.any { it.embeddedPictures.isNotEmpty() }
        if (hasPictures || paragraph.runs.isEmpty()) {
            setParagraphText(paragraph, edit.text)
        } else {
            rebuildRuns(paragraph, edit.text, edit.spans)
        }
    }

    private fun rebuildRuns(paragraph: XWPFParagraph, text: String, spans: List<NoteSpan>) {
        // Remember the font of each original run, by character position.
        class Original(val start: Int, val end: Int, val family: String?, val size: Int, val color: String?)

        var offset = 0
        val originals = paragraph.runs.map { run ->
            val length = run.text().length
            Original(offset, offset + length, run.fontFamily, run.fontSize, run.color).also { offset += length }
        }
        val fallback = originals.firstOrNull()

        val flags = IntArray(text.length)
        spans.forEach { span ->
            val mask = (if (span.bold) NoteRichText.BOLD else 0) or
                (if (span.italic) NoteRichText.ITALIC else 0) or
                (if (span.underline) NoteRichText.UNDERLINE else 0) or
                (if (span.strike) NoteRichText.STRIKE else 0)
            for (i in span.start.coerceIn(0, text.length) until span.end.coerceIn(0, text.length)) {
                flags[i] = flags[i] or mask
            }
        }

        for (index in paragraph.runs.lastIndex downTo 0) paragraph.removeRun(index)

        var start = 0
        while (start < text.length) {
            val mask = flags[start]
            var end = start
            while (end < text.length && flags[end] == mask) end++

            val source = originals.firstOrNull { start >= it.start && start < it.end } ?: fallback
            paragraph.createRun().apply {
                setText(text.substring(start, end))
                setBold(mask and NoteRichText.BOLD != 0)
                setItalic(mask and NoteRichText.ITALIC != 0)
                setUnderline(if (mask and NoteRichText.UNDERLINE != 0) UnderlinePatterns.SINGLE else UnderlinePatterns.NONE)
                setStrikeThrough(mask and NoteRichText.STRIKE != 0)
                source?.family?.let { fontFamily = it }
                source?.size?.takeIf { it > 0 }?.let { fontSize = it }
                source?.color?.let { color = it }
            }
            start = end
        }

        if (text.isEmpty()) paragraph.createRun().setText("")
    }

    /**
     * Writes edited paragraphs and table cells back into [source] (or a new document when
     * [source] is null). Each paragraph keeps the formatting of its first run.
     */
    fun writeDocxPage(
        source: InputStream?,
        paragraphs: List<DocxParagraph>,
        tables: List<DocxTable>,
        output: OutputStream
    ) {
        val doc = source?.let { XWPFDocument(it) } ?: XWPFDocument()
        doc.use { document ->
            // Only changed paragraphs are rewritten, so untouched ones keep their pictures and runs.
            paragraphs.forEach { block ->
                document.paragraphs.getOrNull(block.index)?.let { paragraph ->
                    applyParagraph(paragraph, block)
                }
            }

            tables.forEach { table ->
                document.tables.getOrNull(table.index)?.let { xwpfTable ->
                    table.rows.forEachIndexed { r, cells ->
                        xwpfTable.rows.getOrNull(r)?.let { row ->
                            cells.forEachIndexed { c, value ->
                                row.getCell(c)?.let { cell ->
                                    val paragraph = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
                                    if (paragraph.text != value) setParagraphText(paragraph, value)
                                }
                            }
                        }
                    }
                }
            }

            document.write(output)
        }
    }

    /**
     * Writes [text] back into the paragraphs of [source] (or a new document when [source] is
     * null). Each line becomes one paragraph and keeps the formatting of its first run.
     */
    fun textToDocx(source: InputStream?, text: String, output: OutputStream) {
        val doc = source?.let { XWPFDocument(it) } ?: XWPFDocument()
        doc.use { document ->
            val lines = text.replace("\r\n", "\n").split("\n")

            while (document.paragraphs.size < lines.size) document.createParagraph()
            for (index in document.paragraphs.lastIndex downTo lines.size) {
                // Remove by body position, so tables and other body content stay where they are.
                val bodyIndex = document.bodyElements.indexOf(document.paragraphs[index])
                if (bodyIndex >= 0) document.removeBodyElement(bodyIndex)
            }

            document.paragraphs.zip(lines).forEach { (paragraph, line) ->
                setParagraphText(paragraph, line)
            }
            document.write(output)
        }
    }

    private fun setParagraphText(paragraph: XWPFParagraph, line: String) {
        if (paragraph.runs.isEmpty()) {
            paragraph.createRun().setText(line)
            return
        }
        replaceRunText(paragraph.runs.first(), line)
        for (index in paragraph.runs.lastIndex downTo 1) {
            paragraph.removeRun(index)
        }
    }

    /**
     * Replaces the text of [run] and keeps its formatting. POI's plain setText(value) appends
     * a new text element, which would duplicate the text on every save.
     */
    fun replaceRunText(run: XWPFRun, text: String) {
        runCatching { run.setText(text, 0) }.getOrElse { run.setText(text) }
    }

    // ------------------------------------------------------------------ XLSX

    /** Every worksheet in the workbook, in tab order. */
    fun xlsxSheets(input: InputStream): List<SheetData> =
        XSSFWorkbook(input).use { workbook ->
            val evaluator = workbook.creationHelper.createFormulaEvaluator()
            val dates = DataFormatter()

            (0 until workbook.numberOfSheets).map { sheetIndex ->
                val sheet = workbook.getSheetAt(sheetIndex)
                val rows = (0..sheet.lastRowNum).map { rowIndex ->
                    val row = sheet.getRow(rowIndex)
                    if (row == null || row.lastCellNum < 1) {
                        emptyList()
                    } else {
                        (0 until row.lastCellNum).map { columnIndex ->
                            cellText(row.getCell(columnIndex), evaluator, dates)
                        }
                    }
                }
                SheetData(sheet.sheetName, rows)
            }
        }

    /** Rows of the first sheet. */
    fun xlsxToRows(input: InputStream): List<List<String>> =
        xlsxSheets(input).firstOrNull()?.rows.orEmpty()

    /**
     * Writes [sheets] into [source] (or a new workbook). Sheets are matched by position; a
     * sheet beyond the source's count is created.
     */
    fun writeXlsxSheets(source: InputStream?, sheets: List<SheetData>, output: OutputStream) {
        val workbook = source?.let { XSSFWorkbook(it) } ?: XSSFWorkbook()
        workbook.use { book ->
            sheets.forEachIndexed { sheetIndex, data ->
                val sheet = if (sheetIndex < book.numberOfSheets) {
                    book.getSheetAt(sheetIndex)
                } else {
                    book.createSheet(data.name.ifBlank { "Sheet${sheetIndex + 1}" })
                }

                for (rowIndex in sheet.lastRowNum downTo 0) {
                    sheet.getRow(rowIndex)?.let { sheet.removeRow(it) }
                }

                data.rows.forEachIndexed { rowIndex, cells ->
                    val row = sheet.createRow(rowIndex)
                    cells.forEachIndexed { columnIndex, value ->
                        if (value.isEmpty()) return@forEachIndexed
                        val cell = row.createCell(columnIndex)
                        if (plainNumber.matches(value)) {
                            cell.setCellValue(value.toDouble())
                        } else {
                            cell.setCellValue(value)
                        }
                    }
                }
            }
            book.write(output)
        }
    }

    /** Replaces the contents of the first sheet of [source] (or a new workbook) with [rows]. */
    fun rowsToXlsx(source: InputStream?, rows: List<List<String>>, output: OutputStream) =
        writeXlsxSheets(source, listOf(SheetData("Sheet1", rows)), output)

    private fun cellText(cell: Cell?, evaluator: FormulaEvaluator, dates: DataFormatter): String {
        if (cell == null) return ""

        val value = if (cell.cellType == CellType.FORMULA) evaluator.evaluate(cell) else null
        val type = value?.cellType ?: cell.cellType

        return when (type) {
            CellType.STRING -> value?.stringValue ?: cell.stringCellValue
            CellType.BOOLEAN -> (value?.booleanValue ?: cell.booleanCellValue).toString()
            CellType.NUMERIC -> {
                if (value == null && DateUtil.isCellDateFormatted(cell)) {
                    dates.formatCellValue(cell)
                } else {
                    formatNumber(value?.numberValue ?: cell.numericCellValue)
                }
            }
            else -> ""
        }
    }

    /** 42.0 -> "42", 0.5 -> "0.5". Avoids the trailing ".0" that toString() adds. */
    private fun formatNumber(number: Double): String =
        if (number == Math.floor(number) && !number.isInfinite() && Math.abs(number) < 1e15) {
            number.toLong().toString()
        } else {
            number.toString()
        }
}
