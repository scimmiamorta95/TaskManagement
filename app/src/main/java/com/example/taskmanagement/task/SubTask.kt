package com.example.taskmanagement.task

data class SubTask(
    val name: String = "",
    val description: String = "",
    val createdBy: String?= "",
    val assignedTo: String? = null,
    val deadline: String = "",
    val priority: Int = 0, // 1: alta, 2: media, 3: bassa
    var status: String = "todo",
    var progress: Int = 0,
)