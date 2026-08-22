package integration.toir.relevantqueries.mapper

import application.relevantqueries.RelevantQuerySearchResult
import application.relevantqueries.RelevantQueryTemplate
import application.relevantqueries.SuccessfulQuery
import integration.toir.relevantqueries.dto.RelevantQuerySearchResponseDto
import integration.toir.relevantqueries.dto.RelevantQueryTemplateDto
import integration.toir.relevantqueries.dto.SuccessfulQueryDto

fun RelevantQuerySearchResponseDto.toRelevantQuerySearchResult(): RelevantQuerySearchResult{

    if (success){
        val question = normalizedQuestion
            ?: return RelevantQuerySearchResult.UnexpectedError(message = error?.message ?: "Unknown error")
        val templateDto = templates
            ?: return RelevantQuerySearchResult.UnexpectedError(message = error?.message ?: "Unknown error")
        val queriesDto = successfulQueries
            ?: return RelevantQuerySearchResult.UnexpectedError(message = error?.message ?: "Unknown error")
        if(templateDto.isEmpty() && queriesDto.isEmpty()){
            return RelevantQuerySearchResult.NotFound
        }
        return RelevantQuerySearchResult.Found(
            normalizedQuestion = question,
            templates = templateDto.map { it.toModel() },
            successfulQueries = queriesDto.map { it.toModel() })
    }

    val toolError = error
        ?: return RelevantQuerySearchResult.UnexpectedError(message = "Unknown error")

    when(toolError.code){
        "INVALID_REQUEST" -> return RelevantQuerySearchResult.ValidationError(
            code = toolError.code,
            message = toolError.message)
        "INTERNAL_SERVER_ERROR" -> return RelevantQuerySearchResult.IntegrationError(
            code = toolError.code,
            message = toolError.message
        )
        else -> {
           return RelevantQuerySearchResult.UnexpectedError(
                message = toolError.message
            )
        }
    }

}

private fun RelevantQueryTemplateDto.toModel(): RelevantQueryTemplate{
    return RelevantQueryTemplate(
        templateId = templateId,
        name = name,
        fullName = fullName,
        normalizedIntent = normalizedIntent,
        queryTemplate = queryTemplate,
        comment = comment,
        matchedQuestion = matchedQuestion,
        similarityScore = similarityScore,

    )
}
private fun SuccessfulQueryDto.toModel(): SuccessfulQuery{
    return SuccessfulQuery(
        sourceQuestion = sourceQuestion,
        queryText = queryText,
        templateId = templateId,
        similarityScore = similarityScore,
    )
}