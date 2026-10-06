package com.internship.scritto.documents

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.internship.scritto.ui.theme.*

@Composable
fun ImageWorkspace(
    context: Context,
    descriptor: DocumentDescriptor,
    rotation: Float,
    flipH: Boolean,
    flipV: Boolean,
    cropRatio: String,
    customRatio: String,
    onRotate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onCropRatio: (String) -> Unit,
    onCustomRatio: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(descriptor.uri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(descriptor.uri) { mutableStateOf(true) }
    val previewRatio = remember(cropRatio, customRatio) {
        when (cropRatio) {
            "1:1" -> 1f
            "4:3" -> 4f / 3f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "Original" -> null
            else -> customRatio.split(":").let { parts ->
                if (parts.size == 2) {
                    val width = parts[0].toFloatOrNull()
                    val height = parts[1].toFloatOrNull()
                    if (width != null && height != null && width > 0f && height > 0f) width / height else null
                } else null
            }
        }
    }

    LaunchedEffect(descriptor.uri) {
        loading = true
        bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(descriptor.uri.toUri())?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(ScrittoSurface.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> Text("Loading image…", color = ScrittoTextSecondary)
                bitmap == null -> Text("Unable to preview image", color = ScrittoTextSecondary)
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = if (previewRatio != null) {
                            Modifier
                                .fillMaxSize()
                                .aspectRatio(
                                    previewRatio,
                                    matchHeightConstraintsFirst = true
                                )
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = descriptor.name,
                            contentScale = if (previewRatio != null) ContentScale.Crop else ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .graphicsLayer {
                                    rotationZ = rotation
                                    scaleX = if (flipH) -1f else 1f
                                    scaleY = if (flipV) -1f else 1f
                                }
                        )
                    }
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Crop", color = ScrittoCreamBright, fontSize = 17.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Original", "1:1", "4:3", "16:9", "9:16").forEach { ratio ->
                    FilterChip(
                        selected = cropRatio == ratio,
                        onClick = { onCropRatio(ratio) },
                        label = { Text(ratio) }
                    )
                }
            }
            OutlinedTextField(
                value = customRatio,
                onValueChange = onCustomRatio,
                label = { Text("Custom W:H") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onRotate) {
                    Icon(Icons.Outlined.Rotate90DegreesCw, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Rotate " + rotation.toInt() + "°")
                }
                OutlinedButton(onClick = onFlipH) {
                    Icon(Icons.Outlined.Flip, null)
                    Spacer(Modifier.width(5.dp))
                    Text(if (flipH) "Flip H ✓" else "Flip H")
                }
                OutlinedButton(onClick = onFlipV) {
                    Icon(Icons.Outlined.Flip, null)
                    Spacer(Modifier.width(5.dp))
                    Text(if (flipV) "Flip V ✓" else "Flip V")
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Save, null)
                Spacer(Modifier.width(6.dp))
                Text("Save edited image")
            }
        }
    }
}
