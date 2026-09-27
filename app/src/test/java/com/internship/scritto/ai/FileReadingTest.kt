package com.internship.scritto.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileReadingTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13, 0, 0, 0, 0)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0, 1, 1, 0)

    // ---- what is this file? -------------------------------------------------------------

    @Test
    fun recognisesImagesWhateverTheNameOrMimeSays() {
        assertEquals(FileKind.IMAGE, FileSniffer.detect(png, "application/octet-stream", "photo"))
        assertEquals(FileKind.IMAGE, FileSniffer.detect(jpeg, "image/jpg", "IMG_0001"))
        assertEquals(FileKind.IMAGE, FileSniffer.detect(jpeg, null, "renamed.txt"))
        assertEquals("image/png", FileSniffer.imageMime(png))
        assertEquals("image/jpeg", FileSniffer.imageMime(jpeg))
    }

    @Test
    fun recognisesPdfEvenWithLeadingJunkOrNoExtension() {
        val pdf = "\n\n%PDF-1.7\n1 0 obj".toByteArray()
        assertEquals(FileKind.PDF, FileSniffer.detect(pdf, "application/octet-stream", "download"))
    }

    @Test
    fun recognisesOfficeFilesByTheirContentsNotTheirName() {
        val docx = zip("[Content_Types].xml" to "<x/>", "word/document.xml" to "<w:document/>")
        val pptx = zip("ppt/slides/slide1.xml" to "<p/>")
        val xlsx = zip("xl/worksheets/sheet1.xml" to "<worksheet/>")
        val odt = zip("mimetype" to "application/vnd.oasis.opendocument.text", "content.xml" to "<office:document/>")

        assertEquals(FileKind.DOCX, FileSniffer.detect(docx, "application/octet-stream", "Report"))
        assertEquals(FileKind.PPTX, FileSniffer.detect(pptx, null, "deck"))
        assertEquals(FileKind.XLSX, FileSniffer.detect(xlsx, null, "sheet.bin"))
        assertEquals(FileKind.ODF, FileSniffer.detect(odt, null, "notes.odt"))
    }

    @Test
    fun oldBinaryOfficeFilesGetAClearVerdict() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0, 0, 0, 0)
        assertEquals(FileKind.LEGACY_OFFICE, FileSniffer.detect(ole, "application/msword", "old.doc"))
    }

    @Test
    fun plainTextIsTextEvenWithoutAnExtension() {
        assertEquals(FileKind.TEXT, FileSniffer.detect("just some notes\nline two".toByteArray(), null, "README"))
        assertEquals(FileKind.UNKNOWN, FileSniffer.detect(ByteArray(64) { it.toByte() }, "application/octet-stream", "blob"))
    }

    // ---- reading them -------------------------------------------------------------------

    @Test
    fun readsWordIncludingTablesAndHeaders() {
        val docx = zip(
            "word/document.xml" to "<w:document><w:body><w:p><w:r><w:t>Hello &amp; welcome</w:t></w:r></w:p>" +
                "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Cell A</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>",
            "word/header1.xml" to "<w:hdr><w:p><w:r><w:t>CONFIDENTIAL</w:t></w:r></w:p></w:hdr>"
        )

        val text = OfficeText.extract(FileKind.DOCX, docx)
        assertTrue(text, text.contains("Hello & welcome"))
        assertTrue(text, text.contains("Cell A"))
        assertTrue(text, text.contains("CONFIDENTIAL"))
    }

    @Test
    fun readsPowerPointSlidesInOrder() {
        val pptx = zip(
            "ppt/slides/slide10.xml" to "<a:p><a:t>Ten</a:t></a:p>",
            "ppt/slides/slide2.xml" to "<a:p><a:t>Two</a:t></a:p>",
            "ppt/slides/slide1.xml" to "<a:p><a:t>One</a:t></a:p>"
        )

        val text = OfficeText.extract(FileKind.PPTX, pptx)
        assertTrue(text, text.indexOf("One") < text.indexOf("Two"))
        assertTrue(text, text.indexOf("Two") < text.indexOf("Ten"))
    }

    @Test
    fun readsExcelTextAndNumbersRowByRow() {
        val xlsx = zip(
            "xl/workbook.xml" to "<workbook><sheets><sheet name=\"Budget\" sheetId=\"1\"/></sheets></workbook>",
            "xl/sharedStrings.xml" to "<sst><si><t>Item</t></si><si><t>Cost</t></si><si><t>Hotel</t></si></sst>",
            "xl/worksheets/sheet1.xml" to "<worksheet><sheetData>" +
                "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>" +
                "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"B2\"><v>4500</v></c></row>" +
                "</sheetData></worksheet>"
        )

        val text = OfficeText.extract(FileKind.XLSX, xlsx)
        assertTrue(text, text.contains("Sheet: Budget"))
        assertTrue(text, text.contains("Item\tCost"))
        assertTrue(text, text.contains("Hotel\t4500"))
    }

    @Test
    fun readsOpenDocumentText() {
        val odt = zip("content.xml" to "<office:text><text:p>First para</text:p><text:p>Second<text:tab/>tabbed</text:p></office:text>")
        val text = OfficeText.extract(FileKind.ODF, odt)
        assertEquals("First para\nSecond\ttabbed", text)
    }

    @Test
    fun readsRtfWithoutControlWords() {
        val rtf = "{\\rtf1\\ansi{\\fonttbl{\\f0 Arial;}}{\\*\\generator Word;}\\pard Hello \\b bold\\b0  world\\par Caf\\'e9 line two\\par}"
        val text = OfficeText.rtf(rtf)
        assertTrue(text, text.contains("Hello bold world"))
        assertTrue(text, text.contains("Café line two"))
        assertTrue(text, !text.contains("Arial") && !text.contains("generator"))
    }

    // ---- text encodings -----------------------------------------------------------------

    @Test
    fun decodesUtf16FilesFromWindows() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Total: 45,000".toByteArray(Charsets.UTF_16LE)
        assertEquals("Total: 45,000", TextDecoder.decode(bytes))
        assertEquals(FileKind.TEXT, FileSniffer.detect(bytes, null, "export"))
    }

    @Test
    fun decodesWindows1252InsteadOfShowingGarbage() {
        val bytes = "Café €10".toByteArray(java.nio.charset.Charset.forName("windows-1252"))
        assertEquals("Café €10", TextDecoder.decode(bytes))
    }

    @Test
    fun decodesUtf8WithBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "namaste नमस्ते".toByteArray()
        assertEquals("namaste नमस्ते", TextDecoder.decode(bytes))
    }
}
