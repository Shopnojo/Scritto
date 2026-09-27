package com.internship.scritto.ai

enum class FileKind {
    PDF, IMAGE, AUDIO, DOCX, PPTX, XLSX, ODF, RTF, TEXT,

    /** .doc / .xls / .ppt (the pre-2007 binary formats). */
    LEGACY_OFFICE,

    UNKNOWN
}

/**
 * Decides what a file really is from its bytes, not from what the picker or its name claims.
 * Files from Drive, WhatsApp or a camera often have a missing extension, a wrong MIME type
 * (image/jpg, application/octet-stream ...) or a renamed extension; the content never lies.
 */
object FileSniffer {

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "yml", "yaml",
        "log", "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "cs",
        "go", "rs", "sql", "ini", "cfg", "toml", "tex", "sh", "gradle", "properties", "css", "srt", "vtt"
    )

    private val MP3_SECOND_BYTES = setOf(0xFB, 0xFA, 0xF3, 0xF2, 0xE3, 0xE2)

    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "oga", "flac", "aiff", "aif")

    fun detect(bytes: ByteArray, mime: String?, name: String): FileKind {
        val extension = name.substringAfterLast('.', "").lowercase()
        val type = mime.orEmpty().lowercase()

        return when {
            isPdf(bytes) -> FileKind.PDF
            isImage(bytes) -> FileKind.IMAGE
            isLegacyOffice(bytes) -> FileKind.LEGACY_OFFICE
            isZip(bytes) -> zipKind(bytes)
            isRtf(bytes) -> FileKind.RTF
            isAudio(bytes) || type.startsWith("audio/") || extension in AUDIO_EXTENSIONS -> FileKind.AUDIO

            // A picker may say "image/*" for something we can't recognise; let the decoder try.
            type.startsWith("image/") -> FileKind.IMAGE

            type.startsWith("text/") || extension in TEXT_EXTENSIONS || looksLikeText(bytes) -> FileKind.TEXT

            else -> FileKind.UNKNOWN
        }
    }

    /** MIME type of a recognised image, or null. */
    fun imageMime(b: ByteArray): String? {
        if (!isImage(b)) return null

        fun ascii(from: Int, text: String) = text.indices.all { b.size > from + it && b[from + it].toInt() == text[it].code }

        return when {
            (b[0].toInt() and 0xFF) == 0x89 -> "image/png"
            (b[0].toInt() and 0xFF) == 0xFF -> "image/jpeg"
            ascii(0, "GIF8") -> "image/gif"
            ascii(0, "BM") -> "image/bmp"
            ascii(0, "RIFF") -> "image/webp"
            ascii(8, "avif") -> "image/avif"
            else -> "image/heic"
        }
    }

    private fun isPdf(b: ByteArray): Boolean {
        // The header may be preceded by a little junk.
        val limit = minOf(b.size - 4, 1024)
        for (i in 0..limit) {
            if (b[i] == '%'.code.toByte() && b[i + 1] == 'P'.code.toByte() &&
                b[i + 2] == 'D'.code.toByte() && b[i + 3] == 'F'.code.toByte()
            ) return true
        }
        return false
    }

    private fun isImage(b: ByteArray): Boolean {
        if (b.size < 12) return false

        fun at(i: Int) = b[i].toInt() and 0xFF
        fun ascii(from: Int, text: String) = text.indices.all { b.size > from + it && b[from + it].toInt() == text[it].code }

        return (at(0) == 0x89 && ascii(1, "PNG")) ||
            (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) ||
            ascii(0, "GIF8") ||
            (ascii(0, "BM") && b.size > 30) ||
            (ascii(0, "RIFF") && ascii(8, "WEBP")) ||
            (ascii(4, "ftyp") && listOf("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1", "heif", "avif")
                .any { ascii(8, it) })
    }

    private fun isAudio(b: ByteArray): Boolean {
        if (b.size < 12) return false

        fun ascii(from: Int, text: String) = text.indices.all { b[from + it].toInt() == text[it].code }
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF

        return ascii(0, "ID3") ||
            (b0 == 0xFF && b1 in MP3_SECOND_BYTES) ||
            (ascii(0, "RIFF") && ascii(8, "WAVE")) ||
            ascii(0, "OggS") ||
            ascii(0, "fLaC") ||
            (ascii(4, "ftyp") && ascii(8, "M4A"))
    }

    private fun isLegacyOffice(b: ByteArray): Boolean =
        b.size > 8 &&
            (b[0].toInt() and 0xFF) == 0xD0 && (b[1].toInt() and 0xFF) == 0xCF &&
            (b[2].toInt() and 0xFF) == 0x11 && (b[3].toInt() and 0xFF) == 0xE0

    private fun isZip(b: ByteArray): Boolean =
        b.size > 4 && b[0].toInt() == 0x50 && b[1].toInt() == 0x4B && b[2].toInt() == 0x03 && b[3].toInt() == 0x04

    private fun zipKind(bytes: ByteArray): FileKind {
        val names = OfficeText.zipEntryNames(bytes)

        return when {
            "word/document.xml" in names -> FileKind.DOCX
            names.any { it.startsWith("ppt/slides/slide") } -> FileKind.PPTX
            names.any { it.startsWith("xl/worksheets/") } -> FileKind.XLSX
            "content.xml" in names -> FileKind.ODF
            else -> FileKind.UNKNOWN
        }
    }

    private fun isRtf(b: ByteArray): Boolean =
        b.size > 5 && String(b, 0, 5, Charsets.ISO_8859_1) == "{\\rtf"

    /** Mostly printable characters, no binary NULs (UTF-16 text is recognised by its BOM). */
    fun looksLikeText(b: ByteArray): Boolean {
        if (b.isEmpty()) return true
        if (hasBom(b)) return true

        val sample = minOf(b.size, 4096)
        var odd = 0

        for (i in 0 until sample) {
            val v = b[i].toInt() and 0xFF
            if (v == 0) return false
            if (v < 0x20 && v != 0x09 && v != 0x0A && v != 0x0D && v != 0x0C) odd++
        }

        return odd * 20 < sample
    }

    private fun hasBom(b: ByteArray): Boolean =
        b.size >= 2 && (
            ((b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xFF) == 0xFE) ||
                ((b[0].toInt() and 0xFF) == 0xFE && (b[1].toInt() and 0xFF) == 0xFF) ||
                (b.size >= 3 && (b[0].toInt() and 0xFF) == 0xEF && (b[1].toInt() and 0xFF) == 0xBB && (b[2].toInt() and 0xFF) == 0xBF)
            )
}

/** Decodes text files whatever their encoding: UTF-8, UTF-16 (with BOM) or Windows-1252. */
object TextDecoder {
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF

            if (b0 == 0xFF && b1 == 0xFE) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            if (b0 == 0xFE && b1 == 0xFF) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            if (bytes.size >= 3 && b0 == 0xEF && b1 == 0xBB && (bytes[2].toInt() and 0xFF) == 0xBF) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
        }

        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)

        return runCatching { strict.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, java.nio.charset.Charset.forName("windows-1252")) }
    }
}
