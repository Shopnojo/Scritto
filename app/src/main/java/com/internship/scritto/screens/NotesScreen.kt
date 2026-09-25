package com.internship.scritto.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.data.model.Note
import com.internship.scritto.data.repository.ScrittoStore

@Composable
fun NotesScreen(
    onNoteSelected: (String) -> Unit
) {
    val notes = ScrittoStore.notes

    var noteToDelete by remember {
        mutableStateOf<Note?>(null)
    }

    var noteForActions by remember {
        mutableStateOf<Note?>(null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
             ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 72.dp,
                    bottom = 110.dp
                )
        ) {

            Text(
                text = "Notes",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = if (notes.isEmpty()) {
                    "Your thoughts, ideas and everything in between."
                } else {
                    "${notes.size} note${if (notes.size == 1) "" else "s"}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            if (notes.isEmpty()) {

                EmptyNotesState()

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = notes,
                        key = { it.id }
                    ) { note ->

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut()
                        ) {
                            NoteRow(
                                note = note,
                                onClick = {
                                    onNoteSelected(note.id)
                                },
                                onDelete = {
                                    noteToDelete = note
                                },
                                onLongPress = {
                                    noteForActions = note
                                }
                            )
                        }
                    }
                }
            }
        }

        if (noteForActions != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.background.copy(
                            alpha = 0.72f
                        )
                    )
                    .combinedClickable(
                        onClick = {
                            noteForActions = null
                        },
                        onLongClick = {}
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 24.dp
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = noteForActions?.title?.ifBlank {
                            "Untitled note"
                        } ?: "Note",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = if (noteForActions?.isPinned == true) {
                            "Pinned to Home"
                        } else {
                            "Note actions"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                noteForActions?.let { note ->
                                    ScrittoStore.setPinned(
                                        note.id,
                                        !note.isPinned
                                    )
                                }
                                noteForActions = null
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.size(12.dp))

                        Text(
                            text = if (noteForActions?.isPinned == true) {
                                "Unpin from Home"
                            } else {
                                "Pin to Home"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                noteToDelete = noteForActions
                                noteForActions = null
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Simple delete confirmation
        if (noteToDelete != null) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.background.copy(
                            alpha = 0.72f
                        )
                    )
                    .clickable {
                        noteToDelete = null
                    },
                contentAlignment = Alignment.Center
            ) {

                Column(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            MaterialTheme.colorScheme.surface
                        )
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    Text(
                        text = "Delete note?",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "This note will be removed from Scritto.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    noteToDelete = null
                                }
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 12.dp
                                )
                        )

                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    noteToDelete?.let {
                                        ScrittoStore.deleteNote(it.id)
                                    }

                                    noteToDelete = null
                                }
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 12.dp
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                MaterialTheme.colorScheme.surface.copy(
                    alpha = 0.88f
                )
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.14f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▤",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp
            )
        }

        Spacer(
            modifier = Modifier.size(14.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Text(
                    text = note.title.ifBlank {
                        "Untitled note"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = note.content.ifBlank {
                    "No content"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 2
            )
        }

        Text(
            text = "•••",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    onDelete()
                }
                .padding(10.dp)
        )
    }
}

@Composable
private fun EmptyNotesState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "▤",
                color = MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.75f
                ),
                fontSize = 38.sp
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "No notes yet",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = "Create your first note from the + button.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}