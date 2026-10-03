package com.internship.scritto.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import java.io.OutputStream
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.internship.scritto.documents.DocumentDescriptor
import com.internship.scritto.documents.DocumentEngine
import com.internship.scritto.documents.DocumentKind
import com.internship.scritto.documents.PdfAnnotationWorkspace
import com.internship.scritto.documents.DocxWorkspace
import com.internship.scritto.documents.SpreadsheetWorkspace
import com.internship.scritto.documents.ImageWorkspace
import com.internship.scritto.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DocumentEditorScreen(
    name: String,
    uri: String,
    convertMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val descriptor = remember(name, uri) {
        com.internship.scritto.documents.DocumentTypes.describe(name, uri)
    }

    var text by remember(descriptor.uri) { mutableStateOf("") }
    var loaded by remember(descriptor.uri) { mutableStateOf(false) }
    var imageRotation by remember(descriptor.uri) { mutableStateOf(0f) }
    var flipH by remember(descriptor.uri) { mutableStateOf(false) }
    var flipV by remember(descriptor.uri) { mutableStateOf(false) }
    var cropRatio by remember(descriptor.uri) { mutableStateOf("Original") }
    var customRatio by remember(descriptor.uri) { mutableStateOf("") }
    var conversionOpen by remember { mutableStateOf(convertMode) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingOperation by remember { mutableStateOf<((OutputStream) -> Unit)?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { target ->
        target?.let { destination ->
            val operation = pendingOperation
            pendingOperation = null
            if (operation != null) {
                runCatching {
                    context.contentResolver.openOutputStream(destination)?.use { output -> operation(output) }
                    status = "Saved"
                }.onFailure {
                    status = "Could not save: " + (it.message ?: "unknown error")
                }
            }
        }
    }

    LaunchedEffect(descriptor.uri, descriptor.kind) {
        if (descriptor.kind != DocumentKind.IMAGE) {
            text = withContext(Dispatchers.IO) {
                DocumentEngine.readEditableText(context, descriptor)
            }
        }
        loaded = true
    }

    fun saveEdited() {
        val extension = name.substringAfterLast('.', "txt")
        pendingOperation = { output ->
            DocumentEngine.saveEditedText(context, descriptor, text, output)
        }
        saveLauncher.launch(name.substringBeforeLast('.', name) + "_edited." + extension)
    }

    fun convertTo(extension: String) {
        pendingOperation = { output ->
            DocumentEngine.convert(context, descriptor, extension, output)
        }
        saveLauncher.launch(name.substringBeforeLast('.', name) + "." + extension)
    }

    fun saveImage() {
        val ratio = cropRatioValue(cropRatio, customRatio)
        pendingOperation = { output ->
            DocumentEngine.transformImage(
                    context = context,
                    descriptor = descriptor,
                    output = output,
                    crop = ratio?.let { centerCrop(context, descriptor.uri.toUri(), it.first, it.second) },
                    rotation = imageRotation,
                    flipHorizontal = flipH,
                    flipVertical = flipV,
                    format = Bitmap.CompressFormat.PNG
            )
        }
        saveLauncher.launch(name.substringBeforeLast('.', name) + "_edited.png")
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        if (convertMode) "Convert" else "Edit",
                        color = ScrittoCreamBright,
                        fontSize = 22.sp
                    )
                    Text(name, color = ScrittoTextSecondary, fontSize = 12.sp, maxLines = 1)
                }
                if (!convertMode && descriptor.kind != DocumentKind.PDF && descriptor.kind != DocumentKind.IMAGE && descriptor.kind != DocumentKind.DOCX && descriptor.kind != DocumentKind.CSV && descriptor.kind != DocumentKind.XLSX) {
                    IconButton(onClick = { saveEdited() }) {
                        Icon(Icons.Outlined.Save, "Save", tint = ScrittoAmber)
                    }
                }
            }

            when {
                descriptor.kind == DocumentKind.IMAGE -> ImageWorkspace(
                    context = context,
                    descriptor = descriptor,
                    rotation = imageRotation,
                    flipH = flipH,
                    flipV = flipV,
                    cropRatio = cropRatio,
                    customRatio = customRatio,
                    onRotate = { imageRotation = (imageRotation + 90f) % 360f },
                    onFlipH = { flipH = !flipH },
                    onFlipV = { flipV = !flipV },
                    onCropRatio = { cropRatio = it },
                    onCustomRatio = { customRatio = it },
                    onSave = { saveImage() },
                    modifier = Modifier.weight(1f)
                )
                descriptor.kind == DocumentKind.PDF && !convertMode -> PdfAnnotationWorkspace(
                    modifier = Modifier.weight(1f),
                    descriptor = descriptor
                )
                descriptor.kind == DocumentKind.DOCX && !convertMode -> DocxWorkspace(
                    context = context,
                    descriptor = descriptor,
                    modifier = Modifier.weight(1f)
                )
                (descriptor.kind == DocumentKind.CSV || descriptor.kind == DocumentKind.XLSX) && !convertMode -> SpreadsheetWorkspace(
                    context = context,
                    descriptor = descriptor,
                    modifier = Modifier.weight(1f)
                )
                else -> TextEditBody(
                    modifier = Modifier.weight(1f),
                    text = text,
                    loaded = loaded,
                    onText = { text = it }
                )
            }

            Spacer(Modifier.weight(1f))

            if (status != null) {
                Text(
                    status.orEmpty(),
                    color = ScrittoTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

        }
    }

    if (conversionOpen) {
        ConversionDialog(
            descriptor = descriptor,
            onDismiss = {
                conversionOpen = false
                if (convertMode) onBack()
            },
            onConvert = {
                conversionOpen = false
                convertTo(it)
            }
        )
    }
}

