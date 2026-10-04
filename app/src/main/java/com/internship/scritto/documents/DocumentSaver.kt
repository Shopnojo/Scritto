package com.internship.scritto.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saves a document from an editor screen.
 *
 * - Edits go back to the original file when the app holds write access to it.
 * - Otherwise (or for conversions) the system Save As dialog asks for a destination.
 *
 * [build] runs on a background thread, so heavy rendering never blocks the UI.
 * The result is reported through the onStatus callback passed to [rememberDocumentSaver].
 */
class DocumentSaver internal constructor(
    private val request: (suggestedName: String, allowInPlace: Boolean, build: () -> ByteArray) -> Unit
) {
    fun save(
        suggestedName: String,
        allowInPlace: Boolean = true,
        build: () -> ByteArray
    ) = request(suggestedName, allowInPlace, build)
}

@Composable
fun rememberDocumentSaver(
    uri: Uri?,
    onStatus: (String) -> Unit
): DocumentSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnStatus by rememberUpdatedState(onStatus)

    var pending by remember { mutableStateOf<PendingSave?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { target ->
        val request = pending
        pending = null
        if (target != null && request != null) {
            scope.launch {
                val message = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = request.build()
                        context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                            ?: error("Unable to open the destination")
                    }.fold(
                        onSuccess = {
                            if (request.asCopy) "Saved as a new copy" else "Saved"
                        },
                        onFailure = {
                            "Could not save: " + (it.message ?: "unknown error")
                        }
                    )
                }
                currentOnStatus(message)
            }
        }
    }

    return remember(uri) {
        DocumentSaver { suggestedName, allowInPlace, build ->
            val writable = uri != null && DocumentEngine.canWriteInPlace(context, uri)

            if (allowInPlace && uri != null && writable) {
                scope.launch {
                    val message = withContext(Dispatchers.IO) {
                        runCatching {
                            DocumentEngine.writeInPlace(context, uri, build())
                        }.fold(
                            onSuccess = { "Saved" },
                            onFailure = { "Could not save: " + (it.message ?: "unknown error") }
                        )
                    }
                    currentOnStatus(message)
                }
            } else {
                pending = PendingSave(build = build, asCopy = allowInPlace)
                launcher.launch(suggestedName)
            }
        }
    }
}

private class PendingSave(
    val build: () -> ByteArray,
    /** True when an in-place save was wanted but the file is read-only for the app. */
    val asCopy: Boolean
)
