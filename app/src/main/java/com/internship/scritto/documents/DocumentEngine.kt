package com.internship.scritto.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.core.net.toUri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

object DocumentEngine {

    /** Text shown in the editor for a file. Matches what [saveEditedText] writes back. */
    fun readEditableText(context: Context, descriptor: DocumentDescriptor): String {
        val uri = descriptor.uri.toUri()
        return when (descriptor.kind) {
            DocumentKind.CSV -> DocumentReader.readText(context, uri)
            DocumentKind.DOCX -> context.contentResolver.openInputStream(uri)
                ?.use { OfficeTextCodec.docxToText(it) }.orEmpty()
            DocumentKind.XLSX -> context.contentResolver.openInputStream(uri)
                ?.use { DelimitedText.serialize(OfficeTextCodec.xlsxToRows(it)) }.orEmpty()
            DocumentKind.PDF -> readPdfText(context, uri)
            DocumentKind.TEXT -> DocumentReader.readText(context, uri)
            else -> ""
        }
    }

    /** What to show when a file cannot be read. Damaged or renamed files throw inside the parsers. */
    fun unreadableMessage(name: String): String =
        "“$name” could not be opened. It may be damaged, password-protected, or not a valid file of this type."

    /** A Word document as a page (paragraphs, tables, headers, footers) for the viewer and editor. */
    fun readDocxPage(context: Context, descriptor: DocumentDescriptor): DocxPage =
        context.contentResolver.openInputStream(descriptor.uri.toUri())?.use { OfficeTextCodec.docxPage(it) }
            ?: DocxPage("", "", emptyList(), emptyList(), emptyList())

    /** Every sheet of a spreadsheet (CSV has one). */
    fun readSheets(context: Context, descriptor: DocumentDescriptor): List<SheetData> {
        val uri = descriptor.uri.toUri()
        return when (descriptor.kind) {
            DocumentKind.XLSX -> context.contentResolver.openInputStream(uri)
                ?.use { OfficeTextCodec.xlsxSheets(it) }.orEmpty()
            else -> listOf(SheetData("Sheet1", DelimitedText.parse(DocumentReader.readText(context, uri))))
        }
    }

    /** Renders an edited Word page into memory, ready to be written. */
    fun renderDocxPage(
        context: Context,
        descriptor: DocumentDescriptor,
        paragraphs: List<DocxParagraph>,
        tables: List<DocxTable>
    ): ByteArray = toBytes { output ->
        withSource(context, descriptor.uri.toUri()) { source ->
            OfficeTextCodec.writeDocxPage(source, paragraphs, tables, output)
        }
    }

    /** Renders edited sheets into memory: all sheets for XLSX, the single table for CSV. */
    fun renderSheets(
        context: Context,
        descriptor: DocumentDescriptor,
        sheets: List<SheetData>
    ): ByteArray = toBytes { output ->
        when (descriptor.kind) {
            DocumentKind.XLSX -> withSource(context, descriptor.uri.toUri()) { source ->
                OfficeTextCodec.writeXlsxSheets(source, sheets, output)
            }
            else -> output.write(
                DelimitedText.serialize(sheets.firstOrNull()?.rows.orEmpty()).toByteArray(StandardCharsets.UTF_8)
            )
        }
    }

