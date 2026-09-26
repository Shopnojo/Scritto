package com.internship.scritto.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoAmberBright
import com.internship.scritto.ui.theme.ScrittoBorder
import com.internship.scritto.ui.theme.ScrittoCream
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoSurface
import com.internship.scritto.ui.theme.ScrittoTextMuted
import com.internship.scritto.ui.theme.ScrittoTextSecondary
import com.internship.scritto.data.repository.ScrittoStore
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.widget.Toast
import androidx.compose.ui.text.AnnotatedString
import com.internship.scritto.ai.AssistantAction
import com.internship.scritto.ai.AssistantSession
import com.internship.scritto.ai.Attachment
import com.internship.scritto.ai.ChatTurn
import com.internship.scritto.ai.NavTarget
import com.internship.scritto.components.ActionChip
import com.internship.scritto.components.VoiceAssistantOverlay
import com.internship.scritto.notes.NoteRichText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_ATTACHMENTS = 2

private const val GREETING =
    "Hey. I'm Scritto AI. Ask me to create something, find a note, or help organize your workspace."

private data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val actions: List<AssistantAction> = emptyList()
)

@Composable
fun AiChatScreen(
    onHome: () -> Unit,
    onNotes: () -> Unit,
    onCreateNote: () -> Unit,
    onSchedule: () -> Unit,
    onTasks: () -> Unit = {},
    onFiles: () -> Unit = {},
    onOpenNote: (String) -> Unit = {}
) {
    val view = LocalView.current
    val context = LocalContext.current
    // Coming back from a screen the assistant opened (a note, Tasks...) resumes the same chat.
    val resumed = remember {
        AssistantSession.activeConversationId?.let { id ->
            ScrittoStore.getAiConversations().firstOrNull { it.id == id }
        }
    }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            if (resumed != null && resumed.messages.isNotEmpty()) {
                addAll(resumed.messages.map { ChatMessage(it.text, it.fromUser) })
            } else {
                add(ChatMessage(GREETING, false))
            }
        }
    }
    var input by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var islandExpanded by remember { mutableStateOf(false) }
    var placeholderIndex by remember { mutableIntStateOf(0) }
    var recentsOpen by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var conversationId by remember { mutableStateOf(resumed?.id ?: java.util.UUID.randomUUID().toString()) }
    val scope = rememberCoroutineScope()

    var assistantOpen by remember { mutableStateOf(false) }

    // Back closes the voice assistant first, instead of leaving the whole chat screen.
    androidx.activity.compose.BackHandler(enabled = assistantOpen) {
        assistantOpen = false
    }
    var replyId by remember { mutableIntStateOf(0) }
    val attachments = remember { mutableStateListOf<ScrittoStore.ImportedFile>() }

    val assistant = remember { AssistantSession.assistant(context) }
    val fileReader = remember { AssistantSession.fileReader(context) }

    fun navigateTo(target: NavTarget?) {
        when (target) {
            NavTarget.Home -> onHome()
            NavTarget.Notes -> onNotes()
            NavTarget.Tasks -> onTasks()
            NavTarget.Schedule -> onSchedule()
            NavTarget.Files -> onFiles()
            is NavTarget.Note -> onOpenNote(target.id)
            null -> Unit
        }
    }

    // Reminders created by the assistant need the notification permission, just like the manual forms.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Bumped after files are attached so the prompt field takes focus: the files are
    // only read once the user says what they want done with them.
    var focusPromptSignal by remember { mutableIntStateOf(0) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val room = MAX_ATTACHMENTS - attachments.size
        var added = 0

        uris.forEach { uri ->
            if (added >= room) return@forEach

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val name = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Imported file"

            // Attached files are also kept in Files, so the assistant can find them later.
            val file = ScrittoStore.ImportedFile(name = name, uri = uri.toString())
            ScrittoStore.addImportedFile(file)

            if (attachments.none { it.uri == file.uri }) {
                attachments += file
                added++
            }
        }

        if (uris.size > room) {
            Toast.makeText(
                context,
                "You can attach up to $MAX_ATTACHMENTS files at a time",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (added > 0) focusPromptSignal++
    }

    val recentConversations = remember {
        mutableStateListOf<ScrittoStore.AiConversation>().apply {
            addAll(ScrittoStore.getAiConversations())
        }
    }

    val listState = rememberLazyListState()

    /** Runs one request through Scritto AI. [voice] asks for a short, speakable answer. */
    fun sendPrompt(rawPrompt: String, voice: Boolean = false) {
        val files = attachments.toList()
        val prompt = rawPrompt.trim()

        // Files are never sent on their own: the user has to say what to do with them.
        if (prompt.isEmpty() || thinking) return

        attachments.clear()
        messages += ChatMessage(
            text = prompt + files.joinToString("") { "\n📎 ${it.name}" },
            fromUser = true
        )
        input = ""
        focused = false
        thinking = true

        scope.launch {
            val attached = files.map { file ->
                val read = fileReader.read(file)
                Attachment(
                    name = file.name,
                    text = read.text.takeIf { read.ok },
                    error = read.text.takeUnless { read.ok }
                )
            }

            val history = messages.dropLast(1).map { ChatTurn(it.text, it.fromUser) }
            val reply = assistant.respond(history, prompt, attached, voice)

            messages += ChatMessage(reply.text, false, reply.actions)
            replyId++
            thinking = false

            ScrittoStore.saveAiConversation(
                id = conversationId,
                title = messages.firstOrNull { it.fromUser }?.text?.lineSequence()?.first()
                    ?.let { if (it.length > 44) it.take(44) + "…" else it }
                    ?: "New conversation",
                messages = messages.map {
                    ScrittoStore.AiMessage(it.text, it.fromUser)
                }
            )

            AssistantSession.activeConversationId = conversationId
            recentConversations.clear()
            recentConversations.addAll(ScrittoStore.getAiConversations())

            val createdReminder = reply.actions.any {
                it.kind == AssistantAction.Kind.TASK || it.kind == AssistantAction.Kind.EVENT
            }
            if (
                createdReminder &&
                android.os.Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            // "Open my notes" and friends: let the confirmation land (and be spoken) first.
            reply.navigation?.let { target ->
                delay(if (voice) 1600 else 600)
                assistantOpen = false
                navigateTo(target)
            }
        }
    }

    // Home's mic button lands here with the assistant ready to listen.
    LaunchedEffect(Unit) {
        if (AssistantSession.pendingVoice) {
            AssistantSession.pendingVoice = false
            assistantOpen = true
        }
    }

    // A prompt typed on Home ("Ask or command...") arrives here.
    LaunchedEffect(Unit) {
        AssistantSession.pendingPrompt?.let { prompt ->
            AssistantSession.pendingPrompt = null
            sendPrompt(prompt)
        }
    }

    val placeholders = remember {
        listOf(
            "Ask Scritto AI...",
            "Create a note...",
            "Find something in your workspace...",
            "What's on my schedule?",
            "Organize my workspace..."
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2800)
            if (!focused && input.isBlank()) {
                placeholderIndex = (placeholderIndex + 1) % placeholders.size
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AiAmbientGlow(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 8.dp)
        ) {
        // Narrow phones drop the labels so the title and the two actions never collide.
        val compactHeader = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 390

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title area takes whatever space is left and shortens itself if it has to.
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ScrittoAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = "Scritto AI",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Scritto AI",
                        color = ScrittoCreamBright,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "Your workspace assistant",
                        color = ScrittoTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Plain, background-free actions. The offset lines the last icon up with the page margin.
            Row(
                modifier = Modifier.offset(x = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderAction(
                    icon = Icons.Outlined.Add,
                    label = "New",
                    description = "New conversation",
                    compact = compactHeader,
                    enabled = !thinking,
                    highlighted = false,
                    onClick = {
                        messages.clear()
                        messages += ChatMessage(GREETING, false)
                        attachments.clear()
                        conversationId = java.util.UUID.randomUUID().toString()
                        AssistantSession.activeConversationId = null
                        input = ""
                        recentsOpen = false
                    }
                )

                HeaderAction(
                    icon = Icons.Outlined.History,
                    label = "Recent",
                    description = "Recent conversations",
                    compact = compactHeader,
                    enabled = true,
                    highlighted = recentsOpen,
                    onClick = { recentsOpen = !recentsOpen }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AnimatedVisibility(
            visible = recentsOpen,
            enter = slideInVertically(initialOffsetY = { -24 }) +
                fadeIn(tween(220)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(220)),
            exit = fadeOut(tween(160)) +
                scaleOut(targetScale = 0.98f, animationSpec = tween(160))
        ) {
            RecentConversations(
                conversations = recentConversations,
                onConversationSelected = { conversation ->
                    conversationId = conversation.id
                    AssistantSession.activeConversationId = conversation.id
                    messages.clear()
                    messages.addAll(
                        conversation.messages.map {
                            ChatMessage(it.text, it.fromUser)
                        }
                    )
                    if (messages.isEmpty()) {
                        messages += ChatMessage(GREETING, false)
                    }
                    input = ""
                    focused = false
                    thinking = false
                    recentsOpen = false
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 14.dp)
        ) {
            itemsIndexed(
                messages,
                key = { index, message -> "$index-${message.fromUser}-${message.text}" }
            ) { _, message ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(260, delayMillis = 35)) +
                        slideInHorizontally(
                            initialOffsetX = { if (message.fromUser) 44 else -44 },
                            animationSpec = tween(300, delayMillis = 35)
                        ) +
                        scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(260, delayMillis = 35)
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(0.86f),
                            horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (message.fromUser) {
                                    AnnotatedString(message.text)
                                } else {
                                    NoteRichText.renderMarkup(message.text)
                                },
                                color = if (message.fromUser) MaterialTheme.colorScheme.background else ScrittoCream,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (message.fromUser) ScrittoCreamBright
                                        else ScrittoSurface.copy(alpha = 0.94f)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 13.dp)
                            )

                            // What the assistant actually did - tap to jump there.
                            message.actions.forEach { action ->
                                ActionChip(
                                    action = action,
                                    onClick = { navigateTo(action.target) }
                                )
                            }
                        }
                    }
                }
            }

            if (thinking) {
                item(key = "thinking") {
                    AiThinkingBubble()
                }
            }        }

        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEach { file ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(ScrittoSurface.copy(alpha = 0.94f))
                            .border(1.dp, ScrittoBorder.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83D\uDCCE " + if (file.name.length > 26) file.name.take(26) + "\u2026" else file.name,
                            color = ScrittoCream,
                            fontSize = 13.sp
                        )

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { attachments.remove(file) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Remove attachment",
                                tint = ScrittoTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        AiComposer(
            input = input,
            onInputChange = { input = it },
            focused = focused,
            onFocusedChange = { focused = it },
            placeholder = if (attachments.isNotEmpty()) {
                "What should I do with your file${if (attachments.size > 1) "s" else ""}?"
            } else {
                placeholders[placeholderIndex]
            },
            canSend = input.isNotBlank(),
            hasAttachments = attachments.isNotEmpty(),
            focusSignal = focusPromptSignal,
            onAttach = {
                if (attachments.size >= MAX_ATTACHMENTS) {
                    Toast.makeText(
                        context,
                        "You can attach up to $MAX_ATTACHMENTS files at a time",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    filePicker.launch(arrayOf("*/*"))
                }
            },
            onMic = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                focused = false
                assistantOpen = true
            },
            onSend = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                sendPrompt(input)
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        AiNavigationIsland(
            expanded = islandExpanded,
            onExpandedChange = {
                islandExpanded = !islandExpanded
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            },
            onHome = onHome,
            onNotes = onNotes,
            onCreateNote = onCreateNote,
            onSchedule = onSchedule
        )
        }

        if (assistantOpen) {
            val lastReply = messages.lastOrNull { !it.fromUser }

            VoiceAssistantOverlay(
                thinking = thinking,
                replyText = lastReply?.text,
                replyId = replyId,
                actions = lastReply?.actions.orEmpty(),
                onUtterance = { sendPrompt(it, voice = true) },
                onActionClick = { action ->
                    assistantOpen = false
                    navigateTo(action.target)
                },
                onDismiss = { assistantOpen = false }
            )
        }
    }
}

/** A transparent header action: icon (+ label when there is room), 48 dp tall touch target. */
@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    compact: Boolean,
    enabled: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> ScrittoTextMuted
        highlighted -> ScrittoAmberBright
        else -> ScrittoTextSecondary
    }

    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )

        if (!compact) {
            Text(
                text = label,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecentConversations(
    conversations: List<ScrittoStore.AiConversation>,
    onConversationSelected: (ScrittoStore.AiConversation) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ScrittoSurface.copy(alpha = 0.94f))
            .border(1.dp, ScrittoBorder.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (conversations.isEmpty()) {
            Text(
                text = "No recent conversations yet",
                color = ScrittoTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(10.dp)
            )
        } else {
            conversations.take(6).forEachIndexed { index, conversation ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(180, delayMillis = index * 35)) +
                        slideInHorizontally(
                            initialOffsetX = { 24 },
                            animationSpec = tween(220, delayMillis = index * 35)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onConversationSelected(conversation) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ScrittoAmber.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = ScrittoAmberBright,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(9.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conversation.title,
                                color = ScrittoCream,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            Text(
                                text = conversation.messages.count { it.fromUser }.toString() + " messages",
                                color = ScrittoTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "aiThinking")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkingPulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(ScrittoSurface.copy(alpha = 0.94f))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .graphicsLayer {
                            alpha = (pulse - index * 0.18f).coerceIn(0.2f, 1f)
                        }
                        .clip(CircleShape)
                        .background(ScrittoAmberBright)
                )
            }
        }
    }
}

@Composable
private fun AiAmbientGlow(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "aiAmbient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientDrift"
    )
    val breath by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientBreath"
    )

    Canvas(modifier = modifier) {
        // A soft, nearly imperceptible amber haze moves slowly through the
        // background. There is no travelling orb or visible geometric path.
        val topCenter = androidx.compose.ui.geometry.Offset(
            x = size.width * (0.28f + drift * 0.18f),
            y = size.height * 0.18f
        )
        val lowerCenter = androidx.compose.ui.geometry.Offset(
            x = size.width * (0.72f - drift * 0.14f),
            y = size.height * 0.76f
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    ScrittoAmber.copy(alpha = 0.045f * breath),
                    ScrittoAmber.copy(alpha = 0.018f * breath),
                    Color.Transparent
                ),
                center = topCenter,
                radius = size.width * 0.52f
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    ScrittoAmberBright.copy(alpha = 0.026f * breath),
                    ScrittoAmber.copy(alpha = 0.010f * breath),
                    Color.Transparent
                ),
                center = lowerCenter,
                radius = size.width * 0.62f
            )
        )
    }
}

