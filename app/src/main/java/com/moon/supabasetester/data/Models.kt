package com.moon.supabasetester.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: Long? = null,
    val title: String,
    val content: String = "",
    @SerialName("is_done") val isDone: Boolean = false,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ChatMessage(val sender: String, val text: String)

@Serializable
data class PresenceState(val user: String, val status: String = "online")
