package com.internship.scritto.data.model

data class NoteSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false
)

/** An imported file the note refers to. Stored by uri, so the file stays in Files. */
data class NoteFile(
    val name: String,
    val uri: String
)

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val updatedAt: Long,
    val spans: List<NoteSpan> = emptyList(),
    val textAlign: String = "left",
    val isPinned: Boolean = false,
    val files: List<NoteFile> = emptyList()
)
