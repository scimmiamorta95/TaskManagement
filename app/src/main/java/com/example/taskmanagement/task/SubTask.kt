package com.example.taskmanagement.task

data class SubTask(
    val name: String = "",
    val description: String = "",
    val createdBy: String? = "",
    val assignedTo: String? = null,
    val deadline: String = "",
    val priority: Int = 0,
    var status: Int = 0,
    var progress: Int = 0,
)