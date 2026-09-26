package com.internship.scritto.ai

import java.util.zip.ZipInputStream

/** Pulls plain text out of document formats that are just zipped XML (and RTF). No libraries. */
object OfficeText {

    fun zipEntryNames(bytes: ByteArray): Set<String> {
        val names = HashSet<String>()

        runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    names += entry.name
                    zip.closeEntry()
                }
            }
        }

        return names
    }

    fun extract(kind: FileKind, bytes: ByteArray): String = when (kind) {
        FileKind.DOCX -> docx(bytes)
        FileKind.PPTX -> pptx(bytes)
        FileKind.XLSX -> xlsx(bytes)
        FileKind.ODF -> odf(bytes)
        FileKind.RTF -> rtf(TextDecoder.decode(bytes))
        else -> ""
    }

    // ------------------------------------------------------------------

    private fun docx(bytes: ByteArray): String {
        val parts = readEntries(bytes) { name ->
            name == "word/document.xml" ||
                name.startsWith("word/header") && name.endsWith(".xml") ||
                name.startsWith("word/footer") && name.endsWith(".xml") ||
                name == "word/footnotes.xml"
        }

        val body = xmlToText(parts["word/document.xml"].orEmpty(), listOf("</w:p>", "</w:tr>"))
        val extras = parts.filterKeys { it != "word/document.xml" }.values
            .map { xmlToText(it, listOf("</w:p>")) }
            .filter { it.isNotBlank() }

        return (listOf(body) + extras).joinToString("\n\n").trim()
    }

    private fun pptx(bytes: ByteArray): String {
        val slides = readEntries(bytes) { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }

        return slides.keys
            .sortedBy { Regex("(\\d+)").find(it.substringAfterLast('/'))?.value?.toIntOrNull() ?: 0 }
            .mapIndexed { index, name ->
                "--- Slide ${index + 1} ---\n" + xmlToText(slides.getValue(name), listOf("</a:p>"))
            }
            .joinToString("\n\n")
    }

    private fun xlsx(bytes: ByteArray): String {
        val parts = readEntries(bytes) {
            it == "xl/sharedStrings.xml" || it == "xl/workbook.xml" ||
                (it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml"))
        }

        val shared = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
            .findAll(parts["xl/sharedStrings.xml"].orEmpty())
            .map { xmlToText(it.groupValues[1], emptyList()) }
            .toList()

        val sheetNames = Regex("<sheet [^>]*name=\"([^\"]*)\"")
            .findAll(parts["xl/workbook.xml"].orEmpty())
            .map { unescape(it.groupValues[1]) }
            .toList()

        val sheets = parts.keys
            .filter { it.startsWith("xl/worksheets/sheet") }
            .sortedBy { Regex("(\\d+)").find(it.substringAfterLast('/'))?.value?.toIntOrNull() ?: 0 }

        return sheets.mapIndexed { index, name ->
            val rows = Regex("<row\\b[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
                .findAll(parts.getValue(name))
                .map { row ->
                    Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
                        .findAll(row.groupValues[1])
                        .joinToString("\t") { cell ->
                            val attrs = cell.groupValues[1]
                            val inner = cell.groupValues[2]
                            val type = Regex("\\bt=\"(\\w+)\"").find(attrs)?.groupValues?.get(1)
                            val value = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(inner)?.groupValues?.get(1)

                            when {
                                type == "s" -> shared.getOrNull(value?.toIntOrNull() ?: -1).orEmpty()
                                type == "inlineStr" -> xmlToText(inner, emptyList())
                                value != null -> unescape(value)
                                else -> ""
                            }
                        }
                        .trimEnd('\t')
                }
                .filter { it.isNotBlank() }
                .toList()

            "--- Sheet: ${sheetNames.getOrNull(index) ?: "Sheet ${index + 1}"} ---\n" + rows.joinToString("\n")
        }.joinToString("\n\n")
    }

    private fun odf(bytes: ByteArray): String {
        val content = readEntries(bytes) { it == "content.xml" }["content.xml"].orEmpty()

        return xmlToText(
            content
                .replace("</table:table-cell>", "\t")
                .replace("<text:tab/>", "\t")
                .replace("<text:line-break/>", "\n"),
            listOf("</text:p>", "</text:h>", "</table:table-row>")
        )
    }

    private val SKIPPED_RTF_GROUPS = listOf(
        "{\\*", "{\\fonttbl", "{\\colortbl", "{\\stylesheet", "{\\info", "{\\pict",
        "{\\object", "{\\themedata", "{\\datastore", "{\\latentstyles"
    )

    /** Rich Text Format: drop the control words, keep the words. */
    fun rtf(source: String): String {
        val out = StringBuilder()
        var depthSkip = -1
        var depth = 0
        var i = 0

        while (i < source.length) {
            val c = source[i]

            when {
                c == '{' -> {
                    depth++
                    // Groups that hold settings or binary data, not text.
                    if (depthSkip < 0 && SKIPPED_RTF_GROUPS.any { source.startsWith(it, i) }) depthSkip = depth
                    i++
                }

                c == '}' -> {
                    if (depth == depthSkip) depthSkip = -1
                    depth--
                    i++
                }

                c == '\\' -> {
                    val next = source.getOrNull(i + 1)

                    when {
                        next == '\\' || next == '{' || next == '}' -> {
                            if (depthSkip < 0) out.append(next)
                            i += 2
                        }

                        next == '\'' && i + 3 < source.length -> {
                            val code = source.substring(i + 2, i + 4).toIntOrNull(16)
                            if (code != null && depthSkip < 0) out.append(code.toChar())
                            i += 4
                        }

                        next != null && next.isLetter() -> {
                            var j = i + 1
                            while (j < source.length && source[j].isLetter()) j++
                            val word = source.substring(i + 1, j)
                            while (j < source.length && (source[j].isDigit() || source[j] == '-')) j++
                            if (j < source.length && source[j] == ' ') j++

                            if (depthSkip < 0) {
                                when (word) {
                                    "par", "line" -> out.append('\n')
                                    "tab" -> out.append('\t')
                                }
                            }
                            i = j
                        }

                        else -> i += 2
                    }
                }

                else -> {
                    if (depthSkip < 0 && c != '\r' && c != '\n') out.append(c)
                    i++
                }
            }
        }

        return out.toString().lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n").trim()
    }

    // ------------------------------------------------------------------

    private fun readEntries(bytes: ByteArray, wanted: (String) -> Boolean): Map<String, String> {
        val found = LinkedHashMap<String, String>()

        runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (wanted(entry.name)) found[entry.name] = String(zip.readBytes(), Charsets.UTF_8)
                    zip.closeEntry()
                }
            }
        }

        return found
    }

    fun xmlToText(xml: String, paragraphEnds: List<String>): String {
        var text = xml
        paragraphEnds.forEach { text = text.replace(it, "\n") }

        return unescape(
            text
                .replace(Regex("<w:tab\\s*/>"), "\t")
                .replace(Regex("<w:br\\s*/>"), "\n")
                .replace(Regex("<[^>]+>"), "")
        )
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun unescape(text: String): String =
        text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
            .replace("&amp;", "&")
}
