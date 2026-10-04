package com.internship.scritto.documents

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Hands an imported file to other apps through the system share sheet. */
object DocumentShare {

    /**
     * Files Scritto created itself (edited PDFs) live in app storage and are shared through a
     * FileProvider. Imported files already are content:// URIs with a read grant.
     */
    fun shareIntent(context: Context, uri: Uri, name: String): Intent {
        val shareable = if (uri.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                File(requireNotNull(uri.path) { "Missing file path" })
            )
        } else {
            uri
        }

        val mime = context.contentResolver.getType(shareable)
            ?: DocumentTypes.mimeTypeForExtension(name.substringAfterLast('.', ""))

        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareable)
                // ClipData carries the grant to the receiving app; EXTRA_STREAM alone does not on every launcher.
                clipData = ClipData.newUri(context.contentResolver, name, shareable)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share $name"
        )
    }
}
