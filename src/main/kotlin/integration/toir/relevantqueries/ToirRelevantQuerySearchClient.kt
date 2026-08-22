package integration.toir.relevantqueries

import application.relevantqueries.RelevantQuerySearchResult
import integration.toir.relevantqueries.dto.RelevantQuerySearchRequestDto
import integration.toir.relevantqueries.dto.RelevantQuerySearchResponseDto
import integration.toir.relevantqueries.mapper.toRelevantQuerySearchResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException

class ToirRelevantQuerySearchClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun search(
        question: String,
        maxTemplates: Int,
        maxSuccessfulQueries: Int,
    ): RelevantQuerySearchResult {

        val requestDto = RelevantQuerySearchRequestDto(
            question = question,
            maxTemplates = maxTemplates,
            maxSuccessfulQueries = maxSuccessfulQueries,
        )

        return try {
            val response = httpClient.post("$baseUrl/similar-successful-queries") {
                contentType(ContentType.Application.Json)
                setBody(requestDto)
            }

            val responseDto = response.body<RelevantQuerySearchResponseDto>()

            responseDto.toRelevantQuerySearchResult()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            RelevantQuerySearchResult.IntegrationError(
                code = "CLIENT_INTEGRATION_ERROR",
                message = "Не удалось выполнить поиск релевантных запросов.",
            )
        }
    }
}