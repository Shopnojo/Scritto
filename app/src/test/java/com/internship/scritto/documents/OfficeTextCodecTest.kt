package com.internship.scritto.documents

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class OfficeTextCodecTest {

    private fun docxWith(vararg lines: String): ByteArray {
        val doc = XWPFDocument()
        lines.forEach { doc.createParagraph().createRun().setText(it) }
        return ByteArrayOutputStream().also { doc.write(it) }.toByteArray()
    }

    @Test
    fun docxEditKeepsParagraphCountAndOtherTextIntact() {
        val source = docxWith("one", "two", "three")

        val out = ByteArrayOutputStream()
        OfficeTextCodec.textToDocx(ByteArrayInputStream(source), "one\nchanged\nthree", out)

        val text = OfficeTextCodec.docxToText(ByteArrayInputStream(out.toByteArray()))
        assertEquals("one\nchanged\nthree", text)
    }

    @Test
    fun docxAddingAndRemovingLinesRoundTrips() {
        val source = docxWith("one", "two")

        val grown = ByteArrayOutputStream()
        OfficeTextCodec.textToDocx(ByteArrayInputStream(source), "one\ntwo\nthree\nfour", grown)
        assertEquals(
            "one\ntwo\nthree\nfour",
            OfficeTextCodec.docxToText(ByteArrayInputStream(grown.toByteArray()))
        )

        val shrunk = ByteArrayOutputStream()
        OfficeTextCodec.textToDocx(ByteArrayInputStream(grown.toByteArray()), "only", shrunk)
        assertEquals("only", OfficeTextCodec.docxToText(ByteArrayInputStream(shrunk.toByteArray())))
    }

    @Test
    fun xlsxKeepsNumbersAsNumbersAndTextAsText() {
        val out = ByteArrayOutputStream()
        OfficeTextCodec.rowsToXlsx(
            null,
            listOf(listOf("42", "007", "x"), emptyList(), listOf("1.5", "")),
            out
        )

        val rows = OfficeTextCodec.xlsxToRows(ByteArrayInputStream(out.toByteArray()))
        assertEquals(
            listOf(listOf("42", "007", "x"), emptyList<String>(), listOf("1.5")),
            rows
        )
    }
}
