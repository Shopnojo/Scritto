package com.internship.scritto.documents

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spreadsheet editor: every sheet of an XLSX (or the single CSV table) on a white grid, with
 * sheet tabs. Save writes all sheets back.
 */
@Composable
fun SpreadsheetWorkspace(
    context: Context,
    descriptor: DocumentDescriptor,
    modifier: Modifier = Modifier
) {
    var sheets by remember(descriptor.uri) { mutableStateOf(emptyList<SheetData>()) }
    var selected by remember(descriptor.uri) { mutableStateOf(0) }
    var loaded by remember(descriptor.uri) { mutableStateOf(false) }
    var status by remember(descriptor.uri) { mutableStateOf<String?>(null) }
    val saver = rememberDocumentSaver(descriptor.uri.toUri()) { status = it }

    var failure by remember(descriptor.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(descriptor.uri, descriptor.kind) {
        runCatching { withContext(Dispatchers.IO) { DocumentEngine.readSheets(context, descriptor) } }
            .onSuccess { sheets = it }
            .onFailure { failure = DocumentEngine.unreadableMessage(descriptor.name) }
        selected = 0
        loaded = true
    }

    fun updateSelected(transform: (List<List<String>>) -> List<List<String>>) {
        sheets = sheets.mapIndexed { index, sheet ->
            if (index == selected) sheet.copy(rows = transform(sheet.rows)) else sheet
        }
    }

    val current = sheets.getOrNull(selected)
    val columnCount = current?.rows?.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1

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
                    if (loaded && current != null) {
                        "${current.rows.size} rows • $columnCount columns" +
                            if (sheets.size > 1) " • ${sheets.size} sheets" else ""
                    } else {
                        "Loading…"
                    },
                    color = ScrittoTextSecondary,
                    fontSize = 12.sp
                )
            }
            status?.let {
                Text(it, color = ScrittoTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
            }
            IconButton(
                onClick = {
                    status = null
                    val snapshot = sheets
                    val extension = if (descriptor.kind == DocumentKind.XLSX) "xlsx" else "csv"
                    saver.save(descriptor.name.substringBeforeLast('.', descriptor.name) + "_edited." + extension) {
                        DocumentEngine.renderSheets(context, descriptor, snapshot)
                    }
                },
                enabled = loaded
            ) {
                Icon(Icons.Outlined.Save, "Save", tint = ScrittoAmber)
            }
        }

        if (failure != null) {
            Text(failure.orEmpty(), color = ScrittoTextSecondary, modifier = Modifier.padding(vertical = 16.dp))
        } else if (loaded) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            SheetGridView(
                sheets = sheets,
                selected = selected,
                onSelect = { selected = it },
                editable = true,
                onCell = { row, column, value ->
                    updateSelected { rows ->
                        rows.mapIndexed { r, cells ->
                            if (r != row) {
                                cells
                            } else {
                                cells.toMutableList().also {
                                    while (it.size <= column) it.add("")
                                    it[column] = value
                                }
                            }
                        }
                    }
                }
            )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { updateSelected { it + listOf(emptyList<String>()) } }) { Text("Add row") }
                OutlinedButton(onClick = {
                    updateSelected { rows ->
                        if (rows.isEmpty()) listOf(listOf("")) else rows.map { it + "" }
                    }
                }) { Text("Add column") }
            }
        }
    }
}
