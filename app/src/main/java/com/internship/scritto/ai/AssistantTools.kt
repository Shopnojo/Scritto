package com.internship.scritto.ai

import org.json.JSONArray
import org.json.JSONObject

/** Where the assistant asks the app to go. */
sealed interface NavTarget {
    data object Home : NavTarget
    data object Notes : NavTarget
    data object Tasks : NavTarget
    data object Schedule : NavTarget
    data object Files : NavTarget
    data class Note(val id: String) : NavTarget
}

/** A visible record of something the assistant did ("Created task …"). */
data class AssistantAction(
    val label: String,
    val kind: Kind,
    val target: NavTarget? = null
) {
    enum class Kind { NOTE, TASK, EVENT, FILE, NAVIGATE }
}

data class ToolOutcome(
    val response: JSONObject,
    val action: AssistantAction? = null,
    val navigation: NavTarget? = null
) {
    companion object {
        fun ok(vararg fields: Pair<String, Any?>) =
            ToolOutcome(JSONObject().put("ok", true).also { json ->
                fields.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
            })

        fun error(message: String) =
            ToolOutcome(JSONObject().put("ok", false).put("error", message))
    }
}

/** What the model can see and do. The real implementation is [ScrittoToolbox]. */
interface AssistantToolbox {
    /** Gemini `functionDeclarations`. */
    val declarations: JSONArray

    /** Compact text overview of the workspace, placed in the system prompt. */
    fun workspaceSnapshot(): String

    suspend fun execute(name: String, args: JSONObject): ToolOutcome
}

// ------------------------------------------------------------------------
// Declaration helpers
// ------------------------------------------------------------------------

internal class Param(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enum: List<String>? = null
)

internal fun stringParam(name: String, description: String, required: Boolean = false, enum: List<String>? = null) =
    Param(name, "STRING", description, required, enum)

internal fun intParam(name: String, description: String, required: Boolean = false) =
    Param(name, "INTEGER", description, required)

internal fun boolParam(name: String, description: String) =
    Param(name, "BOOLEAN", description)

internal fun declare(name: String, description: String, vararg params: Param): JSONObject {
    val function = JSONObject().put("name", name).put("description", description)

    if (params.isNotEmpty()) {
        val properties = JSONObject()

        params.forEach { param ->
            properties.put(
                param.name,
                JSONObject().apply {
                    put("type", param.type)
                    put("description", param.description)
                    if (param.enum != null) put("enum", JSONArray(param.enum))
                }
            )
        }

        function.put(
            "parameters",
            JSONObject()
                .put("type", "OBJECT")
                .put("properties", properties)
                .put("required", JSONArray(params.filter { it.required }.map { it.name }))
        )
    }

    return function
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) {
        when (val raw = opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
    } else {
        null
    }
