package com.internship.scritto.documents

/**
 * CSV parsing and writing shared by the spreadsheet editor and the file engine.
 *
 * Quoted fields may contain commas, quotes ("" escapes a quote) and line breaks.
 * Blank lines are kept as empty rows, so row positions match the source file.
 */
object DelimitedText {

    fun parse(text: String): List<List<String>> {
        val source = text.replace("\r\n", "\n").replace('\r', '\n')
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0

        while (index < source.length) {
            val char = source[index]
            when {
                quoted -> if (char == '"') {
                    if (index + 1 < source.length && source[index + 1] == '"') {
                        cell.append('"')
                        index++
                    } else {
                        quoted = false
                    }
                } else {
                    cell.append(char)
                }

                char == '"' -> quoted = true

                char == ',' -> {
                    row += cell.toString()
                    cell.clear()
                }

                char == '\n' -> {
                    row += cell.toString()
                    cell.clear()
                    rows += row
                    row = mutableListOf()
                }

                else -> cell.append(char)
            }
            index++
        }

        // A last line without a trailing newline still counts as a row.
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }

        // A blank line comes out as a single empty cell; represent it as an empty row.
        return rows.map { if (it == listOf("")) emptyList() else it }
    }

    fun serialize(rows: List<List<String>>): String =
        rows.joinToString("\n") { row ->
            row.joinToString(",") { value ->
                if (value.contains(',') || value.contains('"') || value.contains('\n')) {
                    "\"" + value.replace("\"", "\"\"") + "\""
                } else {
                    value
                }
            }
        }
}
