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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import kotlinx.coroutines.launch

private data class ChatMessage(
    val text: String,
    val fromUser: Boolean
)

@Composable
fun AiChatScreen(
    onHome: () -> Unit,
    onNotes: () -> Unit,
    onCreateNote: () -> Unit,
    onSchedule: () -> Unit
) {
    val view = LocalView.current
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Hey. I'm Scritto AI. Ask me to create something, find a note, or help organize your workspace.",
                false
            )
        )
    }
    var input by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var islandExpanded by remember { mutableStateOf(false) }
    var placeholderIndex by remember { mutableIntStateOf(0) }
    var recentsOpen by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var conversationId by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val scope = rememberCoroutineScope()

    val recentConversations = remember {
        mutableStateListOf<ScrittoStore.AiConversation>().apply {
            addAll(ScrittoStore.getAiConversations())
        }
    }

    val listState = rememberLazyListState()

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
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 12.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
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

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(ScrittoSurface.copy(alpha = 0.82f))
                    .border(
                        1.dp,
                        ScrittoBorder.copy(alpha = 0.85f),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable { recentsOpen = !recentsOpen }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "Recent conversations",
                    tint = if (recentsOpen) ScrittoAmberBright else ScrittoTextSecondary,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Recent",
                    color = if (recentsOpen) ScrittoCreamBright else ScrittoTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
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
                    messages.clear()
                    messages.addAll(
                        conversation.messages.map {
                            ChatMessage(it.text, it.fromUser)
                        }
                    )
                    if (messages.isEmpty()) {
                        messages += ChatMessage(
                            "Hey. I'm Scritto AI. Ask me to create something, find a note, or help organize your workspace.",
                            false
                        )
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
                        Text(
                            text = message.text,
                            color = if (message.fromUser) MaterialTheme.colorScheme.background else ScrittoCream,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (message.fromUser) ScrittoCreamBright
                                    else ScrittoSurface.copy(alpha = 0.94f)
                                )
                                .padding(horizontal = 16.dp, vertical = 13.dp)
                        )
                    }
                }
            }

            if (thinking) {
                item(key = "thinking") {
                    AiThinkingBubble()
                }
            }        }

        AiComposer(
            input = input,
            onInputChange = { input = it },
            focused = focused,
            onFocusedChange = { focused = it },
            placeholder = placeholders[placeholderIndex],
            onSend = {
                val prompt = input.trim()
                if (prompt.isNotEmpty() && !thinking) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    messages += ChatMessage(prompt, true)
                    input = ""
                    focused = false
                    thinking = true

                    scope.launch {
                        kotlinx.coroutines.delay(620)
                        messages += ChatMessage(aiReply(prompt), false)
                        thinking = false

                        ScrittoStore.saveAiConversation(
                            id = conversationId,
                            title = if (prompt.length > 44) prompt.take(44) + "…" else prompt,
                            messages = messages.map {
                                ScrittoStore.AiMessage(it.text, it.fromUser)
                            }
                        )

                        recentConversations.clear()
                        recentConversations.addAll(ScrittoStore.getAiConversations())
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(44.dp))

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
    onSend: () -> Unit
) {
    val hasText = input.isNotBlank()
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
    val composerHeight = if (focused || hasText) 122.dp else 72.dp

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
                    visible = focused || hasText,
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
                            onClick = {}
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
            targetValue = if (hasText) 1f else 0.88f,
            animationSpec = tween(180),
            label = "sendScale"
        )

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
                    if (hasText) {
                        ScrittoAmber
                    } else {
                        ScrittoBorder.copy(alpha = 0.55f)
                    }
                )
                .clickable(enabled = hasText, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = "Send",
                tint = if (hasText) {
                    MaterialTheme.colorScheme.background
                } else {
                    ScrittoTextMuted
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
