package com.internship.scritto.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoCream
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoSurface
import com.internship.scritto.ui.theme.ScrittoTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class PdfAnnotationTool {
    SELECT,
    TEXT,
    DRAW
}

private data class PdfTextAnnotation(
    val x: Float,
    val y: Float,
    val width: Float = 220f,
    val height: Float = 64f,
    val text: String = "Text"
)

private data class PdfPageState(
    val bitmap: Bitmap,
    val texts: List<PdfTextAnnotation> = emptyList(),
    val strokes: List<List<Offset>> = emptyList()
)

@Composable
fun PdfAnnotationWorkspace(
    modifier: Modifier,
    descriptor: DocumentDescriptor
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tool by remember(descriptor.uri) { mutableStateOf(PdfAnnotationTool.TEXT) }
    var pages by remember(descriptor.uri) { mutableStateOf<List<PdfPageState>>(emptyList()) }

    LaunchedEffect(descriptor.uri) {
        pages = withContext(Dispatchers.IO) {
            renderPdfPages(context, descriptor.uri.toUri())
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolChip(
                label = "Select",
                selected = tool == PdfAnnotationTool.SELECT,
                onClick = { tool = PdfAnnotationTool.SELECT }
            )
            ToolChip(
                label = "Add text",
                selected = tool == PdfAnnotationTool.TEXT,
                onClick = { tool = PdfAnnotationTool.TEXT }
            )
            ToolChip(
                label = "Draw",
                selected = tool == PdfAnnotationTool.DRAW,
                onClick = { tool = PdfAnnotationTool.DRAW }
            )
            Spacer(Modifier.weight(1f))
            Text(
                "PDF canvas",
                color = ScrittoTextSecondary,
                fontSize = 12.sp
            )
        }

        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading PDF canvas…", color = ScrittoTextSecondary)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(
                    items = pages,
                    key = { index, _ -> index }
                ) { index, page ->
                    PdfAnnotationPage(
                        page = page,
                        tool = tool,
                        onTextAdded = { x, y ->
                            pages = pages.mapIndexed { pageIndex, state ->
                                if (pageIndex == index) {
                                    state.copy(
                                        texts = state.texts + PdfTextAnnotation(x, y)
                                    )
                                } else state
                            }
                        },
                        onTextChanged = { textIndex, value ->
                            pages = pages.mapIndexed { pageIndex, state ->
                                if (pageIndex == index) {
                                    state.copy(
                                        texts = state.texts.mapIndexed { i, text ->
                                            if (i == textIndex) text.copy(text = value) else text
                                        }
                                    )
                                } else state
                            }
                        },
                        onTextMoved = { textIndex, x, y ->
                            pages = pages.mapIndexed { pageIndex, state ->
                                if (pageIndex == index) {
                                    state.copy(
                                        texts = state.texts.mapIndexed { i, text ->
                                            if (i == textIndex) text.copy(x = x, y = y) else text
                                        }
                                    )
                                } else state
                            }
                        },
                        onTextResized = { textIndex, width, height ->
                            pages = pages.mapIndexed { pageIndex, state ->
                                if (pageIndex == index) {
                                    state.copy(
                                        texts = state.texts.mapIndexed { i, text ->
                                            if (i == textIndex) text.copy(width = width, height = height) else text
                                        }
                                    )
                                } else state
                            }
                        },
                        onStrokeFinished = { stroke ->
                            pages = pages.mapIndexed { pageIndex, state ->
                                if (pageIndex == index && stroke.size > 1) {
                                    state.copy(strokes = state.strokes + listOf(stroke))
                                } else state
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) }
    )
}

@Composable
private fun PdfAnnotationPage(
    page: PdfPageState,
    tool: PdfAnnotationTool,
    onTextAdded: (Float, Float) -> Unit,
    onTextChanged: (Int, String) -> Unit,
    onTextMoved: (Int, Float, Float) -> Unit,
    onTextResized: (Int, Float, Float) -> Unit,
    onStrokeFinished: (List<Offset>) -> Unit
) {
    var draftStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedTextIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(
                page.bitmap.width.toFloat() /
                    page.bitmap.height.toFloat()
            )
    ) {
            Image(
                bitmap = page.bitmap.asImageBitmap(),
                contentDescription = "PDF page",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(tool) {
                        if (tool == PdfAnnotationTool.TEXT) {
                            detectTapGestures { position ->
                                onTextAdded(
                                    position.x.coerceIn(8f, (size.width - 188f).coerceAtLeast(8f)),
                                    position.y.coerceIn(8f, (size.height - 64f).coerceAtLeast(8f))
                                )
                            }
                        }
                    }
                    .pointerInput(tool) {
                        if (tool == PdfAnnotationTool.DRAW) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    draftStroke = listOf(position)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draftStroke = draftStroke + (draftStroke.lastOrNull()?.plus(dragAmount)
                                        ?: change.position)
                                },
                                onDragEnd = {
                                    onStrokeFinished(draftStroke)
                                    draftStroke = emptyList()
                                },
                                onDragCancel = {
                                    draftStroke = emptyList()
                                }
                            )
                        }
                    }
            ) {
                page.strokes.forEach { stroke ->
                    drawStroke(stroke)
                }
                if (draftStroke.isNotEmpty()) {
                    drawStroke(draftStroke)
                }
            }

            page.texts.forEachIndexed { index, annotation ->
                val density = androidx.compose.ui.platform.LocalDensity.current
                val isSelected = selectedTextIndex == index
                val widthDp = with(density) { annotation.width.toDp() }
                val heightDp = with(density) { annotation.height.toDp() }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                annotation.x.roundToInt(),
                                annotation.y.roundToInt()
                            )
                        }
                        .width(widthDp)
                        .height(heightDp)
                        .border(
                            1.dp,
                            if (isSelected) ScrittoAmber else Color.Transparent,
                            RoundedCornerShape(7.dp)
                        )
                        .background(
                            Color.White.copy(alpha = 0.88f),
                            RoundedCornerShape(7.dp)
                        )
                        .pointerInput(tool, index, annotation.x, annotation.y) {
                            if (tool == PdfAnnotationTool.SELECT) {
                                detectDragGestures(
                                    onDragStart = {
                                        selectedTextIndex = index
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val newX = (annotation.x + dragAmount.x)
                                            .coerceIn(0f, (size.width - annotation.width).coerceAtLeast(0f))
                                        val newY = (annotation.y + dragAmount.y)
                                            .coerceIn(0f, (size.height - annotation.height).coerceAtLeast(0f))
                                        onTextMoved(index, newX, newY)
                                    }
                                )
                            }
                        }
                ) {
                    BasicTextField(
                        value = annotation.text,
                        onValueChange = { onTextChanged(index, it) },
                        readOnly = tool != PdfAnnotationTool.TEXT,
                        textStyle = TextStyle(
                            color = Color.Black,
                            fontSize = 16.sp
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )

                    if (tool == PdfAnnotationTool.SELECT && isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .background(ScrittoAmber, RoundedCornerShape(4.dp))
                                .pointerInput(index, annotation.width, annotation.height) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val newWidth = (annotation.width + dragAmount.x)
                                                .coerceAtLeast(120f)
                                            val newHeight = (annotation.height + dragAmount.y)
                                                .coerceAtLeast(48f)
                                            onTextResized(index, newWidth, newHeight)
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    points: List<Offset>
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point ->
            lineTo(point.x, point.y)
        }
    }
    drawPath(
        path = path,
        color = ScrittoAmber,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    )
}

private fun renderPdfPages(
    context: Context,
    uri: android.net.Uri
): List<PdfPageState> {
    val fileDescriptor: ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()

    return fileDescriptor.use { fd ->
        PdfRenderer(fd).use { renderer ->
            buildList {
                for (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val width = 1000
                        val scale = width.toFloat() / page.width.coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt()
                        val bitmap = Bitmap.createBitmap(
                            width,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        add(PdfPageState(bitmap))
                    }
                }
            }
        }
    }
}
