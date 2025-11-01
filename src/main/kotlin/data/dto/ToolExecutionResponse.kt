package org.example.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ToolExecutionResponse(
    val toolName: String,
    val status: String,
    val result: String
)
