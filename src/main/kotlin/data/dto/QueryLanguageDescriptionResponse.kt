package data.dto

import kotlinx.serialization.Serializable

@Serializable
data class QueryLanguageDescriptionResponse(
    val response: LanguageDescriptionResponse
)

@Serializable
data class LanguageDescriptionResponse(
    val kind: String,
    val doc: String,
    val version: String,
    val id: String,
    val title: String,
    val description: String,
    val exemples: String
)