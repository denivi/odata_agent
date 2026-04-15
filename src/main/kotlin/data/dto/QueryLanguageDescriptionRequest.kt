package data.dto

import kotlinx.serialization.Serializable

@Serializable
data class QueryLanguageDescriptionRequest(
    val request: DescriptionRequestId
)

@Serializable
data class DescriptionRequestId(
    val id: String
)