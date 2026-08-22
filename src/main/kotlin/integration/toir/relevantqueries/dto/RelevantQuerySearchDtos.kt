package integration.toir.relevantqueries.dto

import kotlinx.serialization.Serializable

@Serializable
data class RelevantQuerySearchRequestDto(
    val question: String,
    val maxTemplates: Int,
    val maxSuccessfulQueries: Int,
)

@Serializable
data class RelevantQuerySearchResponseDto(
    val success: Boolean,
    val normalizedQuestion: String? = null,
    val templates: List<RelevantQueryTemplateDto>? = null,
    val successfulQueries: List<SuccessfulQueryDto>? = null,
    val error: RelevantQuerySearchErrorDto? = null,
)

@Serializable
data class RelevantQueryTemplateDto(
    val templateId: String,
    val name: String,
    val fullName: String,
    val normalizedIntent: String,
    val queryTemplate: String,
    val comment: String,
    val matchedQuestion: String,
    val similarityScore: Double,
)

@Serializable
data class SuccessfulQueryDto(
    val sourceQuestion: String,
    val queryText: String,
    val templateId: String,
    val similarityScore: Double,
)

@Serializable
data class RelevantQuerySearchErrorDto(
    val code: String,
    val message: String,
)
