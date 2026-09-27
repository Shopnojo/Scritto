package com.internship.scritto.ai

import android.content.Context
import com.internship.scritto.data.model.Note
import com.internship.scritto.data.model.NoteSpan
import com.internship.scritto.data.model.ScheduleEvent
import com.internship.scritto.data.model.Task
import com.internship.scritto.data.repository.ScrittoStore
import com.internship.scritto.notes.NoteRichText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Gives the assistant hands: everything it can read or change in Scritto goes
 * through here, and every change goes through [ScrittoStore] so screens,
 * reminders and alarms stay consistent with what the assistant did.
 */
class ScrittoToolbox(
    context: Context,
    private val files: FileContentReader
) : AssistantToolbox {

    private val appContext = context.applicationContext

    override val declarations: JSONArray get() = AssistantToolSpec.declarations

    // ------------------------------------------------------------------
    // Snapshot
    // ------------------------------------------------------------------

    override fun workspaceSnapshot(): String {
        val now = System.currentTimeMillis()
        val out = StringBuilder()

        val notes = ScrittoStore.notes
            .sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt })

        out.appendLine("NOTES (${notes.size}):")
        notes.take(MAX_SNAPSHOT_ITEMS).forEach { note ->
            out.appendLine(
                "- [${shortId(note.id)}] \"${note.title.ifBlank { "Untitled" }}\"" +
                    (if (note.isPinned) " (pinned)" else "") +
                    " — " + note.content.replace('\n', ' ').take(70)
            )
        }
        if (notes.size > MAX_SNAPSHOT_ITEMS) out.appendLine("- …and ${notes.size - MAX_SNAPSHOT_ITEMS} more (use list_notes / search_workspace)")

        val tasks = ScrittoStore.tasks.filterNot { it.completed }.sortedBy { it.dueAt }
        out.appendLine("\nOPEN TASKS (${tasks.size}):")
        tasks.take(MAX_SNAPSHOT_ITEMS).forEach { task ->
            out.appendLine(
                "- [${shortId(task.id)}] \"${task.title}\" due ${AssistantTime.iso(task.dueAt)}" +
                    " ${task.priority.name}" + if (task.dueAt < now) " OVERDUE" else ""
            )
        }

        val horizon = now + 14 * DAY
        val events = ScrittoStore.events
            .filter { it.endAt > AssistantTime.startOfDay(now) && it.startAt < horizon }
            .sortedBy { it.startAt }
        out.appendLine("\nSCHEDULE, NEXT 14 DAYS (${events.size}):")
        events.take(MAX_SNAPSHOT_ITEMS).forEach { event ->
            out.appendLine(
                "- [${shortId(event.id)}] \"${event.title}\" ${event.type.name}" +
                    " ${AssistantTime.iso(event.startAt)}–${AssistantTime.humanTime(event.endAt)}" +
                    if (event.location.isNotBlank()) " @ ${event.location}" else ""
            )
        }

        out.appendLine("\nFILES (${ScrittoStore.importedFiles.size}):")
        ScrittoStore.importedFiles.take(MAX_SNAPSHOT_ITEMS).forEach { file ->
            out.appendLine("- ${file.name}")
        }

        return out.toString().trimEnd()
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    override suspend fun execute(name: String, args: JSONObject): ToolOutcome {
        return try {
            when (name) {
                "read_file" -> readFile(args)
                else -> withContext(Dispatchers.Main.immediate) { executeOnMain(name, args) }
            }
        } catch (e: IllegalArgumentException) {
            ToolOutcome.error(e.message ?: "Invalid request.")
        } catch (e: IllegalStateException) {
            ToolOutcome.error(e.message ?: "Scritto isn't ready yet.")
        }
    }

    private fun executeOnMain(name: String, args: JSONObject): ToolOutcome = when (name) {
        "search_workspace" -> search(args)
        "list_notes" -> listNotes()
        "read_note" -> readNote(args)
        "create_note" -> createNote(args)
        "update_note" -> updateNote(args)
        "pin_note" -> pinNote(args)
        "delete_note" -> deleteNote(args)
        "list_tasks" -> listTasks(args)
        "create_task" -> createTask(args)
        "update_task" -> updateTask(args)
        "complete_task" -> completeTask(args)
        "delete_task" -> deleteTask(args)
        "list_events" -> listEvents(args)
        "get_day_agenda" -> dayAgenda(args)
        "create_event" -> createEvent(args)
        "update_event" -> updateEvent(args)
        "delete_event" -> deleteEvent(args)
        "list_files" -> listFiles()
        "navigate" -> navigate(args)
        else -> ToolOutcome.error("Unknown tool '$name'. Use only the tools you were given.")
    }

    // ------------------------------------------------------------------
    // Notes
    // ------------------------------------------------------------------

    private fun listNotes(): ToolOutcome {
        val notes = ScrittoStore.notes
            .sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt })

        return ToolOutcome.ok(
            "count" to notes.size,
            "notes" to JSONArray(notes.take(50).map { noteJson(it, preview = true) })
        )
    }

    private fun readNote(args: JSONObject): ToolOutcome {
        val note = findNote(args.optStringOrNull("note_id")) ?: return noteNotFound()

        return ToolOutcome.ok("note" to noteJson(note, preview = false))
    }

    private fun createNote(args: JSONObject): ToolOutcome {
        val title = args.optStringOrNull("title") ?: ""
        val rawContent = args.optString("content")
        if (title.isBlank() && rawContent.isBlank()) {
            return ToolOutcome.error("A note needs a title or some content.")
        }

        val (text, spans) = NoteRichText.parseMarkup(rawContent)
        val created = ScrittoStore.createNote()

        ScrittoStore.updateNote(created.id, title.trim(), text, spans, "left")
        if (args.optBoolean("pin", false)) ScrittoStore.setPinned(created.id, true)

        return ToolOutcome(
            JSONObject().put("ok", true).put("note_id", shortId(created.id)),
            AssistantAction("Created note “${title.trim().ifBlank { "Untitled" }}”", AssistantAction.Kind.NOTE, NavTarget.Note(created.id))
        )
    }

    private fun updateNote(args: JSONObject): ToolOutcome {
        val note = findNote(args.optStringOrNull("note_id")) ?: return noteNotFound()

        val newTitle = args.optStringOrNull("title") ?: note.title
        var text = note.content
        var spans: List<NoteSpan> = note.spans

        val replacement = args.optStringOrNull("content")
        val appended = args.optStringOrNull("append_content")

        if (replacement != null) {
            val parsed = NoteRichText.parseMarkup(replacement)
            text = parsed.first
            spans = parsed.second
        }

        if (appended != null) {
            val parsed = NoteRichText.parseMarkup(appended)
            val separator = if (text.isEmpty() || text.endsWith("\n")) "" else "\n"
            val offset = text.length + separator.length

            text = text + separator + parsed.first
            spans = spans + parsed.second.map { it.copy(start = it.start + offset, end = it.end + offset) }
        }

        if (replacement == null && appended == null && newTitle == note.title) {
            return ToolOutcome.error("Nothing to change: pass title, content or append_content.")
        }

        ScrittoStore.updateNote(note.id, newTitle, text, spans, note.textAlign)

        return ToolOutcome(
            JSONObject().put("ok", true).put("note_id", shortId(note.id)),
            AssistantAction("Updated note “${newTitle.ifBlank { "Untitled" }}”", AssistantAction.Kind.NOTE, NavTarget.Note(note.id))
        )
    }

    private fun pinNote(args: JSONObject): ToolOutcome {
        val note = findNote(args.optStringOrNull("note_id")) ?: return noteNotFound()
        val pinned = args.optBoolean("pinned", true)

        ScrittoStore.setPinned(note.id, pinned)

        return ToolOutcome(
            JSONObject().put("ok", true),
            AssistantAction(
                (if (pinned) "Pinned" else "Unpinned") + " “${note.title.ifBlank { "Untitled" }}”",
                AssistantAction.Kind.NOTE,
                NavTarget.Note(note.id)
            )
        )
    }

    private fun deleteNote(args: JSONObject): ToolOutcome {
        val note = findNote(args.optStringOrNull("note_id")) ?: return noteNotFound()

        ScrittoStore.deleteNote(note.id)

        return ToolOutcome(
            JSONObject().put("ok", true),
            AssistantAction("Deleted note “${note.title.ifBlank { "Untitled" }}”", AssistantAction.Kind.NOTE)
        )
    }

    // ------------------------------------------------------------------
    // Tasks
    // ------------------------------------------------------------------

    private fun listTasks(args: JSONObject): ToolOutcome {
        val now = System.currentTimeMillis()
        val includeCompleted = args.optBoolean("include_completed", false)

        val tasks = ScrittoStore.tasks.filter {
            !it.completed || (includeCompleted && it.completedAt != null && now - it.completedAt < DAY)
        }.sortedBy { it.dueAt }

        return ToolOutcome.ok(
            "count" to tasks.size,
            "tasks" to JSONArray(tasks.map { taskJson(it, now) })
        )
    }

    private fun createTask(args: JSONObject): ToolOutcome {
        val title = args.optStringOrNull("title") ?: return ToolOutcome.error("A task needs a title.")
        val due = parseTime(args.optStringOrNull("due"))
            ?: return ToolOutcome.error("Missing or invalid 'due'. Use yyyy-MM-ddTHH:mm.")

        val task = ScrittoStore.createTask(
            context = appContext,
            title = title,
            description = args.optString("description", ""),
            dueAt = due,
            priority = parsePriority(args.optStringOrNull("priority")) ?: Task.Priority.MEDIUM
        )

        val warning = if (due <= System.currentTimeMillis()) {
            "The due time is already in the past, so no reminder will fire."
        } else {
            null
        }

        return ToolOutcome(
            JSONObject().put("ok", true).put("task_id", shortId(task.id)).put("due", AssistantTime.iso(due))
                .also { if (warning != null) it.put("warning", warning) },
            AssistantAction(
                "Added task “${task.title}” · ${AssistantTime.human(due)}",
                AssistantAction.Kind.TASK,
                NavTarget.Tasks
            )
        )
    }

    private fun updateTask(args: JSONObject): ToolOutcome {
        val task = findTask(args.optStringOrNull("task_id")) ?: return taskNotFound()

        val due = args.optStringOrNull("due")?.let {
            parseTime(it) ?: return ToolOutcome.error("Invalid 'due'. Use yyyy-MM-ddTHH:mm.")
        }

        val updated = ScrittoStore.updateTask(
            context = appContext,
            id = task.id,
            title = args.optStringOrNull("title"),
            description = if (args.has("description")) args.optString("description") else null,
            dueAt = due,
            priority = parsePriority(args.optStringOrNull("priority"))
        ) ?: return taskNotFound()

        return ToolOutcome(
            JSONObject().put("ok", true).put("task", taskJson(updated, System.currentTimeMillis())),
            AssistantAction(
                "Updated task “${updated.title}” · ${AssistantTime.human(updated.dueAt)}",
                AssistantAction.Kind.TASK,
                NavTarget.Tasks
            )
        )
    }

    private fun completeTask(args: JSONObject): ToolOutcome {
        val task = findTask(args.optStringOrNull("task_id")) ?: return taskNotFound()
        val completed = args.optBoolean("completed", true)

        ScrittoStore.setTaskCompleted(appContext, task.id, completed)

        return ToolOutcome(
            JSONObject().put("ok", true),
            AssistantAction(
                (if (completed) "Completed" else "Reopened") + " task “${task.title}”",
                AssistantAction.Kind.TASK,
                NavTarget.Tasks
            )
        )
    }

    private fun deleteTask(args: JSONObject): ToolOutcome {
        val task = findTask(args.optStringOrNull("task_id")) ?: return taskNotFound()

        ScrittoStore.deleteTask(appContext, task.id)

        return ToolOutcome(
            JSONObject().put("ok", true),
            AssistantAction("Deleted task “${task.title}”", AssistantAction.Kind.TASK)
        )
    }

    // ------------------------------------------------------------------
    // Schedule / calendar
    // ------------------------------------------------------------------

    private fun listEvents(args: JSONObject): ToolOutcome {
        val today = AssistantTime.startOfDay(System.currentTimeMillis())
        val from = args.optStringOrNull("from")?.let { AssistantTime.parse(it) }?.let { AssistantTime.startOfDay(it) } ?: today
        val to = args.optStringOrNull("to")?.let { AssistantTime.parse(it) }?.let { AssistantTime.startOfDay(it) + DAY }
            ?: (from + 30 * DAY)

        val events = ScrittoStore.events
            .filter { it.startAt < to && it.endAt > from }
            .sortedBy { it.startAt }

        return ToolOutcome.ok(
            "count" to events.size,
            "events" to JSONArray(events.take(100).map { eventJson(it) })
        )
    }

    private fun dayAgenda(args: JSONObject): ToolOutcome {
        val day = args.optStringOrNull("date")?.let { AssistantTime.parse(it) }
            ?: return ToolOutcome.error("Missing or invalid 'date'. Use yyyy-MM-dd.")

        val start = AssistantTime.startOfDay(day)
        val end = start + DAY
        val now = System.currentTimeMillis()

        val events = ScrittoStore.events.filter { it.startAt < end && it.endAt > start }.sortedBy { it.startAt }
        val tasks = ScrittoStore.tasks.filter { !it.completed && it.dueAt in start until end }.sortedBy { it.dueAt }

        return ToolOutcome(
            JSONObject()
                .put("ok", true)
                .put("date", AssistantTime.humanDay(start))
                .put("events", JSONArray(events.map { eventJson(it) }))
                .put("tasks_due", JSONArray(tasks.map { taskJson(it, now) })),
            navigation = null
        )
    }

    private fun createEvent(args: JSONObject): ToolOutcome {
        val title = args.optStringOrNull("title") ?: return ToolOutcome.error("An event needs a title.")
        val start = parseTime(args.optStringOrNull("start"))
            ?: return ToolOutcome.error("Missing or invalid 'start'. Use yyyy-MM-ddTHH:mm.")
        val end = args.optStringOrNull("end")?.let {
            parseTime(it) ?: return ToolOutcome.error("Invalid 'end'. Use yyyy-MM-ddTHH:mm.")
        } ?: (start + HOUR)

        if (end <= start) return ToolOutcome.error("The event must end after it starts.")

        val type = parseEventType(args.optStringOrNull("type")) ?: ScheduleEvent.Type.EVENT
        val repeats = (args.optIntOrNull("repeat_weeks") ?: 1).coerceIn(1, 52)
        val reminder = (args.optIntOrNull("reminder_minutes") ?: 15).coerceIn(0, 24 * 60)

        var created = 0
        for (week in 0 until repeats) {
            val shift = week * 7L
            val event = ScrittoStore.createScheduleEvent(
                context = appContext,
                title = title,
                location = args.optString("location", ""),
                startAt = plusDays(start, shift),
                endAt = plusDays(end, shift),
                type = type,
                reminderMinutes = reminder
            )
            if (event.id.isNotEmpty()) created++
        }

        val label = (if (type == ScheduleEvent.Type.CLASS) "Added class" else "Added event") +
            " “${title.trim()}” · ${AssistantTime.human(start)}" +
            if (created > 1) " (×$created weekly)" else ""

        return ToolOutcome(
            JSONObject().put("ok", true).put("created", created).put("start", AssistantTime.iso(start)),
            AssistantAction(label, AssistantAction.Kind.EVENT, NavTarget.Schedule)
        )
    }

    private fun updateEvent(args: JSONObject): ToolOutcome {
        val event = findEvent(args.optStringOrNull("event_id")) ?: return eventNotFound()

        val start = args.optStringOrNull("start")?.let {
            parseTime(it) ?: return ToolOutcome.error("Invalid 'start'. Use yyyy-MM-ddTHH:mm.")
        }
        var end = args.optStringOrNull("end")?.let {
            parseTime(it) ?: return ToolOutcome.error("Invalid 'end'. Use yyyy-MM-ddTHH:mm.")
        }

        // Moving the start without an end keeps the original duration.
        if (start != null && end == null) end = start + (event.endAt - event.startAt)

        val updated = ScrittoStore.updateScheduleEvent(
            context = appContext,
            id = event.id,
            title = args.optStringOrNull("title"),
            location = if (args.has("location")) args.optString("location") else null,
            startAt = start,
            endAt = end,
            type = parseEventType(args.optStringOrNull("type")),
            reminderMinutes = args.optIntOrNull("reminder_minutes")
        ) ?: return eventNotFound()

        return ToolOutcome(
            JSONObject().put("ok", true).put("event", eventJson(updated)),
            AssistantAction(
                "Updated “${updated.title}” · ${AssistantTime.human(updated.startAt)}",
                AssistantAction.Kind.EVENT,
                NavTarget.Schedule
            )
        )
    }

    private fun deleteEvent(args: JSONObject): ToolOutcome {
        val event = findEvent(args.optStringOrNull("event_id")) ?: return eventNotFound()

        val victims = if (args.optBoolean("whole_series", false)) {
            ScrittoStore.events.filter { other ->
                other.startAt >= event.startAt &&
                    other.title.equals(event.title, ignoreCase = true) &&
                    other.type == event.type &&
                    timeOfWeek(other.startAt) == timeOfWeek(event.startAt)
            }
        } else {
            listOf(event)
        }

        victims.forEach { ScrittoStore.deleteScheduleEvent(appContext, it.id) }

        return ToolOutcome(
            JSONObject().put("ok", true).put("removed", victims.size),
            AssistantAction(
                "Removed “${event.title}”" + if (victims.size > 1) " (${victims.size} occurrences)" else "",
                AssistantAction.Kind.EVENT,
                NavTarget.Schedule
            )
        )
    }

    // ------------------------------------------------------------------
    // Files, search, navigation
    // ------------------------------------------------------------------

    private fun listFiles(): ToolOutcome =
        ToolOutcome.ok(
            "count" to ScrittoStore.importedFiles.size,
            "files" to JSONArray(ScrittoStore.importedFiles.map { it.name })
        )

    private suspend fun readFile(args: JSONObject): ToolOutcome {
        val query = args.optStringOrNull("file_name") ?: return ToolOutcome.error("Missing 'file_name'.")
        val file = withContext(Dispatchers.Main.immediate) {
            ScrittoStore.importedFiles.firstOrNull { it.name.equals(query, ignoreCase = true) }
                ?: ScrittoStore.importedFiles.filter { it.name.contains(query, ignoreCase = true) }.singleOrNull()
        } ?: return ToolOutcome.error(
            "No imported file matches '$query'. Call list_files to see the exact names."
        )

        val result = files.read(file)

        return if (result.ok) {
            ToolOutcome(
                JSONObject()
                    .put("ok", true)
                    .put("file", file.name)
                    .put("truncated", result.truncated)
                    .put("content", result.text),
                AssistantAction("Read “${file.name}”", AssistantAction.Kind.FILE, NavTarget.Files)
            )
        } else {
            ToolOutcome.error(result.text)
        }
    }

    private fun search(args: JSONObject): ToolOutcome {
        val query = args.optStringOrNull("query") ?: return ToolOutcome.error("Missing 'query'.")
        val needle = query.lowercase()
        val now = System.currentTimeMillis()

        val notes = ScrittoStore.notes.filter {
            it.title.lowercase().contains(needle) || it.content.lowercase().contains(needle)
        }
        val tasks = ScrittoStore.tasks.filter {
            it.title.lowercase().contains(needle) || it.description.lowercase().contains(needle)
        }
        val events = ScrittoStore.events.filter {
            it.title.lowercase().contains(needle) || it.location.lowercase().contains(needle)
        }
        val fileNames = ScrittoStore.importedFiles.filter { it.name.lowercase().contains(needle) }

        return ToolOutcome.ok(
            "notes" to JSONArray(notes.take(10).map { noteJson(it, preview = true, around = needle) }),
            "tasks" to JSONArray(tasks.take(10).map { taskJson(it, now) }),
            "events" to JSONArray(events.take(10).map { eventJson(it) }),
            "files" to JSONArray(fileNames.take(10).map { it.name })
        )
    }

    private fun navigate(args: JSONObject): ToolOutcome {
        val target = when (args.optString("screen").lowercase()) {
            "home" -> NavTarget.Home
            "notes" -> NavTarget.Notes
            "tasks" -> NavTarget.Tasks
            "schedule", "calendar" -> NavTarget.Schedule
            "files" -> NavTarget.Files
            "note" -> {
                val note = findNote(args.optStringOrNull("note_id")) ?: return noteNotFound()
                NavTarget.Note(note.id)
            }
            else -> return ToolOutcome.error("Unknown screen. Use home, notes, tasks, schedule, files or note.")
        }

        val label = when (target) {
            NavTarget.Home -> "Opened Home"
            NavTarget.Notes -> "Opened Notes"
            NavTarget.Tasks -> "Opened Tasks"
            NavTarget.Schedule -> "Opened Schedule"
            NavTarget.Files -> "Opened Files"
            is NavTarget.Note -> "Opened note"
        }

        return ToolOutcome(
            JSONObject().put("ok", true),
            AssistantAction(label, AssistantAction.Kind.NAVIGATE, target),
            navigation = target
        )
    }

    // ------------------------------------------------------------------
    // Lookups — ids are shortened to 8 characters to keep prompts small
    // ------------------------------------------------------------------

    private fun shortId(id: String) = id.take(8)

    private fun findNote(query: String?): Note? = resolve(
        query,
        ScrittoStore.notes,
        idOf = { it.id },
        titleOf = { it.title }
    )

    private fun findTask(query: String?): Task? = resolve(
        query,
        ScrittoStore.tasks,
        idOf = { it.id },
        titleOf = { it.title }
    )

    private fun findEvent(query: String?): ScheduleEvent? = resolve(
        query,
        ScrittoStore.events,
        idOf = { it.id },
        titleOf = { it.title }
    )

    private fun <T> resolve(
        query: String?,
        items: List<T>,
        idOf: (T) -> String,
        titleOf: (T) -> String
    ): T? {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return null

        items.firstOrNull { idOf(it) == q }?.let { return it }
        items.filter { idOf(it).startsWith(q, ignoreCase = true) }.singleOrNull()?.let { return it }
        items.filter { titleOf(it).equals(q, ignoreCase = true) }.let { exact ->
            // Several with the same title: the upcoming/most recent (first) one is the natural reading.
            if (exact.isNotEmpty()) return exact.first()
        }
        return items.filter { titleOf(it).contains(q, ignoreCase = true) }.singleOrNull()
    }

    private fun noteNotFound() = ToolOutcome.error("No matching note. Use an id from the workspace list or list_notes.")
    private fun taskNotFound() = ToolOutcome.error("No matching task. Use an id from the workspace list or list_tasks.")
    private fun eventNotFound() = ToolOutcome.error("No matching event. Use an id from the workspace list or list_events.")

    // ------------------------------------------------------------------
    // JSON views
    // ------------------------------------------------------------------

    private fun noteJson(note: Note, preview: Boolean, around: String? = null): JSONObject =
        JSONObject()
            .put("id", shortId(note.id))
            .put("title", note.title)
            .put("pinned", note.isPinned)
            .put("updated", AssistantTime.iso(note.updatedAt))
            .put(
                if (preview) "preview" else "content",
                when {
                    !preview -> note.content.take(MAX_NOTE_CHARS)
                    around != null -> snippet(note.content, around)
                    else -> note.content.replace('\n', ' ').take(120)
                }
            )

    private fun snippet(text: String, needle: String): String {
        val at = text.lowercase().indexOf(needle)
        if (at < 0) return text.replace('\n', ' ').take(120)

        val from = (at - 40).coerceAtLeast(0)
        return text.substring(from, (at + needle.length + 80).coerceAtMost(text.length)).replace('\n', ' ')
    }

    private fun taskJson(task: Task, now: Long): JSONObject =
        JSONObject()
            .put("id", shortId(task.id))
            .put("title", task.title)
            .put("description", task.description)
            .put("due", AssistantTime.iso(task.dueAt))
            .put("priority", task.priority.name)
            .put("completed", task.completed)
            .put("overdue", !task.completed && task.dueAt < now)

    private fun eventJson(event: ScheduleEvent): JSONObject =
        JSONObject()
            .put("id", shortId(event.id))
            .put("title", event.title)
            .put("type", event.type.name)
            .put("start", AssistantTime.iso(event.startAt))
            .put("end", AssistantTime.iso(event.endAt))
            .put("location", event.location)
            .put("reminder_minutes", event.reminderMinutes)

    // ------------------------------------------------------------------
    // Parsing helpers
    // ------------------------------------------------------------------

    private fun parseTime(text: String?): Long? = text?.let { AssistantTime.parse(it) }

    private fun parsePriority(text: String?): Task.Priority? =
        runCatching { Task.Priority.valueOf(text.orEmpty().trim().uppercase()) }.getOrNull()

    private fun parseEventType(text: String?): ScheduleEvent.Type? =
        runCatching { ScheduleEvent.Type.valueOf(text.orEmpty().trim().uppercase()) }.getOrNull()

    private fun plusDays(millis: Long, days: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_YEAR, days.toInt())
        }.timeInMillis

    /** Weekday + minute of day, so a weekly series can be recognised. */
    private fun timeOfWeek(millis: Long): Pair<Int, Int> {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return calendar.get(Calendar.DAY_OF_WEEK) to
            (calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE))
    }

    private companion object {
        const val HOUR = 60L * 60L * 1000L
        const val DAY = 24L * HOUR
        const val MAX_SNAPSHOT_ITEMS = 25
        const val MAX_NOTE_CHARS = 20_000
    }
}
