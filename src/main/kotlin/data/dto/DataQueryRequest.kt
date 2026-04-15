package data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DataQueryRequest(
    val request: TextQuery
)

@Serializable
data class TextQuery(
    @SerialName("text_query") val textQuery: String
)
