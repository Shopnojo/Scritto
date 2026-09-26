package com.internship.scritto.data.model

data class ScheduleEvent(
    val id: String,
    val title: String,
    val location: String = "",
    val startAt: Long,
    val endAt: Long,
    val type: Type = Type.EVENT,
    val reminderMinutes: Int = 15,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class Type {
        EVENT,
        CLASS
    }
}
