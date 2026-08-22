package data.tools

import integration.toir.relevantqueries.ToirRelevantQuerySearchClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class QueryToolSetTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `должен передать вопрос и лимиты в поиск успешных запросов`() = runBlocking {
        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/similar-successful-queries", request.url.encodedPath)

            val requestBody = request.body.toByteArray().decodeToString()
            val requestJson = Json.parseToJsonElement(requestBody).jsonObject

            assertEquals(
                "Сколько объектов обслуживания в системе?",
                requestJson["question"]!!.jsonPrimitive.content,
            )
            assertEquals(3, requestJson["maxTemplates"]!!.jsonPrimitive.int)
            assertEquals(5, requestJson["maxSuccessfulQueries"]!!.jsonPrimitive.int)

            respond(
                content = """
        {
          "success": true,
          "normalizedQuestion": "количество объект_обслуживания",
          "templates": [],
          "successfulQueries": [
            {
              "sourceQuestion": "Сколько объектов обслуживания в системе?",
              "queryText": "ВЫБРАТЬ КОЛИЧЕСТВО(ОбъектыОбслуживания.Код)",
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
                json(json)
            }
        }

        val searchClient = ToirRelevantQuerySearchClient(
            httpClient = httpClient,
            baseUrl = "http://test.local",
        )

        val toolSet = QueryToolSet(
            baseUrl = "http://test.local",
            relevantQuerySearchClient = searchClient,
        )

        val result = toolSet.getSimilarSuccessfulQueries(
            "Сколько объектов обслуживания в системе?",
        )

        assertContains(result, "Статус: найдено")
        assertContains(result, "Финальный ответ пользователю: запрещён")
        assertContains(result, "ВЫБРАТЬ КОЛИЧЕСТВО(ОбъектыОбслуживания.Код)")
    }
}