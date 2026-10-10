package com.internship.scritto.documents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.internship.scritto.notes.NoteRichText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Page colours. Documents always read as black on white, whatever the app theme is. */
private val PaperColor = Color.White
private val InkColor = Color(0xFF1B1B1B)
private val InkMuted = Color(0xFF6B6B6B)
private val RuleColor = Color(0xFFD0D0D0)
private val HeaderTint = Color(0xFFF3F3F3)

/** A white sheet of paper floating on the app background, the way a document opens. */
@Composable
fun PaperSheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .background(PaperColor, RoundedCornerShape(4.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        content()
    }
}

/** A Word document laid out in reading order. Edits go to [onParagraph] and [onCell]. */
@Composable
fun DocxPageView(
    page: DocxPage,
    paragraphs: List<DocxParagraph>,
    tables: List<DocxTable>,
    editable: Boolean,
    /** The editing value of a paragraph: its text plus formatting, with the caret. */
    fieldFor: (DocxParagraph) -> TextFieldValue = { NoteRichText.valueFrom(it.text, it.spans) },
    onField: (index: Int, value: TextFieldValue) -> Unit = { _, _ -> },
    onFocus: (index: Int) -> Unit = {},
    onCell: (table: Int, row: Int, column: Int, value: String) -> Unit = { _, _, _, _ -> }
) {
    Column(Modifier.fillMaxWidth()) {
        if (page.header.isNotBlank()) {
            Text(page.header, color = InkMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }

        page.blocks.forEach { block ->
            when (block) {
                is DocxBlock.Paragraph -> {
                    val current = paragraphs.getOrNull(block.paragraph.index) ?: block.paragraph
                    DocxParagraphView(
                        paragraph = current,
                        editable = editable,
                        value = fieldFor(current),
                        onChange = { onField(current.index, it) },
                        onFocus = { onFocus(current.index) }
                    )
                }
                is DocxBlock.Table -> {
                    val current = tables.getOrNull(block.table.index) ?: block.table
                    DocxTableView(current, editable) { row, column, value ->
                        onCell(current.index, row, column, value)
                    }
                }
            }
        }

        if (page.footer.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(page.footer, color = InkMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DocxParagraphView(
    paragraph: DocxParagraph,
    editable: Boolean,
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit
) {
    val (size, weight) = when {
        paragraph.style.contains("Title", true) -> 26.sp to FontWeight.Bold
        paragraph.style.contains("Heading1", true) || paragraph.style.contains("Heading 1", true) -> 20.sp to FontWeight.Bold
        paragraph.style.contains("Heading", true) -> 17.sp to FontWeight.SemiBold
        else -> 14.sp to FontWeight.Normal
    }
    val align = when (paragraph.align) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        "both" -> TextAlign.Justify
        else -> TextAlign.Start
    }
    val style = TextStyle(
        color = InkColor,
        fontSize = size,
        fontWeight = weight,
        lineHeight = (size.value * 1.45f).sp,
        textAlign = align
    )

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        if (paragraph.bullet.isNotEmpty()) {
            Text("•  ", color = InkColor, fontSize = size, lineHeight = (size.value * 1.45f).sp)
        }

        when {
            paragraph.hasImage && paragraph.text.isBlank() -> Text(
                "[Picture]",
                color = InkMuted,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp
            )
            editable -> BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = style,
                cursorBrush = SolidColor(InkColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFocus() }
            )
            paragraph.text.isBlank() -> Spacer(Modifier.height(10.dp))
            else -> Text(
                NoteRichText.fromSpans(paragraph.text, paragraph.spans),
                style = style,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DocxTableView(table: DocxTable, editable: Boolean, onCell: (row: Int, column: Int, value: String) -> Unit) {
    val columns = table.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1

    Column(
        Modifier
            .padding(vertical = 8.dp)
            .horizontalScroll(rememberScrollState())
            .border(1.dp, RuleColor)
    ) {
        table.rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.background(if (rowIndex == 0) HeaderTint else PaperColor)) {
                repeat(columns) { columnIndex ->
                    val value = row.getOrNull(columnIndex).orEmpty()
                    CellBox(width = 140.dp) {
                        if (editable) {
                            BasicTextField(
                                value = value,
                                onValueChange = { onCell(rowIndex, columnIndex, it) },
                                textStyle = TextStyle(
                                    color = InkColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(InkColor),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                value,
                                color = InkColor,
                                fontSize = 13.sp,
                                fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellBox(width: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .width(width)
            .border(BorderStroke(0.5.dp, RuleColor))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        content()
    }
}

/** Sheet tabs plus a grid of cells, with column letters and row numbers. */
@Composable
fun SheetGridView(
    sheets: List<SheetData>,
    selected: Int,
    onSelect: (Int) -> Unit,
    editable: Boolean,
    onCell: (row: Int, column: Int, value: String) -> Unit = { _, _, _ -> },
    onDeleteRow: (row: Int) -> Unit = {},
    onDeleteColumn: (column: Int) -> Unit = {},
    onMoveRow: (from: Int, to: Int) -> Unit = { _, _ -> },
    onMoveColumn: (from: Int, to: Int) -> Unit = { _, _ -> }
) {
    val sheet = sheets.getOrNull(selected) ?: return
    val columns = sheet.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1

    Column(Modifier.fillMaxSize()) {
        if (sheets.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sheets.forEachIndexed { index, item ->
                    val active = index == selected
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) InkColor else HeaderTint)
                            .clickable { onSelect(index) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            item.name,
                            color = if (active) PaperColor else InkColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .shadow(4.dp, RoundedCornerShape(4.dp))
                .background(PaperColor)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                Row(Modifier.background(HeaderTint)) {
                    CornerCell()
                    repeat(columns) { column ->
                        GridColumnHeader(
                            label = columnLabel(column),
                            editable = editable,
                            maxIndex = columns - 1,
                            onDelete = { onDeleteColumn(column) },
                            onMove = { target -> onMoveColumn(column, target) }
                        )
                    }
                }

                if (sheet.rows.isEmpty()) {
                    Text(
                        "This sheet is empty.",
                        color = InkMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                sheet.rows.forEachIndexed { rowIndex, row ->
                    Row {
                        GridRowHeader(
                            label = (rowIndex + 1).toString(),
                            editable = editable,
                            maxIndex = sheet.rows.lastIndex,
                            onDelete = { onDeleteRow(rowIndex) },
                            onMove = { target -> onMoveRow(rowIndex, target) }
                        )
                        repeat(columns) { column ->
                            val value = row.getOrNull(column).orEmpty()
                            GridCell(width = 110.dp, header = false) {
                                if (editable) {
                                    BasicTextField(
                                        value = value,
                                        onValueChange = { onCell(rowIndex, column, it) },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = InkColor,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Default
                                        ),
                                        cursorBrush = SolidColor(InkColor),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(value, color = InkColor, fontSize = 13.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridRowHeader(
    label: String,
    editable: Boolean,
    maxIndex: Int,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    if (!editable) {
        GridCell(width = 44.dp, header = false) {
            Text(label, color = InkMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        return
    }

    val haptics = androidx.compose.ui.platform.LocalView.current
    val startIndex = label.toInt() - 1
    var dragDistance by remember { mutableStateOf(0f) }
    var targetIndex by remember { mutableStateOf(startIndex) }

    Row(
        Modifier.width(92.dp).height(36.dp).border(BorderStroke(0.5.dp, RuleColor)).background(HeaderTint),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.DragHandle,
            "Drag row $label",
            tint = InkMuted,
            modifier = Modifier
                .size(20.dp)
                .pointerInput(startIndex, maxIndex) {
                    detectDragGestures(
                        onDragStart = {
                            dragDistance = 0f
                            targetIndex = startIndex
                            haptics.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragDistance += amount.y
                            val nextTarget = (startIndex + (dragDistance / 36f).toInt())
                                .coerceIn(0, maxIndex)
                            if (nextTarget != targetIndex) {
                                targetIndex = nextTarget
                                haptics.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        },
                        onDragEnd = {
                            if (targetIndex != startIndex) {
                                onMove(targetIndex)
                                haptics.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            dragDistance = 0f
                            targetIndex = startIndex
                        },
                        onDragCancel = {
                            dragDistance = 0f
                            targetIndex = startIndex
                        }
                    )
                }
        )
        Text(label, color = InkMuted, fontSize = 11.sp, modifier = Modifier.width(24.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Delete, "Delete row $label", tint = InkMuted)
        }
    }
}

@Composable
private fun GridColumnHeader(
    label: String,
    editable: Boolean,
    maxIndex: Int,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    if (!editable) {
        GridCell(width = 110.dp, header = true) {
            Text(label, color = InkMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        return
    }

    val haptics = androidx.compose.ui.platform.LocalView.current
    val startIndex = columnIndex(label)
    var dragDistance by remember { mutableStateOf(0f) }
    var targetIndex by remember { mutableStateOf(startIndex) }

    Row(
        Modifier.width(110.dp).height(30.dp).border(BorderStroke(0.5.dp, RuleColor)).background(HeaderTint),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.DragHandle,
            "Drag column $label",
            tint = InkMuted,
            modifier = Modifier
                .size(18.dp)
                .pointerInput(startIndex, maxIndex) {
                    detectDragGestures(
                        onDragStart = {
                            dragDistance = 0f
                            targetIndex = startIndex
                            haptics.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragDistance += amount.x
                            val nextTarget = (startIndex + (dragDistance / 110f).toInt())
                                .coerceIn(0, maxIndex)
                            if (nextTarget != targetIndex) {
                                targetIndex = nextTarget
                                haptics.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        },
                        onDragEnd = {
                            if (targetIndex != startIndex) {
                                onMove(targetIndex)
                                haptics.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            dragDistance = 0f
                            targetIndex = startIndex
                        },
                        onDragCancel = {
                            dragDistance = 0f
                            targetIndex = startIndex
                        }
                    )
                }
        )
        Text(label, color = InkMuted, fontSize = 11.sp)
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Outlined.Delete, "Delete column $label", tint = InkMuted)
        }
    }
}

private fun columnIndex(label: String): Int {
    var result = 0
    label.forEach { result = result * 26 + (it.code - 'A'.code + 1) }
    return result - 1
}

@Composable
private fun CornerCell() {
    GridCell(width = 44.dp, header = true) { Spacer(Modifier.size(1.dp)) }
}

@Composable
private fun GridCell(width: Dp, header: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .width(width)
            .height(if (header) 30.dp else 36.dp)
            .border(BorderStroke(0.5.dp, RuleColor))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = if (header) Alignment.Center else Alignment.CenterStart
    ) {
        content()
    }
}

/** Spreadsheet column label: 0 -> A, 25 -> Z, 26 -> AA. */
fun columnLabel(index: Int): String {
    var value = index + 1
    val result = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        result.append(('A'.code + remainder).toChar())
        value = (value - 1) / 26
    }
    return result.reverse().toString()
}
