package com.example.taskmanagement.task

data class Task(
    val name: String = "",
    val description: String = "",
    val assignedTo: String? = null,
    val deadline: String = "",
    var progress: Int = 0,
    val createdBy: String? = "",
    var nSubTask: Int = 0,
)