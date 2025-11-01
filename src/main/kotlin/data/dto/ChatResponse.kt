package org.example.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val success: Boolean,
    val answer: String? = null,
    val error: String? = null
)
