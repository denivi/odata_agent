package org.example.data.tools

import kotlinx.serialization.Serializable

@Serializable
data class QueryValidationResult(
    val isValid: Boolean,
    val errors: List<String>)
