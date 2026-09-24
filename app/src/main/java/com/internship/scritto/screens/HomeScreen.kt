package com.internship.scritto.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.data.repository.ScrittoStore
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoBackground
import com.internship.scritto.ui.theme.ScrittoBorder
import com.internship.scritto.ui.theme.ScrittoBorderSubtle
import com.internship.scritto.ui.theme.ScrittoCream
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoSurface
import com.internship.scritto.ui.theme.ScrittoSurfaceElevated
import com.internship.scritto.ui.theme.ScrittoTextMuted
import com.internship.scritto.ui.theme.ScrittoTextSecondary

@Composable
fun HomeScreen(
    onNoteSelected: (String) -> Unit = {},
    onNewNote: () -> Unit = {},
    onSchedule: () -> Unit = {}
) {
    val pinnedNote = ScrittoStore.notes.firstOrNull { it.isPinned }
    val recentNotes = ScrittoStore.notes
        .filter { !it.isPinned }
        .take(3)
    val dashboardScrollState = rememberScrollState()

    var commandText by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(dashboardScrollState)
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 54.dp,
                bottom = 112.dp
            )
    ) {

        // ================================================================
        // GREETING
        // ================================================================

        Text(
            text = "Good evening",
            color = ScrittoCreamBright,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "What are we working on?",
            color = ScrittoTextSecondary,
            fontSize = 17.sp,
            lineHeight = 24.sp
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        // ================================================================
        // COMMAND BAR
        // ================================================================

        HomeCommandBar(
            value = commandText,
            onValueChange = { newValue ->
                commandText = newValue
            }
        )

        Spacer(
            modifier = Modifier.height(26.dp)
        )

        // ================================================================
        // QUICK ACTIONS
        // ================================================================

        Text(
            text = "Quick actions",
            color = ScrittoCream,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            HomeQuickAction(
                modifier = Modifier.weight(1f),
                title = "Note",
                icon = Icons.Outlined.Description,
                onClick = onNewNote
            )

            HomeQuickAction(
                modifier = Modifier.weight(1f),
                title = "Task",
                icon = Icons.Outlined.CheckCircle
            )

            HomeQuickAction(
                modifier = Modifier.weight(1f),
                title = "Event",
                icon = Icons.Outlined.Event
            )

            HomeQuickAction(
                modifier = Modifier.weight(1f),
                title = "Schedule",
                icon = Icons.Outlined.CalendarMonth,
                onClick = onSchedule
            )

            HomeQuickAction(
                modifier = Modifier.weight(1f),
                title = "Schedule",
                icon = Icons.Outlined.CalendarMonth,
                onClick = onSchedule
            )
        }

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        // ================================================================
        // PINNED
        // ================================================================

        if (pinnedNote != null) {
            Text(
                text = "Pinned",
                color = ScrittoCream,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            PinnedNoteCard(
                title = pinnedNote.title.ifBlank {
                    "Untitled note"
                },
                preview = pinnedNote.content.ifBlank {
                    "No content"
                },
                onClick = {
                    onNoteSelected(pinnedNote.id)
                }
            )

            Spacer(
                modifier = Modifier.height(28.dp)
            )
        }

        // ================================================================
        // RECENT
        // ================================================================

        Text(
            text = "Recent",
            color = ScrittoCream,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        if (recentNotes.isEmpty()) {
            if (pinnedNote == null) {
                EmptyRecentState()
            } else {
                EmptyRecentState(
                    title = "No other notes yet",
                    subtitle = "Create another note from the + button."
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                recentNotes.forEach { note ->
                    RecentNoteCard(
                        title = note.title.ifBlank {
                            "Untitled note"
                        },
                        preview = note.content.ifBlank {
                            "No content"
                        },
                        onClick = {
                            onNoteSelected(note.id)
                        }
                    )
                }
            }
        }
    }
}

// ========================================================================
// COMMAND BAR
// ========================================================================

@Composable
private fun HomeCommandBar(
    value: String,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(
                RoundedCornerShape(18.dp)
            )
            .background(
                ScrittoSurface
            )
            .border(
                width = 1.dp,
                color = ScrittoBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(
                horizontal = 18.dp
            ),
        contentAlignment = Alignment.CenterStart
    ) {

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                color = ScrittoCream,
                fontSize = 16.sp
            ),
            cursorBrush = SolidColor(ScrittoAmber),
            decorationBox = { innerTextField ->

                if (value.isEmpty()) {
                    Text(
                        text = "Ask or command...",
                        color = ScrittoTextMuted,
                        fontSize = 16.sp
                    )
                }

                innerTextField()
            }
        )
    }
}

// ========================================================================
// QUICK ACTION
// ========================================================================

@Composable
private fun HomeQuickAction(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .height(86.dp)
            .clip(
                RoundedCornerShape(18.dp)
            )
            .background(
                ScrittoSurface
            )
            .border(
                width = 1.dp,
                color = ScrittoBorderSubtle,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ScrittoAmber,
            modifier = Modifier.size(21.dp)
        )

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        Text(
            text = title,
            color = ScrittoCream,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ========================================================================
// EMPTY RECENT STATE
// ========================================================================

@Composable
private fun EmptyRecentState(
    title: String = "Nothing here yet",
    subtitle: String = "Create something from the + button."
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(18.dp)
            )
            .border(
                width = 1.dp,
                color = ScrittoBorderSubtle,
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                ScrittoSurface.copy(alpha = 0.45f)
            )
            .padding(
                horizontal = 20.dp,
                vertical = 24.dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = title,
                color = ScrittoTextSecondary,
                fontSize = 15.sp
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = subtitle,
                color = ScrittoTextMuted,
                fontSize = 14.sp
            )
        }
    }
}

// ========================================================================
// PINNED NOTE
// ========================================================================

@Composable
private fun PinnedNoteCard(
    title: String,
    preview: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(18.dp)
            )
            .background(
                ScrittoSurfaceElevated
            )
            .border(
                width = 1.dp,
                color = ScrittoAmber.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = 18.dp,
                vertical = 18.dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = "Pinned note",
                    tint = ScrittoAmber,
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = title,
                    color = ScrittoCreamBright,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            Text(
                text = preview,
                color = ScrittoTextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                maxLines = 3
            )
        }
    }
}

// ========================================================================
// RECENT NOTE
// ========================================================================

@Composable
private fun RecentNoteCard(
    title: String,
    preview: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(18.dp)
            )
            .background(
                ScrittoSurfaceElevated
            )
            .border(
                width = 1.dp,
                color = ScrittoBorderSubtle,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                onClick = onClick
            )
            .padding(
                horizontal = 18.dp,
                vertical = 18.dp
            )
    ) {

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = title,
                color = ScrittoCreamBright,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            Text(
                text = preview,
                color = ScrittoTextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                maxLines = 2
            )
        }
    }
}