@Composable
private fun TextEditBody(
    modifier: Modifier,
    text: String,
    loaded: Boolean,
    onText: (String) -> Unit
) {
    Box(
        modifier.fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(ScrittoSurface.copy(alpha = 0.58f), RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        if (!loaded) {
            Text("Loading…", color = ScrittoTextSecondary)
        } else {
            TextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                textStyle = LocalTextStyle.current.copy(
                    color = ScrittoCream,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            )
        }
    }
}

@Composable
private fun ConversionDialog(
    descriptor: DocumentDescriptor,
    onDismiss: () -> Unit,
    onConvert: (String) -> Unit
) {
    val options = when (descriptor.kind) {
        DocumentKind.CSV -> listOf("xlsx", "txt")
        DocumentKind.XLSX -> listOf("csv", "txt")
        DocumentKind.DOCX -> listOf("pdf", "txt")
        DocumentKind.PDF -> listOf("docx", "txt")
        DocumentKind.IMAGE -> listOf("png", "jpg", "webp")
        else -> emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    OutlinedButton(
                        onClick = { onConvert(option) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export as ." + option) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun cropRatioValue(value: String, custom: String): Pair<Float, Float>? {
    if (value == "Original") return null
    if (value == "1:1") return 1f to 1f
    if (value == "4:3") return 4f to 3f
    if (value == "16:9") return 16f to 9f
    if (value == "9:16") return 9f to 16f
    val parts = custom.split(':')
    val w = parts.getOrNull(0)?.toFloatOrNull()
    val h = parts.getOrNull(1)?.toFloatOrNull()
    return if (w != null && h != null && w > 0f && h > 0f) w to h else null
}

private fun centerCrop(
    context: android.content.Context,
    uri: Uri,
    ratioW: Float,
    ratioH: Float
): DocumentEngine.CropRect {
    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, options)
    }
    val width = options.outWidth
    val height = options.outHeight
    if (width <= 0 || height <= 0) return DocumentEngine.CropRect(0, 0, 1, 1)

    val target = ratioW / ratioH
    val source = width.toFloat() / height.toFloat()
    return if (source > target) {
        val cropWidth = (height * target).toInt().coerceAtLeast(1)
        val left = (width - cropWidth) / 2
        DocumentEngine.CropRect(left, 0, left + cropWidth, height)
    } else {
        val cropHeight = (width / target).toInt().coerceAtLeast(1)
        val top = (height - cropHeight) / 2
        DocumentEngine.CropRect(0, top, width, top + cropHeight)
    }
}
