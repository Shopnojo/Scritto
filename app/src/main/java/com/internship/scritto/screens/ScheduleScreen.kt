@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.internship.scritto.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.data.model.Task
import com.internship.scritto.data.repository.ScrittoStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    val tasks = ScrittoStore.tasks

    var showAddTask by remember { mutableStateOf(false) }
    var taskForDelete by remember { mutableStateOf<Task?>(null) }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { }

    val now = System.currentTimeMillis()
    val completedRetention = 24L * 60L * 60L * 1000L

    val pending = tasks
        .filterNot { it.completed }
        .sortedWith(
            compareBy<Task> {
                when (it.priority) {
                    Task.Priority.HIGH -> 0
                    Task.Priority.MEDIUM -> 1
                    Task.Priority.LOW -> 2
                }
            }.thenBy { it.dueAt }
        )

    val completed = tasks
        .filter {
            it.completed &&
                it.completedAt != null &&
                now - it.completedAt < completedRetention
        }
        .sortedWith(
            compareByDescending<Task> { it.completedAt ?: 0L }
        )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 72.dp,
                    bottom = 110.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tasks",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (pending.isEmpty()) {
                            "Nothing waiting for you."
                        } else {
                            pending.size.toString() +
                                if (pending.size == 1) {
                                    " task to keep moving."
                                } else {
                                    " tasks to keep moving."
                                }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        )
                        .clickable { showAddTask = true },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add task",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (pending.isEmpty() && completed.isEmpty()) {
                EmptyTaskState(
                    onAddTask = { showAddTask = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (pending.isNotEmpty()) {
                        item {
                            SectionLabel(
                                text = if (
                                    pending.any {
                                        it.dueAt < System.currentTimeMillis()
                                    }
                                ) {
                                    "Needs attention"
                                } else {
                                    "Upcoming"
                                }
                            )
                        }

                        items(
                            items = pending,
                            key = { it.id }
                        ) { task ->
                            TaskRow(
                                task = task,
                                onToggle = {
                                    ScrittoStore.setTaskCompleted(
                                        context,
                                        task.id,
                                        true
                                    )
                                },
                                onDelete = {
                                    taskForDelete = task
                                }
                            )
                        }
                    }

                    if (completed.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            SectionLabel(text = "Completed")
                        }

                        items(
                            items = completed,
                            key = { it.id }
                        ) { task ->
                            TaskRow(
                                task = task,
                                onToggle = {
                                    ScrittoStore.setTaskCompleted(
                                        context,
                                        task.id,
                                        false
                                    )
                                },
                                onDelete = {
                                    taskForDelete = task
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAddTask) {
            AddTaskPanel(
                context = context,
                onDismiss = { showAddTask = false },
                onCreated = {
                    showAddTask = false

                    if (
                        android.os.Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                }
            )
        }

        taskForDelete?.let { task ->
            DeleteTaskDialog(
                task = task,
                onDismiss = { taskForDelete = null },
                onConfirm = {
                    ScrittoStore.deleteTask(context, task.id)
                    taskForDelete = null
                }
            )
        }
    }
}

@Composable
private fun AddTaskPanel(
    context: Context,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(startOfTodayMillis()) }
    var selectedHour by remember { mutableStateOf<Int?>(null) }
    var selectedMinute by remember { mutableStateOf<Int?>(null) }
    var priority by remember { mutableStateOf(Task.Priority.MEDIUM) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val canCreate =
        title.isNotBlank() &&
            selectedDate != null &&
            selectedHour != null &&
            selectedMinute != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background.copy(alpha = 0.78f)
            )
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 92.dp
                )
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "New task",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )

            TaskInput(
                value = title,
                onValueChange = { title = it },
                hint = "What needs to get done?"
            )

            TaskInput(
                value = description,
                onValueChange = { description = it },
                hint = "Add a note (optional)"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DateTimeButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CalendarToday,
                    text = selectedDate?.let(::formatDate) ?: "Due date",
                    selected = true,
                    onClick = { showDatePicker = true }
                )

                DateTimeButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Schedule,
                    text = if (
                        selectedHour != null &&
                        selectedMinute != null
                    ) {
                        formatTime(selectedHour!!, selectedMinute!!)
                    } else {
                        "Due time"
                    },
                    selected = selectedHour != null,
                    onClick = { showTimePicker = true }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriorityChip(
                    label = "Low",
                    selected = priority == Task.Priority.LOW,
                    onClick = { priority = Task.Priority.LOW }
                )
                PriorityChip(
                    label = "Medium",
                    selected = priority == Task.Priority.MEDIUM,
                    onClick = { priority = Task.Priority.MEDIUM }
                )
                PriorityChip(
                    label = "High",
                    selected = priority == Task.Priority.HIGH,
                    onClick = { priority = Task.Priority.HIGH }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.size(10.dp))

                Text(
                    text = "A notification will be sent when this task is due.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    val dueAt = buildDueTime(
                        selectedDate!!,
                        selectedHour!!,
                        selectedMinute!!
                    )

                    ScrittoStore.createTask(
                        context = context,
                        title = title,
                        description = description,
                        dueAt = dueAt,
                        priority = priority
                    )

                    onCreated()
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Text(
                    text = "Create task",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState =
            androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis =
                    selectedDate
            )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate = it }
                        showDatePicker = false
                    }
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false }
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
        }
    }

    if (showTimePicker) {
        val now = Calendar.getInstance()

        androidx.compose.runtime.LaunchedEffect(Unit) {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    selectedHour = hour
                    selectedMinute = minute
                    showTimePicker = false
                },
                selectedHour ?: now.get(Calendar.HOUR_OF_DAY),
                selectedMinute ?: now.get(Calendar.MINUTE),
                false
            ).show()
        }
        showTimePicker = false
    }
}

