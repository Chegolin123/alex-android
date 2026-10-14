package com.alex.app.data

/** Сообщение чата. */
data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isVoice: Boolean = false,
)
