package data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class DataQueryResponse(
    val response: QueryResult
)

@Serializable
data class QueryResult(
    @SerialName("query_result") val queryResult: JsonArray,
    @SerialName("is_error") val isError: Boolean,
    @SerialName("error_message") val errorMessage: String = "",
    @SerialName("row_number") val rowNumber: Int = 0,
    @SerialName("column_number") val columnNumber: Int = 0,
)