@Composable
private fun AiComposer(
    input: String,
    onInputChange: (String) -> Unit,
    focused: Boolean,
    onFocusedChange: (Boolean) -> Unit,
    placeholder: String,
    canSend: Boolean,
    hasAttachments: Boolean,
    focusSignal: Int,
    onAttach: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit
) {
    val hasText = input.isNotBlank()
    val expanded = focused || hasText || hasAttachments
    val promptFocus = remember { FocusRequester() }

    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) promptFocus.requestFocus()
    }
    val borderAlpha by animateFloatAsState(
        targetValue = if (focused || hasText) 0.68f else 0.10f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "aiComposerBorder"
    )
    val accent by animateColorAsState(
        targetValue = if (focused || hasText) {
            ScrittoAmber.copy(alpha = 0.32f)
        } else {
            ScrittoBorder.copy(alpha = 0.8f)
        },
        animationSpec = tween(240),
        label = "aiComposerAccent"
    )
    val composerHeight = if (expanded) 104.dp else 80.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(composerHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(composerHeight)
                .clip(FoldedSheetShape(26.dp))
                .background(ScrittoSurface.copy(alpha = 0.97f))
                .border(1.dp, accent.copy(alpha = borderAlpha), FoldedSheetShape(26.dp))
                .drawBehind {
                    if (focused || hasText) {
                        drawLine(
                            color = ScrittoAmber.copy(alpha = 0.16f),
                            start = androidx.compose.ui.geometry.Offset(
                                x = 22.dp.toPx(),
                                y = size.height - 2.dp.toPx()
                            ),
                            end = androidx.compose.ui.geometry.Offset(
                                x = size.width - 22.dp.toPx(),
                                y = size.height - 2.dp.toPx()
                            ),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
                .padding(start = 17.dp, end = 10.dp, top = 12.dp, bottom = 10.dp)
        ) {
            // Collapsed composer: keep "attach a file" one tap away.
            if (!expanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ScrittoAmber.copy(alpha = 0.14f))
                        .clickable(onClick = onAttach),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Attach a file",
                        tint = ScrittoAmberBright,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(promptFocus)
                            .onFocusChanged {
                                onFocusedChange(it.isFocused)
                            },
                        maxLines = 4,
                        textStyle = TextStyle(
                            color = ScrittoCream,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        ),
                        cursorBrush = SolidColor(ScrittoAmberBright),
                        decorationBox = { inner ->
                            Box {
                                if (input.isBlank()) {
                                    AnimatedContent(
                                        targetState = placeholder,
                                        transitionSpec = {
                                            fadeIn(tween(260)) togetherWith
                                                fadeOut(tween(180))
                                        },
                                        label = "aiPlaceholder"
                                    ) { currentPlaceholder ->
                                        Text(
                                            text = currentPlaceholder,
                                            color = ScrittoTextMuted,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                                inner()
                            }
                        }
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(180)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(180)
                    ),
                    exit = fadeOut(tween(140)) + scaleOut(
                        targetScale = 0.94f,
                        animationSpec = tween(140)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ComposerAction(
                            icon = Icons.Outlined.Add,
                            label = "Attach",
                            onClick = onAttach
                        )
                        ComposerAction(
                            icon = Icons.Outlined.AlternateEmail,
                            label = "Reference",
                            onClick = {
                                onInputChange(
                                    if (input.isBlank()) "@ " else "$input@ "
                                )
                            }
                        )
                        ComposerAction(
                            icon = Icons.Outlined.AutoAwesome,
                            label = "AI action",
                            onClick = {
                                onInputChange(
                                    if (input.isBlank()) "Help me " else "$input "
                                )
                            }
                        )
                    }
                }
            }
        }

        val sendScale by animateFloatAsState(
            targetValue = if (canSend) 1f else 0.88f,
            animationSpec = tween(180),
            label = "sendScale"
        )

        // Empty composer: the button becomes the microphone that opens the voice assistant.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(48.dp)
                .graphicsLayer {
                    scaleX = sendScale
                    scaleY = sendScale
                }
                .clip(CircleShape)
                .background(
                    if (canSend) {
                        ScrittoAmber
                    } else {
                        ScrittoAmber.copy(alpha = 0.16f)
                    }
                )
                .clickable(onClick = if (canSend) onSend else onMic),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (canSend) Icons.Outlined.ArrowUpward else Icons.Outlined.Mic,
                contentDescription = if (canSend) "Send" else "Talk to Scritto",
                tint = if (canSend) {
                    MaterialTheme.colorScheme.background
                } else {
                    ScrittoAmberBright
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ComposerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = ScrittoTextSecondary,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun AiNavigationIsland(
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onHome: () -> Unit,
    onNotes: () -> Unit,
    onCreateNote: () -> Unit,
    onSchedule: () -> Unit
) {
    val view = LocalView.current
    val width = if (expanded) 226.dp else 58.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(58.dp)
                .width(width)
                .clip(RoundedCornerShape(28.dp))
                .background(ScrittoSurface.copy(alpha = 0.90f))
                .border(
                    1.dp,
                    ScrittoCream.copy(alpha = if (expanded) 0.12f else 0.08f),
                    RoundedCornerShape(28.dp)
                )
                .clickable {
                    onExpandedChange()
                }
                .padding(horizontal = 0.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!expanded) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "Open navigation",
                    tint = ScrittoCreamBright,
                    modifier = Modifier.size(21.dp)
                )
            } else {
                AiIslandItem(Icons.Outlined.Home, "Home", onHome)
                AiIslandItem(Icons.Outlined.Description, "Notes", onNotes)

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ScrittoCreamBright)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onCreateNote()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Create note",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(23.dp)
                    )
                }

                AiIslandItem(Icons.Outlined.CalendarMonth, "Schedule", onSchedule)

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ScrittoAmber.copy(alpha = 0.18f))
                        .border(1.dp, ScrittoAmber.copy(alpha = 0.42f), CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onExpandedChange()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = "AI",
                        tint = ScrittoAmberBright,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AiIslandItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = ScrittoTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private class FoldedSheetShape(
    private val cornerRadius: Dp
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = with(density) { cornerRadius.toPx() }
        val fold = with(density) { 24.dp.toPx() }
        val path = Path().apply {
            val cutoutStart = size.width - fold * 4.2f
            val cutoutTop = size.height - fold * 2.1f

            moveTo(radius, 0f)
            lineTo(size.width - radius, 0f)
            quadraticTo(size.width, 0f, size.width, radius)
            lineTo(size.width, cutoutTop)
            lineTo(size.width - fold * 1.8f, cutoutTop)
            cubicTo(
                size.width - fold * 2.6f,
                cutoutTop,
                size.width - fold * 3.0f,
                cutoutTop + fold * 0.35f,
                cutoutStart + fold * 0.7f,
                size.height - fold * 0.45f
            )
            quadraticTo(
                cutoutStart,
                size.height,
                cutoutStart - fold * 0.5f,
                size.height
            )
            lineTo(radius, size.height)
            quadraticTo(0f, size.height, 0f, size.height - radius)
            lineTo(0f, radius)
            quadraticTo(0f, 0f, radius, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}
