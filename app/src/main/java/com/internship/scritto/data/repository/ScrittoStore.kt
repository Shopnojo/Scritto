package com.internship.scritto.data.repository

import androidx.compose.runtime.mutableStateListOf
import com.internship.scritto.data.model.Note
import java.util.UUID

object ScrittoStore {

    private val _notes = mutableStateListOf<Note>()

    val notes: List<Note>
        get() = _notes

    fun createNote(): Note {
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = "",
            content = "",
            updatedAt = System.currentTimeMillis()
        )

        _notes.add(0, note)

        return note
    }

    fun getNote(id: String): Note? {
        return _notes.firstOrNull {
            it.id == id
        }
    }

    fun updateNote(
        id: String,
        title: String,
        content: String
    ) {
        val index = _notes.indexOfFirst {
            it.id == id
        }

        if (index == -1) return

        _notes[index] = _notes[index].copy(
            title = title,
            content = content,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun deleteNote(id: String) {
        _notes.removeAll {
            it.id == id
        }
    }
}