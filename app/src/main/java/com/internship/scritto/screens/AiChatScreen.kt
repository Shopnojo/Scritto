package com.internship.scritto.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoCream
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoSurface
import com.internship.scritto.ui.theme.ScrittoTextMuted
import com.internship.scritto.ui.theme.ScrittoTextSecondary

private data class ChatMessage(
    val text: String,
    val fromUser: Boolean
)

@Composable
fun AiChatScreen() {
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Hey. I'm Scritto AI. Ask me to create something, find a note, or help organize your workspace.",
                false
            )
        )
    }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, top = 54.dp, bottom = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ScrittoAmber),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "Scritto AI",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(21.dp)
                )
            }

            Column {
                Text(
                    text = "Scritto AI",
                    color = ScrittoCreamBright,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Your workspace assistant",
                    color = ScrittoTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Text(
                        text = message.text,
                        color = if (message.fromUser) MaterialTheme.colorScheme.background else ScrittoCream,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (message.fromUser) ScrittoCreamBright else ScrittoSurface
                            )
                            .padding(horizontal = 15.dp, vertical = 12.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ScrittoSurface)
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = ScrittoCream,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(ScrittoAmber),
                decorationBox = { inner ->
                    if (input.isBlank()) {
                        Text(
                            text = "Ask Scritto AI...",
                            color = ScrittoTextMuted,
                            fontSize = 15.sp
                        )
                    }
                    inner()
                }
            )

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(ScrittoAmber)
                    .clickable {
                        val prompt = input.trim()
                        if (prompt.isNotEmpty()) {
                            messages += ChatMessage(prompt, true)
                            messages += ChatMessage(aiReply(prompt), false)
                            input = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

private fun aiReply(prompt: String): String {
    val normalized = prompt.lowercase()

    return when {
        "new note" in normalized || "create note" in normalized ->
            "I can open a new note for you. Use the + button or the Note quick action to start writing."

        "note" in normalized ->
            "I can help you work with your notes. Try asking about recent notes, a pinned note, or creating a new note."

        "schedule" in normalized || "class" in normalized ->
            "Schedule is ready in Scritto. The schedule workspace is the place for classes and events."

        "help" in normalized || "what can you do" in normalized ->
            "I can help with notes, tasks, events, classes, files, and your schedule. More workspace actions will be added here as Scritto grows."

        "hello" in normalized || "hi" in normalized || "hey" in normalized ->
            "Hey bro 👋 What are we working on?"

        else ->
            "Got it. I understand the request, but that action isn't connected yet. The AI workspace will gain more Scritto actions as we build them."
    }
}
