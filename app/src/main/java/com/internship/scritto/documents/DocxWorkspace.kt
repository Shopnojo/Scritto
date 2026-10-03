package com.internship.scritto.documents

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.internship.scritto.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DocxWorkspace(context: Context, descriptor: DocumentDescriptor, modifier: Modifier = Modifier) {
    val paragraphs = remember(descriptor.uri) { mutableStateListOf<DocxParagraphBlock>() }
    val tables = remember(descriptor.uri) { mutableStateListOf<DocxTableBlock>() }
    var loaded by remember(descriptor.uri) { mutableStateOf(false) }
    var status by remember(descriptor.uri) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { destination ->
        if (destination != null) scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(destination)?.use {
                        DocxXmlEngine.save(
                            context,
                            descriptor.uri.toUri(),
                            paragraphs.toList(),
                            tables.toList(),
                            it
                        )
                    } ?: error("Could not open destination")
                    "Saved"
                }.getOrElse { "Could not save DOCX" }
            }
        }
    }

    LaunchedEffect(descriptor.uri) {
        runCatching {
            withContext(Dispatchers.IO) {
                DocxXmlEngine.read(context, descriptor.uri.toUri())
            }
        }.onSuccess { result ->
            paragraphs.clear()
            paragraphs.addAll(result.first)
            tables.clear()
            tables.addAll(result.second)
            loaded = true
            status = null
        }.onFailure { error ->
            loaded = false
            status = "Could not open DOCX: " + (error.message ?: "unsupported document")
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("DOCX workspace", color = ScrittoCreamBright, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            status?.let { Text(it, color = ScrittoTextSecondary, fontSize = 12.sp); Spacer(Modifier.width(8.dp)) }
            IconButton(
                onClick = {
                    status = null
                    launcher.launch(descriptor.name.substringBeforeLast('.', descriptor.name) + "_edited.docx")
                },
                enabled = loaded
            ) { Icon(Icons.Outlined.Save, "Save DOCX", tint = ScrittoCreamBright) }
        }
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading document…", color = ScrittoTextSecondary)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                itemsIndexed(paragraphs, key = { _, p -> "p_${p.index}" }) { pos, block ->
                    val heading = block.style.contains("Title", true) || block.style.contains("Heading", true)
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { paragraphs[pos] = block.copy(text = it) },
                        modifier = Modifier.fillMaxWidth().background(ScrittoSurface.copy(alpha = .58f)),
                        textStyle = TextStyle(
                            color = ScrittoCream,
                            fontSize = when {
                                block.style.contains("Title", true) -> 22.sp
                                block.style.contains("Heading 1", true) -> 19.sp
                                block.style.contains("Heading 2", true) -> 17.sp
                                else -> 14.sp
                            },
                            fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
                            lineHeight = 21.sp
                        ),
                        label = { if (block.style.isNotBlank()) Text(block.style) },
                        minLines = if (block.text.isBlank()) 1 else 2
                    )
                }
                itemsIndexed(tables, key = { _, t -> "t_${t.index}" }) { tablePos, table ->
                    Text("Table ${table.index + 1}", color = ScrittoCreamBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    table.rows.forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { c, value ->
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { newValue ->
                                        tables[tablePos] = table.copy(rows = table.rows.mapIndexed { ri, cells ->
                                            if (ri == r) cells.mapIndexed { ci, cell -> if (ci == c) newValue else cell } else cells
                                        })
                                    },
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(color = ScrittoCream, fontSize = 13.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
