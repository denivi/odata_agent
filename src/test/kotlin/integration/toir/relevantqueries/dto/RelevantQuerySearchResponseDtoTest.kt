package integration.toir.relevantqueries.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelevantQuerySearchResponseDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `должен десериализовать успешный ответ поиска`() {
        val rawResponse = """
            {
              "success": true,
              "normalizedQuestion": "количество объект_обслуживания",
              "templates": [
                {
                  "templateId": "template-1",
                  "name": "Подсчет количества элементов с отбором",
                  "fullName": "Подсчет количества элементов по условию",
                  "normalizedIntent": "COUNT элементы FILTER параметр",
                  "queryTemplate": "ВЫБРАТЬ КОЛИЧЕСТВО(Ссылка)",
                  "comment": "Тестовый шаблон",
                  "matchedQuestion": "Сколько объектов обслуживания в системе?",
                  "similarityScore": 1.0
                }
              ],
              "successfulQueries": [
                {
                  "sourceQuestion": "Сколько объектов обслуживания в системе?",
                  "queryText": "ВЫБРАТЬ КОЛИЧЕСТВО(ОбъектыОбслуживания.Код)",
                  "templateId": "template-1",
                  "similarityScore": 1.0
                }
              ]
            }
        """.trimIndent()

        val response =
            json.decodeFromString<RelevantQuerySearchResponseDto>(rawResponse)

        assertTrue(response.success)
        assertEquals(
            "количество объект_обслуживания",
            response.normalizedQuestion
        )

        val templates = assertNotNull(response.templates)
        assertEquals(1, templates.size)
        assertEquals("template-1", templates.first().templateId)

        val successfulQueries = assertNotNull(response.successfulQueries)
        assertEquals(1, successfulQueries.size)
        assertEquals(
            "ВЫБРАТЬ КОЛИЧЕСТВО(ОбъектыОбслуживания.Код)",
            successfulQueries.first().queryText
        )

        assertNull(response.error)
    }

    @Test
    fun `должен десериализовать ошибку валидации`() {
        val rawResponse = """
            {
              "success": false,
              "error": {
                "code": "INVALID_REQUEST",
                "message": "Не заполнен или некорректно задан вопрос пользователя."
              }
            }
        """.trimIndent()

        val response =
            json.decodeFromString<RelevantQuerySearchResponseDto>(rawResponse)

        assertFalse(response.success)

        val error = assertNotNull(response.error)
        assertEquals("INVALID_REQUEST", error.code)

        assertNull(response.normalizedQuestion)
        assertNull(response.templates)
        assertNull(response.successfulQueries)
    }
}