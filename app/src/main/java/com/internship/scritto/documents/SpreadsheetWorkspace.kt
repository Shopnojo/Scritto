package com.internship.scritto.documents

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

@Composable
fun SpreadsheetWorkspace(
    context: Context,
    descriptor: DocumentDescriptor,
    modifier: Modifier = Modifier
) {
    var rows by remember(descriptor.uri) { mutableStateOf(emptyList<MutableList<String>>()) }
    var loaded by remember(descriptor.uri) { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<((OutputStream) -> Unit)?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            if (descriptor.kind == DocumentKind.XLSX) {
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            } else {
                "text/csv"
            }
        )
    ) { target ->
        target?.let { destination ->
            val operation = pendingSave
            pendingSave = null
            if (operation != null) {
                runCatching {
                    context.contentResolver.openOutputStream(destination)?.use { output ->
                        operation(output)
                    } ?: error("Unable to open destination")
                    status = "Saved"
                }.onFailure {
                    status = "Could not save: " + (it.message ?: "unknown error")
                }
            }
        }
    }

    LaunchedEffect(descriptor.uri, descriptor.kind) {
        val content = withContext(Dispatchers.IO) {
            DocumentEngine.readEditableText(context, descriptor)
        }
        rows = parseDelimited(content).map { it.toMutableList() }
        loaded = true
    }

    fun updateCell(rowIndex: Int, columnIndex: Int, value: String) {
        rows = rows.mapIndexed { index, row ->
            if (index == rowIndex) {
                row.toMutableList().also {
                    while (it.size <= columnIndex) it.add("")
                    it[columnIndex] = value
                }
            } else {
                row.toMutableList()
            }
        }
    }

    fun addRow() {
        val columns = rows.maxOfOrNull { it.size } ?: 1
        rows = rows + listOf(MutableList(columns.coerceAtLeast(1)) { "" })
    }

    fun addColumn() {
        rows = rows.map { it.toMutableList().also { row -> row.add("") } }
        if (rows.isEmpty()) rows = listOf(mutableListOf(""))
    }

    fun save() {
        val snapshot = rows.map { it.toList() }
        pendingSave = { output ->
            DocumentEngine.saveEditedText(
                context,
                descriptor,
                serializeDelimited(snapshot),
                output
            )
        }
        val extension = if (descriptor.kind == DocumentKind.XLSX) "xlsx" else "csv"
        saveLauncher.launch(descriptor.name.substringBeforeLast('.', descriptor.name) + "_edited." + extension)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (descriptor.kind == DocumentKind.XLSX) "Spreadsheet" else "CSV sheet",
                    color = ScrittoCreamBright,
                    fontSize = 18.sp
                )
                Text(
                    if (loaded) rows.size.toString() + " rows • " +
                        (rows.maxOfOrNull { it.size } ?: 0) + " columns"
                    else "Loading…",
                    color = ScrittoTextSecondary,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { save() }, enabled = loaded) {
                Icon(Icons.Outlined.Save, "Save", tint = ScrittoAmber)
            }
        }

        if (status != null) {
            Text(
                status.orEmpty(),
                color = ScrittoTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (loaded) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(ScrittoSurface.copy(alpha = 0.42f))
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
            ) {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    if (rows.isEmpty()) {
                        Text(
                            "Empty sheet",
                            color = ScrittoTextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        val columnCount = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                        Row {
                            repeat(columnCount) { columnIndex ->
                                Text(
                                    columnLabel(columnIndex),
                                    color = ScrittoTextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .width(132.dp)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }

                        rows.forEachIndexed { rowIndex, row ->
                            Row {
                                repeat(columnCount) { columnIndex ->
                                    OutlinedTextField(
                                        value = row.getOrNull(columnIndex).orEmpty(),
                                        onValueChange = { updateCell(rowIndex, columnIndex, it) },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(
                                            color = ScrittoCream,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        ),
                                        modifier = Modifier
                                            .width(132.dp)
                                            .heightIn(min = 52.dp)
                                            .padding(2.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedBorderColor = ScrittoAmber.copy(alpha = 0.8f),
                                            unfocusedBorderColor = ScrittoTextSecondary.copy(alpha = 0.35f),
                                            cursorColor = ScrittoAmber
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { addRow() }) { Text("Add row") }
                OutlinedButton(onClick = { addColumn() }) { Text("Add column") }
            }
        }
    }
}

private fun parseDelimited(text: String): List<List<String>> {
    if (text.isBlank()) return emptyList()
    return text.replace("\r\n", "\n").replace("\r", "\n")
        .split("\n")
        .filterNot { it.isEmpty() }
        .map(::parseDelimitedLine)
}

private fun parseDelimitedLine(line: String): List<String> {
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

private fun serializeDelimited(rows: List<List<String>>): String =
    rows.joinToString("\n") { row ->
        row.joinToString(",") { value ->
            if (value.contains(',') || value.contains('"') || value.contains('\n')) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
        }
    }

private fun columnLabel(index: Int): String {
    var value = index + 1
    val result = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        result.append(('A'.code + remainder).toChar())
        value = (value - 1) / 26
    }
    return result.reverse().toString()
}
