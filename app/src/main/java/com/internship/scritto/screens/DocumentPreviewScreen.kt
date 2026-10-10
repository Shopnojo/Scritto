package com.internship.scritto.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.internship.scritto.components.ScrittoMesh
import com.internship.scritto.documents.*
import com.internship.scritto.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import com.internship.scritto.telemetry.Telemetry

@Composable
fun DocumentPreviewScreen(
    name: String,
    uri: String,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onConvert: () -> Unit = {},
    onAskAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val descriptor = remember(name, uri) { DocumentTypes.describe(name, uri) }
    var actionsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(descriptor.uri) { Telemetry.track("document_opened") }

    Box(Modifier.fillMaxSize()) {
        ScrittoMesh(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            DocumentTopBar(descriptor, onBack) { actionsOpen = true }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (descriptor.kind) {
                    DocumentKind.CODE -> CodeDocumentPreview(descriptor)
                    DocumentKind.JSON, DocumentKind.XML ->
                        TextDocumentPreview(descriptor, true)
                    DocumentKind.TEXT, DocumentKind.MARKDOWN, DocumentKind.UNKNOWN ->
                        TextDocumentPreview(descriptor, false)
                    DocumentKind.CSV, DocumentKind.XLSX, DocumentKind.DOCX ->
                        OfficeDocumentPreview(descriptor)
                    DocumentKind.PDF -> PdfDocumentPreview(descriptor)
                    DocumentKind.IMAGE -> ImageDocumentPreview(descriptor)
                }
            }

            DocumentBottomBar(
                descriptor = descriptor,
                onEdit = onEdit,
                onConvert = onConvert,
                onShare = {
                    shareUri(context, descriptor.uri.toUri(), descriptor.name)
                },
                onMore = { actionsOpen = true }
            )
        }
    }

    if (actionsOpen) {
        AlertDialog(
            onDismissRequest = { actionsOpen = false },
            title = { Text("File actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(descriptor.name, color = ScrittoCream)
                    TextButton(
                        onClick = {
                            actionsOpen = false
                            onAskAssistant()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ask Scritto AI about this file", color = ScrittoAmber) }
                    Text(
                        when {
                            descriptor.kind == DocumentKind.CODE ->
                                "Code is view-only. You can share it from Scritto."
                            descriptor.capabilities.canEdit && descriptor.capabilities.canConvert ->
                                "Editing and conversion actions belong to this file type."
                            else -> "This file is preview-only."
                        },
                        color = ScrittoTextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    actionsOpen = false
                    shareUri(context, descriptor.uri.toUri(), descriptor.name)
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { actionsOpen = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun DocumentTopBar(
    descriptor: DocumentDescriptor,
    onBack: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ScrittoCreamBright)
        }
        Icon(
            when (descriptor.kind) {
                DocumentKind.CODE -> Icons.Outlined.Code
                DocumentKind.IMAGE -> Icons.Outlined.Image
                else -> Icons.Outlined.Description
            },
            null,
            tint = ScrittoAmber
        )
        Column(
            Modifier
                .padding(start = 10.dp)
                .weight(1f)
        ) {
            Text(
                descriptor.name,
                color = ScrittoCreamBright,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                descriptor.language ?: descriptor.kind.name.lowercase().replace('_', ' '),
                color = ScrittoTextSecondary,
                fontSize = 12.sp
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Outlined.MoreVert, "More", tint = ScrittoCream)
        }
    }
}

@Composable
private fun DocumentBottomBar(
    descriptor: DocumentDescriptor,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ScrittoSurface.copy(alpha = 0.88f))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (descriptor.capabilities.canEdit) {
                ActionButton("Edit", Icons.Outlined.Edit, onEdit, Modifier.weight(1f))
            }
            if (descriptor.capabilities.canConvert) {
                ActionButton("Convert", Icons.Outlined.Download, onConvert, Modifier.weight(1f))
            }
            ActionButton("Share", Icons.Outlined.Share, onShare, Modifier.weight(1f))
            ActionButton("More", Icons.Outlined.MoreVert, onMore, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Ink colour for text drawn on the white document page. */
private val PaperInk = androidx.compose.ui.graphics.Color(0xFF1B1B1B)

@Composable
private fun TextDocumentPreview(descriptor: DocumentDescriptor, monospace: Boolean) {
    val context = LocalContext.current
    var text by remember(descriptor.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(descriptor.uri) {
        text = withContext(Dispatchers.IO) {
            DocumentReader.readText(context, descriptor.uri.toUri())
        }
    }

    PaperSheet {
        val body = text
        if (body == null) {
            Text("Loading…", color = ScrittoTextSecondary)
        } else {
            Text(
                body,
                color = PaperInk,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun CodeDocumentPreview(descriptor: DocumentDescriptor) {
    val context = LocalContext.current
    var source by remember(descriptor.uri) { mutableStateOf("Loading…") }

    LaunchedEffect(descriptor.uri) {
        source = withContext(Dispatchers.IO) {
            DocumentReader.readText(context, descriptor.uri.toUri())
        }
    }

    val aligned = remember(source, descriptor.language) {
        CodeFormatter.alignForDisplay(source, descriptor.language)
    }
    val highlighted = CodeSyntax.highlight(
        aligned,
        descriptor.language,
        keywordColor = ScrittoAmber,
        stringColor = MaterialTheme.colorScheme.secondary,
        commentColor = ScrittoTextSecondary,
        numberColor = MaterialTheme.colorScheme.tertiary,
        typeColor = ScrittoCreamBright,
        plainColor = ScrittoCream
    )
    val lines = highlighted.text.split("\n")

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp)
        ) {
            lines.indices.forEach { index ->
                Text(
                    (index + 1).toString(),
                    color = ScrittoTextSecondary.copy(alpha = 0.62f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 20.sp
                )
            }
        }
        Text(
            highlighted,
            color = ScrittoCream,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/** Word, spreadsheet and CSV files open on a white page, read-only. Edit switches to the editor. */
@Composable
private fun OfficeDocumentPreview(descriptor: DocumentDescriptor) {
    if (descriptor.kind == DocumentKind.DOCX) {
        DocxPreview(descriptor)
    } else {
        SheetPreview(descriptor)
    }
}

@Composable
private fun DocxPreview(descriptor: DocumentDescriptor) {
    val context = LocalContext.current
    var page by remember(descriptor.uri) { mutableStateOf<DocxPage?>(null) }
    var failure by remember(descriptor.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(descriptor.uri) {
        runCatching { withContext(Dispatchers.IO) { DocumentEngine.readDocxPage(context, descriptor) } }
            .onSuccess { page = it }
            .onFailure { failure = DocumentEngine.unreadableMessage(descriptor.name) }
    }

    val loaded = page
    if (loaded == null) {
        Text(
            failure ?: "Loading document…",
            color = ScrittoTextSecondary,
            modifier = Modifier.padding(20.dp)
        )
    } else {
        PaperSheet {
            DocxPageView(
                page = loaded,
                paragraphs = loaded.paragraphs,
                tables = loaded.tables,
                editable = false
            )
        }
    }
}

@Composable
private fun SheetPreview(descriptor: DocumentDescriptor) {
    val context = LocalContext.current
    var sheets by remember(descriptor.uri) { mutableStateOf<List<SheetData>?>(null) }
    var selected by remember(descriptor.uri) { mutableStateOf(0) }
    var failure by remember(descriptor.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(descriptor.uri) {
        runCatching { withContext(Dispatchers.IO) { DocumentEngine.readSheets(context, descriptor) } }
            .onSuccess { sheets = it }
            .onFailure { failure = DocumentEngine.unreadableMessage(descriptor.name) }
    }

    val loaded = sheets
    if (loaded == null) {
        Text(
            failure ?: "Loading document…",
            color = ScrittoTextSecondary,
            modifier = Modifier.padding(20.dp)
        )
    } else {
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            SheetGridView(
                sheets = loaded,
                selected = selected,
                onSelect = { selected = it },
                editable = false
            )
        }
    }
}

@Composable
private fun PdfDocumentPreview(descriptor: DocumentDescriptor) {
    val context = LocalContext.current
    var pageCount by remember(descriptor.uri) { mutableStateOf(0) }
    var failure by remember(descriptor.uri) { mutableStateOf<String?>(null) }
    var fd by remember(descriptor.uri) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember(descriptor.uri) { mutableStateOf<PdfRenderer?>(null) }
    // PdfRenderer can have only one page open at a time, so page renders take turns.
    val renderLock = remember(descriptor.uri) { Mutex() }

    LaunchedEffect(descriptor.uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val fileDescriptor = context.contentResolver.openFileDescriptor(descriptor.uri.toUri(), "r")
                    ?: error("missing")
                val pdf = PdfRenderer(fileDescriptor)
                fd = fileDescriptor
                renderer = pdf
                pageCount = pdf.pageCount
            }.onFailure {
                failure = DocumentEngine.unreadableMessage(descriptor.name)
            }
        }
    }

    DisposableEffect(descriptor.uri) {
        onDispose {
            renderer?.close()
            fd?.close()
        }
    }

    val failed = failure
    if (failed != null) {
        Text(
            failed,
            color = androidx.compose.ui.graphics.Color(0xFF6B6B6B),
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.White)
                .padding(20.dp)
        )
        return
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pageCount) { pageIndex ->
            PdfPage(renderer, renderLock, pageIndex)
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer?, renderLock: Mutex, pageIndex: Int) {
    var bitmap by remember(pageIndex, renderer) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(renderer, pageIndex) {
        val source = renderer ?: return@LaunchedEffect
        val rendered = withContext(Dispatchers.IO) {
            renderLock.withLock {
                runCatching {
                    source.openPage(pageIndex).use { page ->
                        val width = 1000
                        val height = (page.height * width.toFloat() / page.width.coerceAtLeast(1)).toInt()
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
                            target.eraseColor(android.graphics.Color.WHITE)
                            page.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }.getOrNull()
            }
        }
        bitmap = rendered
    }

    val loaded = bitmap
    if (loaded == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(androidx.compose.ui.graphics.Color(0xFFF4F4F4)),
            contentAlignment = Alignment.Center
        ) {
            Text("Page ${pageIndex + 1}…", color = androidx.compose.ui.graphics.Color(0xFF9A9A9A), fontSize = 12.sp)
        }
    } else {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "PDF page " + (pageIndex + 1),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(3.dp)
                .background(androidx.compose.ui.graphics.Color.White)
        )
    }
}

@Composable
private fun ImageDocumentPreview(descriptor: DocumentDescriptor) {
    val context = LocalContext.current
    var bitmap by remember(descriptor.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(descriptor.uri) {
        bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(descriptor.uri.toUri())?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                descriptor.name,
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        } ?: Text("Unable to preview this image.", color = ScrittoTextSecondary)
    }
}

private fun shareUri(context: Context, uri: Uri, name: String) {
    context.startActivity(DocumentShare.shareIntent(context, uri, name))
}
