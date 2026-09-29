package com.internship.scritto.documents

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

object DocumentReader {
    fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
        }.orEmpty()

    fun readOfficeText(context: Context, uri: Uri, kind: DocumentKind): String {
        val preferred = if (kind == DocumentKind.DOCX) {
            "word/document.xml"
        } else {
            "xl/worksheets/sheet1.xml"
        }

        val entries = mutableMapOf<String, String>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory &&
                        (entry.name == preferred || entry.name == "xl/sharedStrings.xml")
                    ) {
                        entries[entry.name] = zip.bufferedReader(StandardCharsets.UTF_8).readText()
                    }
                    zip.closeEntry()
                }
            }
        }

        return if (kind == DocumentKind.DOCX) {
            xmlToReadableText(entries[preferred].orEmpty())
        } else {
            xmlToSheetText(
                entries[preferred].orEmpty(),
                extractSharedStrings(entries["xl/sharedStrings.xml"].orEmpty())
            )
        }
    }

    private fun extractSharedStrings(xml: String): List<String> {
        val result = mutableListOf<String>()
        val items = Regex("<si[\\s\\S]*?</si>").findAll(xml)
        for (item in items) {
            val texts = Regex("<t[^>]*>([\\s\\S]*?)</t>").findAll(item.value)
            result += texts.joinToString("") { decodeXml(it.groupValues[1]) }
        }
        return result
    }

    private fun xmlToSheetText(xml: String, shared: List<String>): String {
        if (xml.isBlank()) return ""

        val rows = Regex("<row[\\s\\S]*?</row>").findAll(xml)
        return rows.joinToString("\n") { rowMatch ->
            val cells = Regex("<c[\\s\\S]*?</c>").findAll(rowMatch.value)
            cells.joinToString("    ") { cellMatch ->
                val cell = cellMatch.value
                val isSharedString = cell.contains("t=\"s\"")
                val value = Regex("<v[^>]*>([\\s\\S]*?)</v>").find(cell)
                    ?.groupValues?.getOrNull(1)

                when {
                    isSharedString && value != null ->
                        shared.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                    value != null -> decodeXml(value)
                    else ->
                        Regex("<t[^>]*>([\\s\\S]*?)</t>").find(cell)
                            ?.groupValues?.getOrNull(1)
                            ?.let(::decodeXml)
                            .orEmpty()
                }
            }
        )
    }

    private fun xmlToReadableText(xml: String): String {
        if (xml.isBlank()) return ""

        val broken = xml
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("</w:tr>"), "\n")
            .replace(Regex("</w:tc>"), "    ")
            .replace(Regex("<w:tab[^>]*/>"), "\t")

        return decodeXml(Regex("<[^>]+>").replace(broken, ""))
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun decodeXml(value: String): String =
        value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
}