@Composable
private fun TaskInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = hint.contains("What"),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun DateTimeButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (selected) 0.08f else 0.05f
                )
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PriorityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        },
        animationSpec = tween(180),
        label = "priority_background"
    )

    Text(
        text = label,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 14.dp,
                vertical = 9.dp
            )
    )
}

@Composable
private fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val completedAlpha by animateFloatAsState(
        targetValue = if (task.completed) 0.58f else 1f,
        animationSpec = tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        label = "task_alpha"
    )

    val overdue =
        !task.completed && task.dueAt < System.currentTimeMillis()

    val glowColor = when {
        overdue -> androidx.compose.ui.graphics.Color(0xFFFF4D4D)
        task.completed -> androidx.compose.ui.graphics.Color(0xFF8DFF9A)
        else -> androidx.compose.ui.graphics.Color(0xFFFFB12B)
    }

    val glowElevation = when {
        overdue -> 12.dp
        task.completed -> 8.dp
        else -> 8.dp
    }

    val taskShape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = glowElevation,
                shape = taskShape,
                clip = false,
                ambientColor = glowColor.copy(
                    alpha = if (overdue) 0.50f else 0.28f
                ),
                spotColor = glowColor.copy(
                    alpha = if (overdue) 0.42f else 0.22f
                )
            )
            .clip(taskShape)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            )
            .clickable(onClick = onToggle)
            .padding(16.dp)
            .graphicsLayer {
                alpha = completedAlpha
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.completed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.08f
                            )
                        }
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (task.completed) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = task.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = task.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(7.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = task.priority.name.lowercase().replaceFirstChar {
                            it.uppercase()
                        },
                        color = when (task.priority) {
                            Task.Priority.HIGH -> MaterialTheme.colorScheme.primary
                            Task.Priority.MEDIUM -> MaterialTheme.colorScheme.onSurfaceVariant
                            Task.Priority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.70f
                            )
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.55f
                        ),
                        fontSize = 11.sp
                    )

                    Text(
                        text = dueLabel(task.dueAt),
                        color = if (overdue) {
                            androidx.compose.ui.graphics.Color(0xFFFF5A5A)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 12.sp,
                        fontWeight = if (overdue) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        maxLines = 1
                    )
                }

                if (!task.completed) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "• reminder on",
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.72f
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = "•••",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDelete)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun EmptyTaskState(
    onAddTask: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✓",
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
            fontSize = 42.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Nothing on your list",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Create a task and Scritto will remind you when it is due.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Add your first task",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onAddTask)
                .padding(
                    horizontal = 16.dp,
                    vertical = 11.dp
                )
        )
    }
}

@Composable
private fun DeleteTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete task?")
        },
        text = {
            Text(
                "“" + task.title +
                    "” will be removed from Scritto."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun buildDueTime(
    dateMillis: Long,
    hour: Int,
    minute: Int
): Long {
    return Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat(
        "EEE, d MMM",
        Locale.getDefault()
    ).format(Date(timestamp))
}

private fun formatTime(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }

    return SimpleDateFormat(
        "h:mm a",
        Locale.getDefault()
    ).format(calendar.time)
}

private fun dueLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()

    return when {
        timestamp < now -> {
            "Overdue • " +
                formatDate(timestamp) +
                " " +
                formatTimeFromTimestamp(timestamp)
        }

        isSameDay(timestamp, now) -> {
            "Today • " + formatTimeFromTimestamp(timestamp)
        }

        isSameDay(
            timestamp,
            now + 24 * 60 * 60 * 1000L
        ) -> {
            "Tomorrow • " + formatTimeFromTimestamp(timestamp)
        }

        else -> {
            formatDate(timestamp) +
                " • " +
                formatTimeFromTimestamp(timestamp)
        }
    }
}

private fun formatTimeFromTimestamp(timestamp: Long): String {
    return SimpleDateFormat(
        "h:mm a",
        Locale.getDefault()
    ).format(Date(timestamp))
}

private fun isSameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }

    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}


private fun startOfTodayMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
