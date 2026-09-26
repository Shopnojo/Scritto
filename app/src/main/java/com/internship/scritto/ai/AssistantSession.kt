package com.internship.scritto.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-wide glue between screens and the assistant. */
object AssistantSession {

    /** A prompt typed elsewhere (e.g. Home's command bar) waiting for the chat screen to pick it up. */
    var pendingPrompt by mutableStateOf<String?>(null)

    /** Set by Home's mic button: the chat screen opens the voice assistant straight away. */
    var pendingVoice by mutableStateOf(false)

    /** The chat currently on screen, so it survives the assistant opening another screen. */
    var activeConversationId: String? = null

    private var reader: FileContentReader? = null

    fun fileReader(context: Context): FileContentReader =
        reader ?: FileContentReader(context.applicationContext, GeminiConfig.client).also { reader = it }

    fun assistant(context: Context): ScrittoAssistant =
        ScrittoAssistant(
            client = GeminiConfig.client,
            toolbox = ScrittoToolbox(context.applicationContext, fileReader(context))
        )
}
