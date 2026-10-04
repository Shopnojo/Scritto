package com.internship.scritto.documents

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class OfficeLayoutTest {

    private fun sampleDocx(): ByteArray {
        val doc = XWPFDocument()
        doc.createParagraph().createRun().setText("Title line")
        val table = doc.createTable(2, 2)
        table.getRow(0).getCell(0).setText("Name")
        table.getRow(0).getCell(1).setText("Qty")
        table.getRow(1).getCell(0).setText("Pen")
        table.getRow(1).getCell(1).setText("3")
        doc.createParagraph().createRun().setText("After table")
        return ByteArrayOutputStream().also { doc.write(it) }.toByteArray()
    }

    @Test
    fun docxPageKeepsReadingOrderWithTablesBetweenParagraphs() {
        val page = OfficeTextCodec.docxPage(ByteArrayInputStream(sampleDocx()))

        assertEquals(3, page.blocks.size)
        assertTrue(page.blocks[0] is DocxBlock.Paragraph)
        assertTrue(page.blocks[1] is DocxBlock.Table)
        assertTrue(page.blocks[2] is DocxBlock.Paragraph)
        assertEquals(listOf(listOf("Name", "Qty"), listOf("Pen", "3")), page.tables.single().rows)
        assertEquals(listOf("Title line", "After table"), page.paragraphs.map { it.text })
    }

    @Test
    fun editingOneParagraphLeavesTheRestOfTheDocumentAlone() {
        val page = OfficeTextCodec.docxPage(ByteArrayInputStream(sampleDocx()))
        val edited = page.paragraphs.map { if (it.index == 1) it.copy(text = "Changed") else it }

        val out = ByteArrayOutputStream()
        OfficeTextCodec.writeDocxPage(ByteArrayInputStream(sampleDocx()), edited, page.tables, out)

        val again = OfficeTextCodec.docxPage(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf("Title line", "Changed"), again.paragraphs.map { it.text })
        assertEquals(listOf(listOf("Name", "Qty"), listOf("Pen", "3")), again.tables.single().rows)
    }

    @Test
    fun editingATableCellWritesOnlyThatCell() {
        val page = OfficeTextCodec.docxPage(ByteArrayInputStream(sampleDocx()))
        val tables = listOf(page.tables.single().copy(rows = listOf(listOf("Name", "Qty"), listOf("Pen", "5"))))

        val out = ByteArrayOutputStream()
        OfficeTextCodec.writeDocxPage(ByteArrayInputStream(sampleDocx()), page.paragraphs, tables, out)

        val again = OfficeTextCodec.docxPage(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf(listOf("Name", "Qty"), listOf("Pen", "5")), again.tables.single().rows)
        assertEquals(listOf("Title line", "After table"), again.paragraphs.map { it.text })
    }

    @Test
    fun workbookSheetsAreReadAndWrittenInTabOrder() {
        val book = XSSFWorkbook()
        book.createSheet("Sales").createRow(0).createCell(0).setCellValue("Total")
        book.getSheet("Sales").createRow(1).createCell(0).setCellValue(42.0)
        book.createSheet("Notes").createRow(0).createCell(0).setCellValue("hello")
        val source = ByteArrayOutputStream().also { book.write(it) }.toByteArray()

        val sheets = OfficeTextCodec.xlsxSheets(ByteArrayInputStream(source))
        assertEquals(listOf("Sales", "Notes"), sheets.map { it.name })
        assertEquals(listOf(listOf("Total"), listOf("42")), sheets[0].rows)

        val edited = listOf(
            sheets[0].copy(rows = listOf(listOf("Total"), listOf("43"))),
            sheets[1]
        )
        val out = ByteArrayOutputStream()
        OfficeTextCodec.writeXlsxSheets(ByteArrayInputStream(source), edited, out)

        val again = OfficeTextCodec.xlsxSheets(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf(listOf("Total"), listOf("43")), again[0].rows)
        assertEquals(listOf(listOf("hello")), again[1].rows)
    }

    @Test
    fun boldAndItalicRoundTripThroughEditsAndLeaveOtherParagraphsAlone() {
        val doc = XWPFDocument()
        val first = doc.createParagraph()
        first.createRun().apply { setText("Plain "); setBold(true) }
        first.createRun().apply { setText("tail"); setItalic(true) }
        doc.createParagraph().createRun().setText("Untouched")
        val source = ByteArrayOutputStream().also { doc.write(it) }.toByteArray()

        val page = OfficeTextCodec.docxPage(ByteArrayInputStream(source))
        val firstSpans = page.paragraphs[0].spans
        assertEquals(
            listOf(
                com.internship.scritto.data.model.NoteSpan(0, 6, bold = true),
                com.internship.scritto.data.model.NoteSpan(6, 10, italic = true)
            ),
            firstSpans
        )

        // Underline the word "tail" and leave the rest as it is.
        val edited = page.paragraphs.map {
            if (it.index == 0) it.copy(spans = listOf(
                com.internship.scritto.data.model.NoteSpan(0, 6, bold = true),
                com.internship.scritto.data.model.NoteSpan(6, 10, italic = true, underline = true)
            )) else it
        }
        val out = ByteArrayOutputStream()
        OfficeTextCodec.writeDocxPage(ByteArrayInputStream(source), edited, page.tables, out)

        val again = OfficeTextCodec.docxPage(ByteArrayInputStream(out.toByteArray()))
        assertEquals("Plain tail", again.paragraphs[0].text)
        assertTrue(again.paragraphs[0].spans.any { it.underline && it.italic })
        assertTrue(again.paragraphs[0].spans.any { it.bold })
        assertEquals("Untouched", again.paragraphs[1].text)
        assertEquals(emptyList<com.internship.scritto.data.model.NoteSpan>(), again.paragraphs[1].spans)
    }

    @Test
    fun columnLabelsContinuePastZ() {
        assertEquals("A", columnLabel(0))
        assertEquals("Z", columnLabel(25))
        assertEquals("AA", columnLabel(26))
    }
}
