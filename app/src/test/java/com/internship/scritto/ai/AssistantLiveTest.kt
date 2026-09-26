package com.internship.scritto.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Drives the REAL Gemini API through the production tool schema, against an in-memory workspace.
 *
 * Skipped unless SCRITTO_TEST_KEYS (comma separated) is set. Optionally SCRITTO_TEST_MODELS
 * restricts the model chain, e.g. "gemini-3.5-flash".
 */
class AssistantLiveTest {

    private class FakeWorkspace : AssistantToolbox {
        val tasks = mutableListOf<JSONObject>()
        val events = mutableListOf<JSONObject>()
        val notes = mutableListOf<JSONObject>()
        val calls = mutableListOf<String>()
        private var counter = 0

        override val declarations: JSONArray get() = AssistantToolSpec.declarations

        override fun workspaceSnapshot(): String =
            "NOTES (${notes.size}):\n" + notes.joinToString("\n") { "- [${it.getString("id")}] \"${it.getString("title")}\"" } +
                "\n\nOPEN TASKS (${tasks.size}):\n" +
                tasks.joinToString("\n") { "- [${it.getString("id")}] \"${it.getString("title")}\" due ${it.getString("due")}" } +
                "\n\nSCHEDULE (${events.size}):\n" +
                events.joinToString("\n") { "- [${it.getString("id")}] \"${it.getString("title")}\" ${it.getString("start")}" }

        override suspend fun execute(name: String, args: JSONObject): ToolOutcome {
            calls += "$name $args"

            return when (name) {
                "create_task" -> {
                    val task = JSONObject()
                        .put("id", "t${++counter}")
                        .put("title", args.getString("title"))
                        .put("due", args.getString("due"))
                        .put("priority", args.optString("priority", "MEDIUM"))
                        .put("completed", false)
                    tasks += task
                    ToolOutcome(JSONObject().put("ok", true).put("task_id", task.getString("id")),
                        AssistantAction("Added task", AssistantAction.Kind.TASK))
                }

                "list_tasks" -> ToolOutcome.ok("tasks" to JSONArray(tasks.filter { !it.getBoolean("completed") }))

                "update_task" -> {
                    val task = tasks.firstOrNull { it.getString("id") == args.optString("task_id") || it.getString("title").contains(args.optString("task_id"), true) }
                        ?: return ToolOutcome.error("No matching task.")
                    args.optString("due").takeIf { it.isNotBlank() }?.let { task.put("due", it) }
                    args.optString("title").takeIf { it.isNotBlank() }?.let { task.put("title", it) }
                    args.optString("priority").takeIf { it.isNotBlank() }?.let { task.put("priority", it) }
                    ToolOutcome(JSONObject().put("ok", true), AssistantAction("Updated task", AssistantAction.Kind.TASK))
                }

                "complete_task" -> {
                    val task = tasks.firstOrNull { it.getString("id") == args.optString("task_id") || it.getString("title").equals(args.optString("task_id"), true) }
                        ?: return ToolOutcome.error("No matching task.")
                    task.put("completed", true)
                    ToolOutcome(JSONObject().put("ok", true), AssistantAction("Completed", AssistantAction.Kind.TASK))
                }

                "create_event" -> {
                    val repeats = args.optInt("repeat_weeks", 1)
                    val start = LocalDateTime.parse(args.getString("start"))
                    repeat(repeats) { week ->
                        events += JSONObject()
                            .put("id", "e${++counter}")
                            .put("title", args.getString("title"))
                            .put("type", args.optString("type", "EVENT"))
                            .put("start", start.plusWeeks(week.toLong()).toString())
                            .put("location", args.optString("location", ""))
                    }
                    ToolOutcome(JSONObject().put("ok", true).put("created", repeats),
                        AssistantAction("Added event", AssistantAction.Kind.EVENT))
                }

                "list_events" -> ToolOutcome.ok("events" to JSONArray(events))

                "get_day_agenda" -> {
                    val day = LocalDate.parse(args.getString("date").take(10))
                    ToolOutcome.ok(
                        "events" to JSONArray(events.filter { it.getString("start").startsWith(day.toString()) }),
                        "tasks_due" to JSONArray(tasks.filter { !it.getBoolean("completed") && it.getString("due").startsWith(day.toString()) })
                    )
                }

                "update_event" -> {
                    val event = events.firstOrNull { it.getString("id") == args.optString("event_id") || it.getString("title").equals(args.optString("event_id"), true) }
                        ?: return ToolOutcome.error("No matching event.")
                    args.optString("start").takeIf { it.isNotBlank() }?.let { event.put("start", it) }
                    ToolOutcome(JSONObject().put("ok", true), AssistantAction("Updated", AssistantAction.Kind.EVENT))
                }

                "create_note" -> {
                    notes += JSONObject().put("id", "n${++counter}").put("title", args.getString("title")).put("content", args.getString("content"))
                    ToolOutcome(JSONObject().put("ok", true), AssistantAction("Created note", AssistantAction.Kind.NOTE))
                }

                "navigate" -> ToolOutcome(JSONObject().put("ok", true), AssistantAction("Opened", AssistantAction.Kind.NAVIGATE),
                    navigation = navFor(args.optString("screen")))

                else -> ToolOutcome.error("Unknown tool '$name'.")
            }
        }

