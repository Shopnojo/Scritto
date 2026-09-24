package com.internship.scritto.screens

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoBorder
import com.internship.scritto.ui.theme.ScrittoCream
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoSurface
import com.internship.scritto.ui.theme.ScrittoTextSecondary
import com.internship.scritto.data.repository.ScrittoStore

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val importedFiles = ScrittoStore.importedFiles

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val fileName = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    )
                } else {
                    null
                }
            } ?: "Imported file"

            ScrittoStore.addImportedFile(
                ScrittoStore.ImportedFile(
                    name = fileName,
                    uri = uri.toString()
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 72.dp,
                bottom = 110.dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Files",
                        color = ScrittoCreamBright,
                        fontSize = 32.sp
                    )

                    Text(
                        text = if (importedFiles.isEmpty()) {
                            "Your imported files"
                        } else {
                            importedFiles.size.toString() +
                                " file" +
                                if (importedFiles.size == 1) "" else "s"
                        },
                        color = ScrittoTextSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Button(
                    onClick = {
                        filePicker.launch(arrayOf("*/*"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileOpen,
                        contentDescription = null
                    )

                    Text(
                        text = "Import",
                        modifier = Modifier.padding(start = 7.dp)
                    )
                }
            }

            if (importedFiles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = ScrittoAmber,
                        modifier = Modifier.size(46.dp)
                    )

                    Text(
                        text = "No files yet",
                        color = ScrittoCreamBright,
                        fontSize = 22.sp,
                        modifier = Modifier.padding(top = 14.dp)
                    )

                    Text(
                        text = "Import any file from your device to get started.",
                        color = ScrittoTextSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = importedFiles,
                        key = { it.uri }
                    ) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(ScrittoSurface)
                                .border(
                                    1.dp,
                                    ScrittoBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 15.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = ScrittoAmber,
                                modifier = Modifier.size(28.dp)
                            )

                            Text(
                                text = file.name,
                                color = ScrittoCream,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(start = 14.dp)
                                    .weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
