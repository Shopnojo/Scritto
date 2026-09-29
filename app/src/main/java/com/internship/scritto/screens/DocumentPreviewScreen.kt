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
import androidx.compose.ui.graphics.asImageBitmap
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

@Composable
fun DocumentPreviewScreen(
    name: String,
    uri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val descriptor = remember(name, uri) { DocumentTypes.describe(name, uri) }
    var actionsOpen by remember { mutableStateOf(false) }

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
                onEdit = { actionsOpen = true },
                onConvert = { actionsOpen = true },
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun TextDocumentPreview(descriptor: DocumentDescriptor, monospace: Boolean) {
    val context = LocalContext.current
    var text by remember(descriptor.uri) { mutableStateOf("Loading…") }

    LaunchedEffect(descriptor.uri) {
        text = withContext(Dispatchers.IO) {
            DocumentReader.readText(context, descriptor.uri.toUri())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .background(ScrittoSurface.copy(alpha = 0.52f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(
            text,
            color = ScrittoCream,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
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
            .background(ScrittoSurface.copy(alpha = 0.52f), RoundedCornerShape(20.dp))
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

@Composable
private fun OfficeDocumentPreview(descriptor: DocumentDescriptor) {
    val context = LocalContext.current
    var text by remember(descriptor.uri) { mutableStateOf("Loading document…") }

    LaunchedEffect(descriptor.uri) {
        text = withContext(Dispatchers.IO) {
            DocumentReader.readOfficeText(context, descriptor.uri.toUri(), descriptor.kind)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .background(ScrittoSurface.copy(alpha = 0.52f), RoundedCornerShape(20.dp))
            .padding(18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text.ifBlank { "This document contains no directly previewable text." },
            color = ScrittoCream,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun PdfDocumentPreview(descriptor: DocumentDescriptor) {
    val context = LocalContext.current
    var pageCount by remember(descriptor.uri) { mutableStateOf(0) }
    var fd by remember(descriptor.uri) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember(descriptor.uri) { mutableStateOf<PdfRenderer?>(null) }

    LaunchedEffect(descriptor.uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val fileDescriptor =
                    context.contentResolver.openFileDescriptor(descriptor.uri.toUri(), "r")
                        ?: return@runCatching
                val pdf = PdfRenderer(fileDescriptor)
                fd = fileDescriptor
                renderer = pdf
                pageCount = pdf.pageCount
            }
        }
    }

    DisposableEffect(descriptor.uri) {
        onDispose {
            renderer?.close()
            fd?.close()
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(pageCount) { pageIndex ->
            PdfPage(renderer, pageIndex)
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer?, pageIndex: Int) {
    var bitmap by remember(pageIndex, renderer) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(renderer, pageIndex) {
        withContext(Dispatchers.IO) {
            runCatching {
                renderer?.openPage(pageIndex)?.use { page ->
                    val width = 900
                    val scale = width.toFloat() / page.width.coerceAtLeast(1)
                    val height = (page.height * scale).toInt()
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = it
                    }
                }
            }
        }
    }

    bitmap?.let {
        Image(
            it.asImageBitmap(),
            "PDF page " + (pageIndex + 1),
            Modifier
                .fillMaxWidth()
                .background(ScrittoCream, RoundedCornerShape(10.dp))
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
            .padding(horizontal = 20.dp)
            .background(ScrittoSurface.copy(alpha = 0.52f), RoundedCornerShape(20.dp)),
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
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share " + name
        )
    )
}
