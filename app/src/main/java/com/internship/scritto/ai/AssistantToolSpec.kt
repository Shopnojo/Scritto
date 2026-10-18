package com.internship.scritto.ai

import org.json.JSONArray
import org.json.JSONObject

/** The tools Gemini may call. Kept separate from the implementation so tests can use the exact same schema. */
object AssistantToolSpec {

    val declarations: JSONArray = JSONArray().put(
        JSONObject().put(
            "functionDeclarations",
            JSONArray(
                listOf(
                    declare(
                        "search_workspace",
                        "Search notes, tasks, events and files for a word or phrase.",
                        stringParam("query", "Text to look for.", required = true)
                    ),
                    declare(
                        "list_notes",
                        "List notes (pinned first, then most recently edited)."
                    ),
                    declare(
                        "read_note",
                        "Read the full text of one note.",
                        stringParam("note_id", "Note id or exact title.", required = true)
                    ),
                    declare(
                        "create_note",
                        "Create a new note. The body may use **bold**, *italic*, __underline__, ~~strike~~, " +
                            "'- ' bullets and '# ' headings; they become real formatting.",
                        stringParam("title", "Note title.", required = true),
                        stringParam("content", "Note body.", required = true),
                        boolParam("pin", "Pin the note to Home.")
                    ),
                    declare(
                        "update_note",
                        "Edit a note. Use 'content' to replace the whole body, or 'append_content' to add to the end.",
                        stringParam("note_id", "Note id or exact title.", required = true),
                        stringParam("title", "New title."),
                        stringParam("content", "Replacement body (same markup as create_note)."),
                        stringParam("append_content", "Text to add after the existing body.")
                    ),
                    declare(
                        "pin_note",
                        "Pin or unpin a note on Home.",
                        stringParam("note_id", "Note id or exact title.", required = true),
                        boolParam("pinned", "true to pin, false to unpin.")
                    ),
                    declare(
                        "delete_note",
                        "Permanently delete a note. Only when the user clearly asked for it.",
                        stringParam("note_id", "Note id or exact title.", required = true)
                    ),
                    declare(
                        "list_tasks",
                        "List tasks. Pending ones by default.",
                        boolParam("include_completed", "Also include tasks completed in the last 24 hours.")
                    ),
                    declare(
                        "create_task",
                        "Create a task with a due date/time. A reminder notification is scheduled automatically.",
                        stringParam("title", "What needs doing.", required = true),
                        stringParam(
                            "due",
                            "Local due time as yyyy-MM-ddTHH:mm (a bare yyyy-MM-dd means 09:00).",
                            required = true
                        ),
                        stringParam("description", "Optional details."),
                        stringParam("priority", "Priority.", enum = listOf("LOW", "MEDIUM", "HIGH"))
                    ),
                    declare(
                        "update_task",
                        "Change a task's title, due time, description or priority.",
                        stringParam("task_id", "Task id or exact title.", required = true),
                        stringParam("title", "New title."),
                        stringParam("due", "New due time, yyyy-MM-ddTHH:mm."),
                        stringParam("description", "New description."),
                        stringParam("priority", "New priority.", enum = listOf("LOW", "MEDIUM", "HIGH"))
                    ),
                    declare(
                        "complete_task",
                        "Mark a task done (or not done).",
                        stringParam("task_id", "Task id or exact title.", required = true),
                        boolParam("completed", "Defaults to true.")
                    ),
                    declare(
                        "delete_task",
                        "Delete a task.",
                        stringParam("task_id", "Task id or exact title.", required = true)
                    ),
                    declare(
                        "list_events",
                        "List schedule events and classes in a date range (default: the next 30 days).",
                        stringParam("from", "Start date, yyyy-MM-dd."),
                        stringParam("to", "End date, yyyy-MM-dd (inclusive).")
                    ),
                    declare(
                        "get_day_agenda",
                        "Everything on one day: events, classes and tasks due.",
                        stringParam("date", "Day, yyyy-MM-dd.", required = true)
                    ),
                    declare(
                        "create_event",
                        "Add an event or class to the schedule/calendar. A reminder is scheduled automatically. " +
                            "Cannot be created for a past day.",
                        stringParam("title", "Event title.", required = true),
                        stringParam("start", "Start as yyyy-MM-ddTHH:mm.", required = true),
                        stringParam("end", "End as yyyy-MM-ddTHH:mm. Defaults to one hour after start."),
                        stringParam("type", "EVENT or CLASS.", enum = listOf("EVENT", "CLASS")),
                        stringParam("location", "Room or place."),
                        intParam("reminder_minutes", "Minutes before start to remind. Default 15."),
                        intParam(
                            "repeat_weeks",
                            "Total number of weekly occurrences including the first (e.g. 8 = every week for 8 weeks). Default 1."
                        )
                    ),
                    declare(
                        "update_event",
                        "Reschedule or edit an event/class.",
                        stringParam("event_id", "Event id or exact title.", required = true),
                        stringParam("title", "New title."),
                        stringParam("start", "New start, yyyy-MM-ddTHH:mm."),
                        stringParam("end", "New end, yyyy-MM-ddTHH:mm."),
                        stringParam("type", "EVENT or CLASS.", enum = listOf("EVENT", "CLASS")),
                        stringParam("location", "New location."),
                        intParam("reminder_minutes", "New reminder lead time in minutes.")
                    ),
                    declare(
                        "delete_event",
                        "Remove an event or class.",
                        stringParam("event_id", "Event id or exact title.", required = true),
                        boolParam("whole_series", "Also remove all later weekly repeats of it.")
                    ),
                    declare(
                        "list_files",
                        "List the files the user imported into Scritto."
                    ),
                    declare(
                        "read_file",
                        "Read the whole contents of an imported file (text, code, CSV, Word, PowerPoint, Excel, PDF, image, audio). " +
                            "Use this to summarise a file, pull out key points or answer questions about it. PDF text comes back with [Page N] markers. " +
                            "Not needed for a file attached to the current message: its text is already in the message.",
                        stringParam("file_name", "File name, as returned by list_files.", required = true)
                    ),
                    declare(
                        "get_pdf_text",
                        "Read the text of a PDF's pages, on the device (cheap). Use it only to prepare an edit_pdf, so you quote the exact text; to summarise a PDF use read_file. " +
                            "Read only the pages you need; the result is capped, so ask for a smaller page range if it is truncated.",
                        stringParam("file_name", "PDF file name, as returned by list_files.", required = true),
                        intParam("from_page", "First page, starting at 1. Default 1."),
                        intParam("to_page", "Last page. Default: the last page.")
                    ),
                    declare(
                        "edit_pdf",
                        "Edit an imported PDF as the user asked, in ONE call with all the changes. The result is a NEW file " +
                            "(the original is never changed) that appears in Files. Use only the changes the user requested.",
                        stringParam("file_name", "PDF file name, as returned by list_files.", required = true),
                        objectArrayParam(
                            name = "operations",
                            description = "The changes, applied in order. At most 20.",
                            required = true,
                            fields = listOf(
                                stringParam(
                                    "op",
                                    "replace_text: replace find with replace. add_text: write text at a position. highlight: mark find in yellow.",
                                    required = true,
                                    enum = listOf("replace_text", "add_text", "highlight")
                                ),
                                intParam("page", "Page number, starting at 1.", required = true),
                                stringParam("find", "Exact text on the page (copied from get_pdf_text). Needed for replace_text and highlight."),
                                stringParam("replace", "New text for replace_text. May be empty to delete the found text."),
                                stringParam("text", "Text to add for add_text."),
                                intParam("x_percent", "add_text: distance from the left edge, 0-100. Default 10."),
                                intParam("y_percent", "add_text: distance from the top edge, 0-100. Default 10."),
                                intParam("size", "add_text: font size in points, 6-40. Default 12.")
                            ),
                            requiredFields = listOf("op", "page")
                        )
                    ),
                    declare(
                        "navigate",
                        "Open a screen in the app.",
                        stringParam(
                            "screen",
                            "Where to go.",
                            required = true,
                            enum = listOf("home", "notes", "tasks", "schedule", "files", "note")
                        ),
                        stringParam("note_id", "Required when screen is 'note'.")
                    )
                )
            )
        )
    )
}
