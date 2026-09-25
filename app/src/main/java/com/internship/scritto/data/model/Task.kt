package com.internship.scritto.data.model

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val dueAt: Long,
    val priority: Priority = Priority.MEDIUM,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    enum class Priority {
        LOW,
        MEDIUM,
        HIGH
    }
}
