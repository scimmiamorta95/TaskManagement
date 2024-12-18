package com.example.taskmanagement.chat

data class Chat(
    var id: String = "",
    val participants: List<String> = listOf(),
    val lastMessage: Message? = null
)
