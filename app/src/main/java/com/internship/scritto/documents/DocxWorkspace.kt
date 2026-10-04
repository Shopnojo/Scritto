package com.internship.scritto.documents

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.internship.scritto.notes.NoteRichText
import com.internship.scritto.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Word document editor: the whole document on a white page, in reading order. Paragraphs,
 * lists, tables, headers and footers are shown and editable. The toolbar formats the paragraph
 * you are typing in (bold, italic, underline, strike, alignment); Save writes it back.
 */
@Composable
fun DocxWorkspace(context: Context, descriptor: DocumentDescriptor, modifier: Modifier = Modifier) {
    var page by remember(descriptor.uri) { mutableStateOf<DocxPage?>(null) }
    var paragraphs by remember(descriptor.uri) { mutableStateOf(emptyList<DocxParagraph>()) }
    var tables by remember(descriptor.uri) { mutableStateOf(emptyList<DocxTable>()) }
    var status by remember(descriptor.uri) { mutableStateOf<String?>(null) }
    var failure by remember(descriptor.uri) { mutableStateOf<String?>(null) }
    // Editing value per paragraph (text, formatting and caret), keyed by paragraph index.
    val fields = remember(descriptor.uri) { mutableStateMapOf<Int, TextFieldValue>() }
    var focused by remember(descriptor.uri) { mutableStateOf<Int?>(null) }
    // A toolbar style tapped with no text selected: applies to the next typed characters.
    var pending by remember(descriptor.uri) { mutableStateOf<Int?>(null) }
    val saver = rememberDocumentSaver(descriptor.uri.toUri()) { status = it }

    LaunchedEffect(descriptor.uri) {
        runCatching { withContext(Dispatchers.IO) { DocumentEngine.readDocxPage(context, descriptor) } }
            .onSuccess { loaded ->
                page = loaded
                paragraphs = loaded.paragraphs
                tables = loaded.tables
            }
            .onFailure { failure = DocumentEngine.unreadableMessage(descriptor.name) }
    }

    fun valueOf(index: Int): TextFieldValue {
        fields[index]?.let { return it }
        val paragraph = paragraphs.firstOrNull { it.index == index } ?: return TextFieldValue()
        return NoteRichText.valueFrom(paragraph.text, paragraph.spans)
    }

    /** Keeps the paragraph model in step with what is on screen. */
    fun sync(index: Int, value: TextFieldValue, align: String? = null) {
        paragraphs = paragraphs.map {
            if (it.index != index) it
            else it.copy(
                text = value.text,
                spans = NoteRichText.toSpans(value.annotatedString),
                align = align ?: it.align
            )
        }
    }

    fun fieldChanged(index: Int, incoming: TextFieldValue) {
        val result = NoteRichText.reconcile(valueOf(index), incoming, pending)
        fields[index] = result.value
        pending = result.pending
        sync(index, result.value)
    }

    fun applyToFocused(transform: (TextFieldValue) -> NoteRichText.Result) {
        val index = focused ?: return
        val result = transform(valueOf(index))
        fields[index] = result.value
        pending = result.pending
        sync(index, result.value)
    }

    fun setAlign(align: String) {
        val index = focused ?: return
        paragraphs = paragraphs.map { if (it.index == index) it.copy(align = align) else it }
    }

    val focusedValue = focused?.let { valueOf(it) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Word document", color = ScrittoCreamBright, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            status?.let {
                Text(it, color = ScrittoTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(
                onClick = {
                    status = null
                    val snapshotParagraphs = paragraphs
                    val snapshotTables = tables
                    saver.save(descriptor.name.substringBeforeLast('.', descriptor.name) + "_edited.docx") {
                        DocumentEngine.renderDocxPage(context, descriptor, snapshotParagraphs, snapshotTables)
                    }
                },
                enabled = page != null
            ) { Icon(Icons.Outlined.Save, "Save DOCX", tint = ScrittoCreamBright) }
        }

        val loaded = page
        when {
            loaded == null && failure != null ->
                Text(failure.orEmpty(), color = ScrittoTextSecondary, modifier = Modifier.padding(20.dp))

            loaded == null ->
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = ScrittoAmber)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading document…", color = ScrittoTextSecondary)
                }

            else -> {
                FormatBar(
                    enabled = focused != null,
                    value = focusedValue,
                    pending = pending,
                    onStyle = { style -> applyToFocused { NoteRichText.toggle(it, style, pending) } },
                    onClear = { applyToFocused { NoteRichText.clearFormatting(it) } },
                    onAlign = { setAlign(it) }
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PaperSheet {
                        DocxPageView(
                            page = loaded,
                            paragraphs = paragraphs,
                            tables = tables,
                            editable = true,
                            fieldFor = { valueOf(it.index) },
                            onField = { index, value -> fieldChanged(index, value) },
                            onFocus = { index ->
                                if (focused != index) pending = null
                                focused = index
                            },
                            onCell = { tableIndex, row, column, value ->
                                tables = tables.map { table ->
                                    if (table.index != tableIndex) table
                                    else table.copy(rows = table.rows.mapIndexed { r, cells ->
                                        if (r != row) cells
                                        else cells.mapIndexed { c, cell -> if (c == column) value else cell }
                                    })
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Bold, italic, underline, strike, clear and alignment for the paragraph being edited. */
@Composable
private fun FormatBar(
    enabled: Boolean,
    value: TextFieldValue?,
    pending: Int?,
    onStyle: (Int) -> Unit,
    onClear: () -> Unit,
    onAlign: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StyleButton("B", enabled, value != null && NoteRichText.isActive(value, NoteRichText.BOLD, pending)) {
            onStyle(NoteRichText.BOLD)
        }
        StyleButton("I", enabled, value != null && NoteRichText.isActive(value, NoteRichText.ITALIC, pending)) {
            onStyle(NoteRichText.ITALIC)
        }
        StyleButton("U", enabled, value != null && NoteRichText.isActive(value, NoteRichText.UNDERLINE, pending)) {
            onStyle(NoteRichText.UNDERLINE)
        }
        StyleButton("S", enabled, value != null && NoteRichText.isActive(value, NoteRichText.STRIKE, pending)) {
            onStyle(NoteRichText.STRIKE)
        }
        IconChip(Icons.AutoMirrored.Outlined.FormatAlignLeft, "Align left", enabled) { onAlign("left") }
        IconChip(Icons.Outlined.FormatAlignCenter, "Align centre", enabled) { onAlign("center") }
        IconChip(Icons.AutoMirrored.Outlined.FormatAlignRight, "Align right", enabled) { onAlign("right") }
        IconChip(Icons.Outlined.FormatClear, "Clear formatting", enabled) { onClear() }
    }
}

@Composable
private fun StyleButton(label: String, enabled: Boolean, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) ScrittoAmber.copy(alpha = 0.25f) else ScrittoSurface.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) ScrittoCreamBright else ScrittoTextSecondary,
            fontSize = 16.sp,
            fontWeight = if (label == "B") FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (label == "I") FontStyle.Italic else FontStyle.Normal,
            textDecoration = when (label) {
                "U" -> TextDecoration.Underline
                "S" -> TextDecoration.LineThrough
                else -> null
            }
        )
    }
}

@Composable
private fun IconChip(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ScrittoSurface.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) ScrittoCreamBright else ScrittoTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
