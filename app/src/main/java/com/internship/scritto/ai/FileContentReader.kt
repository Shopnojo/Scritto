package com.internship.scritto.ai

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.internship.scritto.data.repository.ScrittoStore
import com.internship.scritto.documents.PdfEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Turns an imported file into text the assistant can read.
 *
 * What a file *is* is decided from its bytes ([FileSniffer]), so a missing extension, a wrong
 * MIME type or a renamed file doesn't matter.
 *
 * - text / code / CSV / JSON / Markdown, any encoding : read directly
 * - .docx .pptx .xlsx .odt/.ods/.odp .rtf             : text pulled out locally
 * - PDF, images (any size, any rotation), audio       : transcribed / described by Gemini
 */
class FileContentReader(
    context: Context,
    private val client: GeminiClient
) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Cache of Gemini transcriptions so a file is only sent once. */
    private val transcriptions = HashMap<String, String>()

    data class Result(
        val ok: Boolean,
        val text: String,
        val mimeType: String,
        val truncated: Boolean = false
    )

    suspend fun read(file: ScrittoStore.ImportedFile): Result =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(file.uri)
            val declaredMime = mimeTypeOf(uri, file.name)

            try {
                val bytes = readBytes(uri, MAX_READ_BYTES)
                    ?: return@withContext fail("This file is larger than 40 MB, which is too big for Scritto AI to read.", declaredMime)

                if (bytes.isEmpty()) return@withContext fail("This file is empty.", declaredMime)

                when (val kind = FileSniffer.detect(bytes, declaredMime, file.name)) {
                    FileKind.TEXT ->
                        clipped(TextDecoder.decode(bytes.copyOf(minOf(bytes.size, MAX_TEXT_BYTES))), declaredMime)

                    FileKind.DOCX, FileKind.PPTX, FileKind.XLSX, FileKind.ODF, FileKind.RTF -> {
                        val text = OfficeText.extract(kind, bytes)

                        if (text.isBlank()) {
                            fail("I couldn't find any readable text in this ${kind.name} file.", declaredMime)
                        } else {
                            clipped(text, declaredMime)
                        }
                    }

                    FileKind.PDF -> {
                        // Text PDFs are read on the device: instant, free, and never cut off by a
                        // failed model call. Only scans (no text layer) go to Gemini.
                        val local = localPdfText(bytes)

                        when {
                            local != null -> clipped(local, "application/pdf")
                            bytes.size > MAX_INLINE_BYTES ->
                                fail("This PDF looks scanned and is larger than 12 MB, which is too big for Scritto AI to read.", "application/pdf")
                            else -> transcribe(file, bytes, "application/pdf", declaredMime)
                        }
                    }

                    FileKind.IMAGE -> readImage(file, bytes, declaredMime)

                    FileKind.AUDIO -> {
                        if (bytes.size > MAX_INLINE_BYTES) {
                            fail("This audio file is larger than 12 MB, which is too big to transcribe.", declaredMime)
                        } else {
                            transcribe(file, bytes, audioMime(declaredMime, file.name), declaredMime)
                        }
                    }

                    FileKind.LEGACY_OFFICE -> fail(
                        "This is an old-format Office file (.doc, .xls or .ppt). Save it as .docx, .xlsx, .pptx or PDF and attach that instead.",
                        declaredMime
                    )

                    FileKind.UNKNOWN -> fail(
                        "I can't read this kind of file. Supported: text, code, CSV, JSON, Word, PowerPoint, Excel, OpenDocument, RTF, PDF, images and audio.",
                        declaredMime
                    )
                }
            } catch (e: SecurityException) {
                fail("I no longer have permission to open this file. Import it again from Files.", declaredMime)
            } catch (e: FileNotFoundException) {
                fail("This file was moved or deleted on the device. Import it again from Files.", declaredMime)
            } catch (e: OutOfMemoryError) {
                fail("This file is too big for this phone to process.", declaredMime)
            } catch (e: GeminiException) {
                fail(e.message ?: "Couldn't read the file right now. Try again in a moment.", declaredMime)
            }
        }

    // ------------------------------------------------------------------

    /** Page-labelled text of a PDF, or null when it has no usable text layer (a scan) or can't be parsed. */
    private fun localPdfText(bytes: ByteArray): String? {
        val read = runCatching {
            PdfEditor(appContext).readPages(ByteArrayInputStream(bytes), 1, Int.MAX_VALUE, MAX_CHARS)
        }.getOrNull() ?: return null

        val letters = read.pages.sumOf { page -> page.text.count { it.isLetterOrDigit() } }
        if (read.pages.isEmpty() || letters < MIN_LETTERS_PER_PAGE * read.pages.size) return null

        val body = read.pages.joinToString("\n\n") { "[Page ${it.page}]\n${it.text}" }

        return if (read.truncated) {
            body + "\n\n[Only the first ${read.pages.size} of ${read.pageCount} pages are included.]"
        } else {
            body
        }
    }

    private suspend fun readImage(file: ScrittoStore.ImportedFile, bytes: ByteArray, declaredMime: String): Result {
        // Normal path: shrink, straighten and re-encode so any photo is small and readable.
        val jpeg = ImagePrep.toJpeg(bytes)
        if (jpeg != null) return transcribe(file, jpeg, "image/jpeg", declaredMime)

        // The phone can't decode this format (e.g. HEIC on an old Android): let Gemini try it raw.
        val rawMime = FileSniffer.imageMime(bytes)

        return if (rawMime != null && bytes.size <= MAX_INLINE_BYTES) {
            transcribe(file, bytes, rawMime, declaredMime)
        } else {
            fail("I couldn't open this image. Try sharing it as a JPEG or PNG.", declaredMime)
        }
    }

    private suspend fun transcribe(
        file: ScrittoStore.ImportedFile,
        bytes: ByteArray,
        sendAs: String,
        declaredMime: String
    ): Result {
        val key = "${file.uri}#${bytes.size}"

        val text = transcriptions[key]
            ?: client.readFile(bytes, sendAs, instructionFor(sendAs))
                .also { if (it.isNotBlank()) transcriptions[key] = it }

        return if (text.isBlank()) {
            fail("No text or content could be read from this file.", declaredMime)
        } else {
            clipped(text, declaredMime)
        }
    }

    private fun mimeTypeOf(uri: Uri, name: String): String {
        val reported = resolver.getType(uri)
        if (!reported.isNullOrBlank() && reported != "application/octet-stream") return reported

        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: reported
            ?: "application/octet-stream"
    }

    private fun audioMime(declared: String, name: String): String {
        if (declared.startsWith("audio/")) return if (declared == "audio/mp3") "audio/mpeg" else declared

        return when (name.substringAfterLast('.', "").lowercase()) {
            "wav" -> "audio/wav"
            "m4a", "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aiff", "aif" -> "audio/aiff"
            else -> "audio/mpeg"
        }
    }

    private fun instructionFor(mime: String): String = when {
        mime.startsWith("audio/") ->
            "Transcribe this audio verbatim. Output only the transcript."

        mime.startsWith("image/") ->
            "Transcribe every piece of text visible in this image exactly, then add one short paragraph " +
                "describing what the image shows (objects, people, colours, layout, charts). Output only that."

        else ->
            "Extract all of the text in this document verbatim, preserving headings, lists and tables " +
                "as plain text. Describe charts or images briefly in [square brackets]. " +
                "Do not add commentary."
    }

    /** Reads at most [limit] bytes; returns null when the file is bigger. */
    private fun readBytes(uri: Uri, limit: Int): ByteArray? {
        val stream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

        return stream.use { input -> input.readUpTo(limit) }
    }

    private fun InputStream.readUpTo(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0

        while (true) {
            val read = read(buffer)
            if (read == -1) break

            total += read
            if (total > limit) return null

            out.write(buffer, 0, read)
        }

        return out.toByteArray()
    }

    private fun clipped(text: String, mime: String): Result {
        val truncated = text.length > MAX_CHARS

        return Result(
            ok = true,
            text = if (truncated) text.take(MAX_CHARS) else text,
            mimeType = mime,
            truncated = truncated
        )
    }

    private fun fail(message: String, mime: String) = Result(false, message, mime)

    private companion object {
        const val MAX_CHARS = 60_000
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024

        /** Below this many letters/digits per page a PDF is treated as a scan. */
        const val MIN_LETTERS_PER_PAGE = 20

        /** Raw PDFs/audio are sent as-is (base64 grows them by a third; requests cap at 20 MB). */
        const val MAX_INLINE_BYTES = 12 * 1024 * 1024

        /** Photos can be big; they are shrunk before sending. */
        const val MAX_READ_BYTES = 40 * 1024 * 1024
    }
}