    fun readPdfText(context: Context, uri: Uri): String {
        initPdf(context)
        return context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { PDFTextStripper().getText(it) }
        }.orEmpty()
    }

    /** Writes edited [text] in the format of [descriptor] to [output]. */
    fun saveEditedText(
        context: Context,
        descriptor: DocumentDescriptor,
        text: String,
        output: OutputStream
    ) {
        val uri = descriptor.uri.toUri()
        when (descriptor.kind) {
            DocumentKind.DOCX -> withSource(context, uri) { source ->
                OfficeTextCodec.textToDocx(source, text, output)
            }
            DocumentKind.XLSX -> withSource(context, uri) { source ->
                OfficeTextCodec.rowsToXlsx(source, DelimitedText.parse(text), output)
            }
            DocumentKind.PDF -> writeTextPdf(context, text, output)
            else -> output.write(text.toByteArray(StandardCharsets.UTF_8))
        }
    }

    /** Renders an edit into memory so the file can be written in one go. */
    fun renderEditedText(
        context: Context,
        descriptor: DocumentDescriptor,
        text: String
    ): ByteArray = toBytes { saveEditedText(context, descriptor, text, it) }

    /** Renders a conversion into memory, ready to be written to a new file. */
    fun renderConversion(
        context: Context,
        descriptor: DocumentDescriptor,
        targetExtension: String
    ): ByteArray = toBytes { convert(context, descriptor, targetExtension, it) }

    /** True when the app was granted write access to [uri] (see FilesScreen). */
    fun canWriteInPlace(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    /** Replaces the content of [uri] with [bytes]. Callers render first, so a failure cannot leave a half-written file. */
    fun writeInPlace(context: Context, uri: Uri, bytes: ByteArray) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("The file cannot be opened for writing")
        output.use { it.write(bytes) }
    }

    fun convert(
        context: Context,
        descriptor: DocumentDescriptor,
        targetExtension: String,
        output: OutputStream
    ) {
        val uri = descriptor.uri.toUri()
        when (descriptor.kind) {
            DocumentKind.CSV -> when (targetExtension) {
                "xlsx" -> OfficeTextCodec.rowsToXlsx(
                    null,
                    DelimitedText.parse(DocumentReader.readText(context, uri)),
                    output
                )
                else -> output.write(DocumentReader.readText(context, uri).toByteArray(StandardCharsets.UTF_8))
            }
            DocumentKind.XLSX -> {
                val csv = context.contentResolver.openInputStream(uri)
                    ?.use { DelimitedText.serialize(OfficeTextCodec.xlsxToRows(it)) }.orEmpty()
                output.write(csv.toByteArray(StandardCharsets.UTF_8))
            }
            DocumentKind.DOCX -> {
                val text = context.contentResolver.openInputStream(uri)
                    ?.use { OfficeTextCodec.docxToText(it) }.orEmpty()
                when (targetExtension) {
                    "pdf" -> writeTextPdf(context, text, output)
                    else -> output.write(text.toByteArray(StandardCharsets.UTF_8))
                }
            }
            DocumentKind.PDF -> {
                val text = readPdfText(context, uri)
                when (targetExtension) {
                    "docx" -> OfficeTextCodec.textToDocx(null, text, output)
                    else -> output.write(text.toByteArray(StandardCharsets.UTF_8))
                }
            }
            DocumentKind.IMAGE -> {
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                } ?: error("Unable to decode image")
                val format = when (targetExtension.lowercase()) {
                    "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                    "webp" -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.PNG
                }
                bitmap.compress(format, 92, output)
                bitmap.recycle()
            }
            else -> output.write(DocumentReader.readText(context, uri).toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun transformImage(
        context: Context,
        descriptor: DocumentDescriptor,
        output: OutputStream,
        crop: CropRect? = null,
        rotation: Float = 0f,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
    ) {
        val source = context.contentResolver.openInputStream(descriptor.uri.toUri())?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: error("Unable to decode image")

        val cropped = crop?.let {
            val left = it.left.coerceIn(0, source.width - 1)
            val top = it.top.coerceIn(0, source.height - 1)
            val right = it.right.coerceIn(left + 1, source.width)
            val bottom = it.bottom.coerceIn(top + 1, source.height)
            Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        } ?: source

        val matrix = Matrix().apply {
            postRotate(rotation)
            postScale(
                if (flipHorizontal) -1f else 1f,
                if (flipVertical) -1f else 1f
            )
        }

        val transformed = if (!matrix.isIdentity) {
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        } else {
            cropped
        }

        transformed.compress(format, 92, output)

        if (transformed !== cropped) transformed.recycle()
        if (cropped !== source) cropped.recycle()
        source.recycle()
    }

    /**
     * Plain text as a multi-page A4 PDF. Long lines wrap and text continues onto new pages.
     * Uses a Unicode system font when one is available; otherwise characters outside the
     * Latin range are replaced with "?" rather than silently dropped.
     */
    private fun writeTextPdf(context: Context, text: String, output: OutputStream) {
        initPdf(context)
        val fontSize = 11f
        val leading = 15f
        val margin = 50f
        val pageSize = PDRectangle.A4
        val maxWidth = pageSize.width - margin * 2
        val linesPerPage = ((pageSize.height - margin * 2) / leading).toInt().coerceAtLeast(1)

        PDDocument().use { document ->
            val font = PdfFonts.load(document)
            val clean: (String) -> String = { PdfFonts.sanitize(font, it) }

            val lines = wrap(
                text.replace("\r\n", "\n").split("\n"),
                font,
                fontSize,
                maxWidth,
                clean
            )

            lines.chunked(linesPerPage).ifEmpty { listOf(emptyList()) }.forEach { pageLines ->
                val page = PDPage(pageSize)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(font, fontSize)
                    stream.setLeading(leading)
                    stream.newLineAtOffset(margin, pageSize.height - margin)
                    pageLines.forEach { line ->
                        stream.showText(line)
                        stream.newLine()
                    }
                    stream.endText()
                }
            }

            document.save(output)
        }
    }

    /** Greedy word wrap. A word longer than the line is broken by characters. */
    private fun wrap(
        paragraphs: List<String>,
        font: PDFont,
        fontSize: Float,
        maxWidth: Float,
        clean: (String) -> String
    ): List<String> {
        fun width(value: String) = font.getStringWidth(value) / 1000f * fontSize

        val out = mutableListOf<String>()
        paragraphs.forEach { paragraph ->
            var current = ""
            clean(paragraph).split(' ').forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (width(candidate) <= maxWidth) {
                    current = candidate
                    return@forEach
                }
                if (current.isNotEmpty()) out += current

                var rest = word
                while (width(rest) > maxWidth && rest.length > 1) {
                    var cut = rest.length - 1
                    while (cut > 1 && width(rest.substring(0, cut)) > maxWidth) cut--
                    out += rest.substring(0, cut)
                    rest = rest.substring(cut)
                }
                current = rest
            }
            out += current
        }
        return out
    }

    /** Opens [uri] for reading, or passes null when it cannot be opened, and always closes it. */
    private inline fun <T> withSource(context: Context, uri: Uri, block: (InputStream?) -> T): T {
        val source = context.contentResolver.openInputStream(uri)
        try {
            return block(source)
        } finally {
            source?.close()
        }
    }

    private fun toBytes(block: (OutputStream) -> Unit): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            block(buffer)
            buffer.toByteArray()
        }

    private fun initPdf(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
}
