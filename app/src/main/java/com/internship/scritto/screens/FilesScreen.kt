package com.internship.scritto.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoBorder
import com.internship.scritto.ui.theme.ScrittoCream
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoSurface
import com.internship.scritto.ui.theme.ScrittoTextSecondary

@Composable
fun FilesScreen() {
    var importedFileName by remember {
        mutableStateOf<String?>(null)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importedFileName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.ifBlank { "Imported file" }
                ?: "Imported file"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 54.dp,
                bottom = 112.dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = ScrittoAmber,
                modifier = Modifier.size(46.dp)
            )

            Text(
                text = if (importedFileName == null) "No files yet" else "Imported",
                color = ScrittoCreamBright,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 14.dp)
            )

            if (importedFileName == null) {
                Text(
                    text = "Import any file from your device to get started.",
                    color = ScrittoTextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ScrittoSurface)
                        .border(
                            1.dp,
                            ScrittoBorder,
                            RoundedCornerShape(18.dp)
                        )
                        .padding(
                            horizontal = 18.dp,
                            vertical = 16.dp
                        )
                ) {
                    Text(
                        text = importedFileName ?: "Imported file",
                        color = ScrittoCream,
                        fontSize = 15.sp
                    )
                }
            }

            Button(
                onClick = {
                    filePicker.launch(arrayOf("*/*"))
                },
                modifier = Modifier.padding(top = 22.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null
                )

                Text(
                    text = if (importedFileName == null) {
                        "Import file"
                    } else {
                        "Import another"
                    },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
