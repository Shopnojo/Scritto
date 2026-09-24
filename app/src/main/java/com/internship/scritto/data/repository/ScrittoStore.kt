package com.internship.scritto.data.repository

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.internship.scritto.data.model.Note
import com.internship.scritto.data.model.NoteSpan
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ScrittoStore {

    private const val PREFS_NAME = "scritto_store"
    private const val NOTES_KEY = "notes"

    private val _notes = mutableStateListOf<Note>()
    val notes: List<Note>
        get() = _notes

    private var initialized = false
    private var preferences: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        if (initialized) return

        preferences = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        _notes.clear()
        _notes.addAll(loadNotes())
        initialized = true
    }

    fun createNote(): Note {
        checkInitialized()

        val note = Note(
            id = UUID.randomUUID().toString(),
            title = "",
            content = "",
            updatedAt = System.currentTimeMillis()
        )

        _notes.add(0, note)
        persist()

        return note
    }

    fun getNote(id: String): Note? {
        return _notes.firstOrNull { it.id == id }
    }

    fun updateNote(
        id: String,
        title: String,
        content: String,
        spans: List<NoteSpan> = emptyList(),
        textAlign: String = "left"
    ) {
        checkInitialized()

        val index = _notes.indexOfFirst { it.id == id }
        if (index == -1) return

        _notes[index] = _notes[index].copy(
            title = title,
            content = content,
            spans = spans,
            textAlign = textAlign,
            updatedAt = System.currentTimeMillis()
        )

        persist()
    }

    fun setPinned(id: String, pinned: Boolean) {
        checkInitialized()

        val index = _notes.indexOfFirst { it.id == id }
        if (index == -1) return

        if (pinned) {
            _notes.replaceAll { note ->
                if (note.id == id) {
                    note.copy(
                        isPinned = true,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    note.copy(isPinned = false)
                }
            }
        } else {
            _notes[index] = _notes[index].copy(
                isPinned = false,
                updatedAt = System.currentTimeMillis()
            )
        }

        persist()
    }

    fun deleteNote(id: String) {
        checkInitialized()

        _notes.removeAll { it.id == id }
        persist()
    }

    private fun persist() {
        preferences
            ?.edit()
            ?.putString(
                NOTES_KEY,
                JSONArray().apply {
                    _notes.forEach { note ->
                        put(
                            JSONObject().apply {
                                put("id", note.id)
                                put("title", note.title)
                                put("content", note.content)
                                put("updatedAt", note.updatedAt)
                                put("textAlign", note.textAlign)
                                put("isPinned", note.isPinned)

                                put(
                                    "spans",
                                    JSONArray().apply {
                                        note.spans.forEach { span ->
                                            put(
                                                JSONObject().apply {
                                                    put("start", span.start)
                                                    put("end", span.end)
                                                    put("bold", span.bold)
                                                    put("italic", span.italic)
                                                    put("underline", span.underline)
                                                    put("strike", span.strike)
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }.toString()
            )
            ?.apply()
    }

    private fun loadNotes(): List<Note> {
        val raw = preferences?.getString(NOTES_KEY, null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val spansArray = item.optJSONArray("spans") ?: JSONArray()

                    val spans = buildList {
                        for (spanIndex in 0 until spansArray.length()) {
                            val span = spansArray.getJSONObject(spanIndex)

                            add(
                                NoteSpan(
                                    start = span.optInt("start"),
                                    end = span.optInt("end"),
                                    bold = span.optBoolean("bold"),
                                    italic = span.optBoolean("italic"),
                                    underline = span.optBoolean("underline"),
                                    strike = span.optBoolean("strike")
                                )
                            )
                        }
                    }

                    add(
                        Note(
                            id = item.getString("id"),
                            title = item.optString("title"),
                            content = item.optString("content"),
                            updatedAt = item.optLong("updatedAt"),
                            spans = spans,
                            textAlign = item.optString("textAlign", "left"),
                            isPinned = item.optBoolean("isPinned", false)
                        )
                    )
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun checkInitialized() {
        check(initialized) {
            "ScrittoStore.initialize(context) must be called before using the store."
        }
    }
}
