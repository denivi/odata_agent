package application.relevantqueries

sealed interface RelevantQuerySearchResult{

    data class Found(
        val normalizedQuestion: String,
       val  templates: List<RelevantQueryTemplate>,
        val successfulQueries: List<SuccessfulQuery>,
    ) : RelevantQuerySearchResult

    data object NotFound: RelevantQuerySearchResult

    data class ValidationError(
        val code: String,
        val message: String,
    ) : RelevantQuerySearchResult

    data class IntegrationError(
        val code: String,
        val message: String,
    ) : RelevantQuerySearchResult

    data class UnexpectedError(
        val message: String,
    ) : RelevantQuerySearchResult

}

data class RelevantQueryTemplate(
    val templateId: String,
    val name: String,
    val fullName: String,
    val normalizedIntent: String,
    val queryTemplate: String,
    val comment: String,
    val matchedQuestion: String,
    val similarityScore: Double,
)

data class SuccessfulQuery(
    val sourceQuestion: String,
    val queryText: String,
    val templateId: String,
    val similarityScore: Double,
)