        private fun navFor(screen: String): NavTarget = when (screen) {
            "notes" -> NavTarget.Notes
            "tasks" -> NavTarget.Tasks
            "schedule" -> NavTarget.Schedule
            "files" -> NavTarget.Files
            else -> NavTarget.Home
        }
    }

    private val keys = System.getenv("SCRITTO_TEST_KEYS").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private val models = System.getenv("SCRITTO_TEST_MODELS").orEmpty().split(',').map { it.trim() }
        .filter { it.isNotEmpty() }.ifEmpty { GeminiConfig.MODELS }

    private fun scenario(vararg prompts: String, voice: Boolean = false, check: (FakeWorkspace, List<AssistantReply>) -> Unit) {
        assumeTrue("SCRITTO_TEST_KEYS not set", keys.isNotEmpty())

        val workspace = FakeWorkspace()
        val client = GeminiClient(GeminiKeyRing(keys), models)
        val assistant = ScrittoAssistant(client, workspace)
        val history = mutableListOf<ChatTurn>()
        val replies = mutableListOf<AssistantReply>()

        runBlocking {
            prompts.forEach { prompt ->
                val reply = assistant.respond(history.toList(), prompt, voice = voice)
                println("USER : $prompt\nAI   : ${reply.text}\nACTS : ${reply.actions.map { it.label }}\nCALLS: ${workspace.calls.takeLast(4)}\n")
                history += ChatTurn(prompt, true)
                history += ChatTurn(reply.text, false)
                replies += reply
                Thread.sleep(1500)
            }
        }

        check(workspace, replies)
    }

    private val tomorrow: String get() = LocalDate.now(ZoneId.systemDefault()).plusDays(1).toString()

    @Test
    fun createsATaskWithResolvedDateAndPriority() = scenario(
        "Add a task to buy milk tomorrow at 6pm, high priority"
    ) { ws, replies ->
        assertEquals(1, ws.tasks.size)
        assertEquals("${tomorrow}T18:00", ws.tasks[0].getString("due"))
        assertEquals("HIGH", ws.tasks[0].getString("priority"))
        assertTrue(replies[0].text.isNotBlank())
    }

    @Test
    fun schedulesARecurringClass() = scenario(
        "Schedule my Physics class every Monday at 10am for 4 weeks in Room 204"
    ) { ws, _ ->
        assertEquals(4, ws.events.size)
        assertEquals("CLASS", ws.events[0].getString("type"))
        assertTrue(ws.events[0].getString("start").endsWith("T10:00"))
        assertTrue(java.time.DayOfWeek.MONDAY == LocalDateTime.parse(ws.events[0].getString("start")).dayOfWeek)
    }

    @Test
    fun writesAFormattedNote() = scenario(
        "Write a note called Goa trip ideas with three bullet points, make the first bold"
    ) { ws, _ ->
        assertEquals(1, ws.notes.size)
        assertTrue(ws.notes[0].getString("content").contains("- ") || ws.notes[0].getString("content").contains("•"))
    }

    @Test
    fun answersScheduleQuestionsFromRealData() = scenario(
        "Add a task to call mom tomorrow at 5pm",
        "What do I have tomorrow?"
    ) { _, replies ->
        assertTrue(replies[1].text.lowercase().contains("mom"))
    }

    @Test
    fun completesAndReschedulesExistingItems() = scenario(
        "Add a task to submit report tomorrow at 3pm",
        "I finished the report, mark it done",
        "Add a Chemistry class on Monday at 9am",
        "Move the Chemistry class to 11am"
    ) { ws, _ ->
        assertTrue(ws.tasks[0].getBoolean("completed"))
        assertTrue(ws.events[0].getString("start").endsWith("T11:00"))
    }

    @Test
    fun editsATaskFromAVoicePrompt() = scenario(
        "Add a task to submit the lab report tomorrow at 3pm",
        "Move my lab report task to tomorrow at 8pm",
        voice = true
    ) { ws, replies ->
        assertEquals("${tomorrow}T20:00", ws.tasks[0].getString("due"))
        assertTrue(replies[1].actions.isNotEmpty())
        // voice replies are short and spoken, no markdown
        assertTrue(replies[1].text.length < 200)
    }

    @Test
    fun changesTaskPriorityByVoice() = scenario(
        "Add a task to pay rent tomorrow at 10am",
        "Make the rent task high priority",
        voice = true
    ) { ws, _ ->
        assertEquals("HIGH", ws.tasks[0].getString("priority"))
    }

    @Test
    fun declinesGeneralKnowledgeQuestions() = scenario(
        "Tell me about elephants",
        "What is 12 times 13?",
        "Tell me a joke"
    ) { ws, replies ->
        assertTrue("no tools for off-topic questions", ws.calls.isEmpty())
        replies.forEach { reply ->
            assertTrue("reply should be one short line: ${reply.text}", reply.text.length < 140)
            assertTrue(reply.text.lowercase().contains("notes") || reply.text.lowercase().contains("tasks"))
        }
    }

    @Test
    fun stillWritesNotesOnAnyTopicWhenAsked() = scenario(
        "Write a note titled Elephants with three facts about elephants"
    ) { ws, _ ->
        assertEquals(1, ws.notes.size)
    }
}
