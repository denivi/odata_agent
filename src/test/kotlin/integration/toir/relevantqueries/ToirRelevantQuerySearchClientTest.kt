package integration.toir.relevantqueries

import application.relevantqueries.RelevantQuerySearchResult
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ToirRelevantQuerySearchClientTest {
    @Test
    fun `должен отправить запрос и преобразовать ответ в Found`() = runBlocking {
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/similar-successful-queries", request.url.encodedPath)

            respond(
                content = """
                {
                  "success": true,
                  "normalizedQuestion": "количество объект_обслуживания",
                  "templates": [],
                  "successfulQueries": [
                    {
                      "sourceQuestion": "Сколько объектов?",
                      "queryText": "ВЫБРАТЬ КОЛИЧЕСТВО(*)",
                      "templateId": "template-1",
                      "similarityScore": 1.0
                    }
                  ]
                }
            """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(
                    Json,
                    ContentType.Application.Json
                )
            }
        }
        val toirQueryClient = ToirRelevantQuerySearchClient(
            httpClient = httpClient,
            baseUrl = "http://test.local",
        )

        val result = toirQueryClient.search(
            question = "Сколько объектов?",
            maxTemplates = 5,
            maxSuccessfulQueries = 5
        )

        val found = assertIs<RelevantQuerySearchResult.Found>(result)
        assertEquals("количество объект_обслуживания", found.normalizedQuestion)
        assertEquals(0, found.templates.size)
        assertEquals(1, found.successfulQueries.size)
        assertEquals(
            "ВЫБРАТЬ КОЛИЧЕСТВО(*)",
            found.successfulQueries.first().queryText,
        )
    }

    @Test
    fun `должен проверить ответ 400 INVALID_REQUEST`() = runBlocking {

        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/similar-successful-queries", request.url.encodedPath)

            respond(
                content = """
                {
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Не заполнен вопрос"
  }
}
            """.trimIndent(),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(
                    Json,
                    ContentType.Application.Json
                )
            }
            expectSuccess = false
        }
        val toirQueryClient = ToirRelevantQuerySearchClient(
            httpClient = httpClient,
            baseUrl = "http://test.local",
        )

        val result = toirQueryClient.search(
            question = "Сколько объектов?",
            maxTemplates = 5,
            maxSuccessfulQueries = 5
        )

        val error = assertIs<RelevantQuerySearchResult.ValidationError>(result)
        assertEquals("INVALID_REQUEST", error.code)
        assertEquals("Не заполнен вопрос", error.message)
    }

    @Test
    fun `должен проверить ответ 500 INTERNAL_SERVER_ERROR`() = runBlocking {

        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/similar-successful-queries", request.url.encodedPath)

            respond(
                content = """
                {
  "success": false,
  "error": {
    "code": "INTERNAL_SERVER_ERROR",
    "message": "ошибка сервиса"
  }
}
            """.trimIndent(),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(
                    Json,
                    ContentType.Application.Json
                )
            }
            expectSuccess = false
        }
        val toirQueryClient = ToirRelevantQuerySearchClient(
            httpClient = httpClient,
            baseUrl = "http://test.local",
        )

        val result = toirQueryClient.search(
            question = "Сколько объектов?",
            maxTemplates = 5,
            maxSuccessfulQueries = 5
        )

        val error = assertIs<RelevantQuerySearchResult.IntegrationError>(result)
        assertEquals("INTERNAL_SERVER_ERROR", error.code)
        assertEquals("ошибка сервиса", error.message)
    }

    @Test
    fun `должен вернуть IntegrationError при некорректном JSON ответа`() = runBlocking {
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/similar-successful-queries", request.url.encodedPath)

            respond(
                content = "{ это не JSON",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(
                    Json,
                    ContentType.Application.Json
                )
            }
        }
        val toirQueryClient = ToirRelevantQuerySearchClient(
            httpClient = httpClient,
            baseUrl = "http://test.local",
        )

        val result = toirQueryClient.search(
            question = "Сколько объектов?",
            maxTemplates = 5,
            maxSuccessfulQueries = 5
        )

        val error = assertIs<RelevantQuerySearchResult.IntegrationError>(result)

        assertEquals("CLIENT_INTEGRATION_ERROR", error.code)
        assertEquals(
            "Не удалось выполнить поиск релевантных запросов.",
            error.message,
        )
    }
}