@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.internship.scritto.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.data.model.ScheduleEvent
import com.internship.scritto.data.model.Task
import com.internship.scritto.data.repository.ScrittoStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

private data class TimelineItem(
    val time: Long,
    val title: String,
    val subtitle: String,
    val kind: Kind,
    val eventId: String? = null,
    val priority: Task.Priority? = null
) {
    enum class Kind { EVENT, CLASS, TASK }
}

@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    val events = ScrittoStore.events
    val tasks = ScrittoStore.tasks
    val today = startOfDay(System.currentTimeMillis())
    var selectedDay by remember { mutableStateOf(today) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteEvent by remember { mutableStateOf<ScheduleEvent?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val endOfDay = selectedDay + DAY_MILLIS
    val dayEvents = events.filter { it.startAt < endOfDay && it.endAt > selectedDay }.sortedBy { it.startAt }
    val dayTasks = tasks.filter { !it.completed && it.dueAt >= selectedDay && it.dueAt < endOfDay }.sortedBy { it.dueAt }

    val items = buildList {
        dayEvents.forEach { event ->
            add(TimelineItem(
                time = event.startAt,
                title = event.title,
                subtitle = event.location.ifBlank { if (event.type == ScheduleEvent.Type.CLASS) "Class" else "Event" },
                kind = if (event.type == ScheduleEvent.Type.CLASS) TimelineItem.Kind.CLASS else TimelineItem.Kind.EVENT,
                eventId = event.id
            ))
        }
        dayTasks.forEach { task ->
            add(TimelineItem(
                time = task.dueAt,
                title = task.title,
                subtitle = "Task • ${task.priority.name.lowercase().replaceFirstChar { it.uppercase() }}",
                kind = TimelineItem.Kind.TASK,
                priority = task.priority
            ))
        }
    }.sortedBy { it.time }

    val weekDays = (-2..2).map { offset ->
        Calendar.getInstance().apply {
            timeInMillis = selectedDay
            add(Calendar.DAY_OF_YEAR, offset)
        }.timeInMillis
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 110.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(selectedDay)),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        when (items.size) {
                            0 -> "Nothing scheduled for this day."
                            1 -> "1 thing on your schedule."
                            else -> "${items.size} things on your schedule."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }
                Box(
                    Modifier.padding(top = 2.dp).size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .clickable { showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Add, "Add to schedule", tint = MaterialTheme.colorScheme.primary) }
            }

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                weekDays.forEach { day ->
                    val selected = isSameDay(day, selectedDay)
                    val cal = Calendar.getInstance().apply { timeInMillis = day }
                    Column(
                        Modifier.width(54.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                            .clickable { selectedDay = startOfDay(day) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(day)).uppercase(),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(cal.get(Calendar.DAY_OF_MONTH).toString(), color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        if (isSameDay(day, today)) {
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isSameDay(selectedDay, today)) {
                val nowText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(8.dp))
                    Text("NOW • $nowText", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                }
                Spacer(Modifier.height(14.dp))
            }

            if (items.isEmpty()) {
                ScheduleEmptyState { showAdd = true }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("TODAY'S FLOW", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp) }
                    items(items, key = { "${it.kind}-${it.eventId ?: it.title}-${it.time}" }) { item ->
                        TimelineRow(item) {
                            item.eventId?.let { id -> events.firstOrNull { it.id == id }?.let { deleteEvent = it } }
                        }
                    }
                }
            }
        }

        if (showAdd) {
            AddSchedulePanel(
                context = context,
                initialDate = selectedDay,
                onDismiss = { showAdd = false },
                onCreated = {
                    showAdd = false
                    if (android.os.Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }

        deleteEvent?.let { event ->
            AlertDialog(
                onDismissRequest = { deleteEvent = null },
                title = { Text("Remove ${if (event.type == ScheduleEvent.Type.CLASS) "class" else "event"}?") },
                text = { Text("“${event.title}” will be removed from your schedule.") },
                confirmButton = {
                    TextButton(onClick = { ScrittoStore.deleteScheduleEvent(context, event.id); deleteEvent = null }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { deleteEvent = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, onDelete: () -> Unit) {
    val accent = when (item.kind) {
        TimelineItem.Kind.CLASS -> MaterialTheme.colorScheme.primary
        TimelineItem.Kind.EVENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
        TimelineItem.Kind.TASK -> when (item.priority) {
            Task.Priority.HIGH -> Color(0xFFE56B62)
            Task.Priority.MEDIUM -> MaterialTheme.colorScheme.primary
            Task.Priority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
            null -> MaterialTheme.colorScheme.primary
        }
    }
    val icon = when (item.kind) {
        TimelineItem.Kind.CLASS -> Icons.Outlined.Book
        TimelineItem.Kind.EVENT -> Icons.Outlined.Event
        TimelineItem.Kind.TASK -> Icons.Outlined.CheckCircle
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(58.dp)) {
            Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(item.time)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(SimpleDateFormat("a", Locale.getDefault()).format(Date(item.time)).uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.padding(top = 2.dp).size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.weight(1f).shadow(8.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = accent.copy(alpha = 0.16f), spotColor = accent.copy(alpha = 0.12f))
                .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (item.eventId != null) Icon(Icons.Outlined.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).clickable(onClick = onDelete))
            }
            Spacer(Modifier.height(7.dp))
            Text(item.subtitle, color = if (item.kind == TimelineItem.Kind.TASK && item.priority == Task.Priority.HIGH) Color(0xFFE6A23C) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ScheduleEmptyState(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(14.dp))
        Text("Your day is clear", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Add an event or class to shape the day.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text("Add to schedule", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onAdd))
    }
}

@Composable
private fun AddSchedulePanel(context: Context, initialDate: Long, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var startHour by remember { mutableStateOf<Int?>(null) }
    var startMinute by remember { mutableStateOf<Int?>(null) }
    var endHour by remember { mutableStateOf<Int?>(null) }
    var endMinute by remember { mutableStateOf<Int?>(null) }
    var type by remember { mutableStateOf(ScheduleEvent.Type.EVENT) }
    var repeatWeekly by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var startPicker by remember { mutableStateOf(false) }
    var endPicker by remember { mutableStateOf(false) }

    val canCreate = title.isNotBlank() && startHour != null && startMinute != null && endHour != null && endMinute != null

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 92.dp)
                .clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).clickable(enabled = false) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Add to schedule", color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Outlined.Close, "Close", modifier = Modifier.size(22.dp).clickable(onClick = onDismiss))
            }
            OutlinedTextField(title, { title = it }, placeholder = { Text("What is happening?") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            OutlinedTextField(location, { location = it }, placeholder = { Text("Room or location (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Outlined.LocationOn, null) })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScheduleChoice("Event", type == ScheduleEvent.Type.EVENT, Modifier.weight(1f)) { type = ScheduleEvent.Type.EVENT }
                ScheduleChoice("Class", type == ScheduleEvent.Type.CLASS, Modifier.weight(1f)) { type = ScheduleEvent.Type.CLASS }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScheduleChoice(formatScheduleDate(date), true, Modifier.weight(1f)) { datePicker = true }
                ScheduleChoice(if (startHour != null) formatTime(startHour!!, startMinute!!) else "Start time", startHour != null, Modifier.weight(1f)) { startPicker = true }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScheduleChoice(if (endHour != null) formatTime(endHour!!, endMinute!!) else "End time", endHour != null, Modifier.weight(1f)) { endPicker = true }
                ScheduleChoice("15 min reminder", true, Modifier.weight(1f)) {}
            }

            if (type == ScheduleEvent.Type.CLASS) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .clickable { repeatWeekly = !repeatWeekly }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (repeatWeekly) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Repeat weekly", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                }
            }

            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Alarm, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text("A reminder will be sent 15 minutes before.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val start = buildDateTime(date, startHour!!, startMinute!!)
                    val endBase = buildDateTime(date, endHour!!, endMinute!!)
                    val end = if (endBase <= start) endBase + DAY_MILLIS else endBase
                    val count = if (type == ScheduleEvent.Type.CLASS && repeatWeekly) 8 else 1
                    repeat(count) { index ->
                        val offset = index * 7L * DAY_MILLIS
                        ScrittoStore.createScheduleEvent(context, title, location, start + offset, end + offset, type, 15)
                    }
                    onCreated()
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (repeatWeekly) "Add class series" else "Add to schedule", fontWeight = FontWeight.SemiBold) }
        }
    }

    if (datePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = startOfDay(it) }; datePicker = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state, showModeToggle = false) }
    }

    LaunchedEffect(startPicker) {
        if (!startPicker) return@LaunchedEffect
        val now = Calendar.getInstance()
        TimePickerDialog(context, { _, h, m -> startHour = h; startMinute = m; startPicker = false }, startHour ?: now.get(Calendar.HOUR_OF_DAY), startMinute ?: now.get(Calendar.MINUTE), false).show()
    }

    LaunchedEffect(endPicker) {
        if (!endPicker) return@LaunchedEffect
        val now = Calendar.getInstance()
        TimePickerDialog(context, { _, h, m -> endHour = h; endMinute = m; endPicker = false }, endHour ?: ((startHour ?: now.get(Calendar.HOUR_OF_DAY)) + 1).coerceAtMost(23), endMinute ?: (startMinute ?: now.get(Calendar.MINUTE)), false).show()
    }
}

@Composable
private fun ScheduleChoice(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun buildDateTime(date: Long, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
    timeInMillis = date
    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun formatTime(hour: Int, minute: Int): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }.time)

private fun formatScheduleDate(timestamp: Long): String = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(timestamp))

private fun isSameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
