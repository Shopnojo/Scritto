package com.internship.scritto.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.max

object DocumentEngine {
    fun readEditableText(context: Context, descriptor: DocumentDescriptor): String {
        return when (descriptor.kind) {
            DocumentKind.CSV -> DocumentReader.readText(context, descriptor.uri.toUri())
            DocumentKind.DOCX -> context.contentResolver.openInputStream(descriptor.uri.toUri())?.use { input ->
                XWPFDocument(input).use { doc ->
                    doc.paragraphs.joinToString("\n") { it.text } +
                        doc.tables.flatMap { table ->
                            table.rows.flatMap { row ->
                                row.tableCells.map { it.text }
                            }
                        }.joinToString("\n", prefix = if (doc.tables.isNotEmpty()) "\n" else "")
                }
            }.orEmpty()
            DocumentKind.XLSX -> context.contentResolver.openInputStream(descriptor.uri.toUri())?.use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet = workbook.getSheetAt(0)
                    buildString {
                        for (row in sheet) {
                            val last = row.lastCellNum.toInt().coerceAtLeast(0)
                            for (column in 0 until last) {
                                if (column > 0) append(',')
                                append(csvEscape(row.getCell(column)?.toString().orEmpty()))
                            }
                            append('\n')
                        }
                    }.trimEnd()
                }
            }.orEmpty()
            DocumentKind.PDF -> readPdfText(context, descriptor.uri.toUri())
            else -> ""
        }
    }

    fun readPdfText(context: Context, uri: Uri): String {
        initPdf(context)
        return context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { PDFTextStripper().getText(it) }
        }.orEmpty()
    }

    fun saveEditedText(
        context: Context,
        descriptor: DocumentDescriptor,
        text: String,
        output: OutputStream
    ) {
        when (descriptor.kind) {
            DocumentKind.CSV -> output.write(text.toByteArray(StandardCharsets.UTF_8))
            DocumentKind.DOCX -> writeDocx(context, descriptor.uri.toUri(), text, output)
            DocumentKind.XLSX -> writeXlsxFromCsv(text, output)
            DocumentKind.PDF -> writePdfText(context, descriptor.uri.toUri(), text, output)
            else -> output.write(text.toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun convert(
        context: Context,
        descriptor: DocumentDescriptor,
        targetExtension: String,
        output: OutputStream
    ) {
        when (descriptor.kind) {
            DocumentKind.CSV -> when (targetExtension) {
                "xlsx" -> writeXlsxFromCsv(DocumentReader.readText(context, descriptor.uri.toUri()), output)
                "txt" -> output.write(DocumentReader.readText(context, descriptor.uri.toUri()).toByteArray())
                else -> output.write(DocumentReader.readText(context, descriptor.uri.toUri()).toByteArray())
            }
            DocumentKind.XLSX -> when (targetExtension) {
                "csv" -> output.write(readXlsxAsCsv(context, descriptor.uri.toUri()).toByteArray())
                "txt" -> output.write(readXlsxAsCsv(context, descriptor.uri.toUri()).toByteArray())
                else -> output.write(readXlsxAsCsv(context, descriptor.uri.toUri()).toByteArray())
            }
            DocumentKind.DOCX -> when (targetExtension) {
                "pdf" -> writePdfText(context, null, readDocx(context, descriptor.uri.toUri()), output)
                "txt" -> output.write(readDocx(context, descriptor.uri.toUri()).toByteArray())
                else -> output.write(readDocx(context, descriptor.uri.toUri()).toByteArray())
            }
            DocumentKind.PDF -> when (targetExtension) {
                "txt" -> output.write(readPdfText(context, descriptor.uri.toUri()).toByteArray())
                "docx" -> writeDocxFromText(readPdfText(context, descriptor.uri.toUri()), output)
                else -> output.write(readPdfText(context, descriptor.uri.toUri()).toByteArray())
            }
            DocumentKind.IMAGE -> {
                val bitmap = context.contentResolver.openInputStream(descriptor.uri.toUri())?.use {
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
            else -> output.write(DocumentReader.readText(context, descriptor.uri.toUri()).toByteArray())
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

    private fun writeDocx(
        context: Context,
        sourceUri: Uri,
        text: String,
        output: OutputStream
    ) {
        val doc = context.contentResolver.openInputStream(sourceUri)?.use { XWPFDocument(it) }
            ?: XWPFDocument()

        val lines = text.replace("\r\n", "\n").split("\n")
        val paragraphs = doc.paragraphs.toMutableList()

        lines.forEachIndexed { index, line ->
            val paragraph = paragraphs.getOrNull(index) ?: doc.createParagraph()
            while (paragraph.runs.isNotEmpty()) paragraph.removeRun(0)
            paragraph.createRun().setText(line)
        }

        while (doc.paragraphs.size > lines.size && doc.paragraphs.isNotEmpty()) {
            doc.removeBodyElement(doc.bodyElements.lastIndex)
        }

        doc.write(output)
        doc.close()
    }

    private fun writeDocxFromText(text: String, output: OutputStream) {
        XWPFDocument().use { doc ->
            text.replace("\r\n", "\n").split("\n").forEach { line ->
                doc.createParagraph().createRun().setText(line)
            }
            doc.write(output)
        }
    }

    private fun readDocx(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { input ->
            XWPFDocument(input).use { doc ->
                doc.paragraphs.joinToString("\n") { it.text }
            }
        }.orEmpty()

    private fun writeXlsxFromCsv(text: String, output: OutputStream) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Scritto")
            text.replace("\r\n", "\n").split("\n").forEachIndexed { rowIndex, line ->
                val row = sheet.createRow(rowIndex)
                parseCsvLine(line).forEachIndexed { columnIndex, value ->
                    row.createCell(columnIndex).setCellValue(value)
                }
            }
            workbook.write(output)
        }
    }

    private fun readXlsxAsCsv(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                buildString {
                    for (row in sheet) {
                        val last = row.lastCellNum.toInt().coerceAtLeast(0)
                        for (column in 0 until last) {
                            if (column > 0) append(',')
                            append(csvEscape(row.getCell(column)?.toString().orEmpty()))
                        }
                        append('\n')
                    }
                }.trimEnd()
            }
        }.orEmpty()

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' -> {
                    if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                        current.append('"')
                        index++
                    } else {
                        quoted = !quoted
                    }
                }
                char == ',' && !quoted -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        result += current.toString()
        return result
    }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            """ + value.replace(""", """") + """
        } else value

    private fun writePdfText(
        context: Context,
        sourceUri: Uri?,
        text: String,
        output: OutputStream
    ) {
        initPdf(context)
        val document = if (sourceUri != null) {
            context.contentResolver.openInputStream(sourceUri)?.use { PDDocument.load(it) }
                ?: PDDocument()
        } else {
            PDDocument()
        }

        if (document.numberOfPages == 0) document.addPage(com.tom_roush.pdfbox.pdmodel.PDPage(PDRectangle.A4))

        val page = document.getPage(0)
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true
        ).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 10f)
            stream.setLeading(14f)
            stream.newLineAtOffset(40f, page.mediaBox.height - 50f)
            text.replace("\r\n", "\n").split("\n").take(45).forEach { line ->
                stream.showText(line.filter { it.code in 32..126 })
                stream.newLine()
            }
            stream.endText()
        }

        document.save(output)
        document.close()
    }

    private fun initPdf(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
}
