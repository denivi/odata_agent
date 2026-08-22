package integration.toir.relevantqueries.mapper

import application.relevantqueries.RelevantQuerySearchResult
import integration.toir.relevantqueries.dto.RelevantQuerySearchErrorDto
import integration.toir.relevantqueries.dto.RelevantQuerySearchResponseDto
import integration.toir.relevantqueries.dto.RelevantQueryTemplateDto
import integration.toir.relevantqueries.dto.SuccessfulQueryDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RelevantQuerySearchResponseMapperTest {

    @Test
    fun `должен преобразовать найденные данные в Found`() {
        // Arrange
        val response = RelevantQuerySearchResponseDto(
            success = true,
            normalizedQuestion = "количество объект_обслуживания",
            templates = listOf(
                RelevantQueryTemplateDto(
                    templateId = "template-1",
                    name = "Подсчёт",
                    fullName = "Подсчёт элементов",
                    normalizedIntent = "COUNT элементы",
                    queryTemplate = "ВЫБРАТЬ КОЛИЧЕСТВО(*)",
                    comment = "Тест",
                    matchedQuestion = "Сколько объектов?",
                    similarityScore = 1.0,
                ),
            ),
            successfulQueries = listOf(
                SuccessfulQueryDto(
                    sourceQuestion = "Сколько объектов?",
                    queryText = "ВЫБРАТЬ КОЛИЧЕСТВО(*)",
                    templateId = "template-1",
                    similarityScore = 1.0,
                ),
            ),
        )

        // Act
        val result = response.toRelevantQuerySearchResult()

        // Assert
        val found = assertIs<RelevantQuerySearchResult.Found>(result)
        assertEquals("количество объект_обслуживания", found.normalizedQuestion)
        assertEquals(1, found.templates.size)
        assertEquals("template-1", found.templates.first().templateId)
        assertEquals(1, found.successfulQueries.size)
        assertEquals(
            "ВЫБРАТЬ КОЛИЧЕСТВО(*)",
            found.successfulQueries.first().queryText,
        )

    }

    @Test
    fun `должен преобразовать ошибку валидации в ValidationError`() {
        val response = RelevantQuerySearchResponseDto(
            success = false,
            error = RelevantQuerySearchErrorDto(
                code = "INVALID_REQUEST",
                message = "Не заполнен вопрос"
            )
        )

        val result = response.toRelevantQuerySearchResult()
        val validationError = assertIs<RelevantQuerySearchResult.ValidationError>(result)
        assertEquals("INVALID_REQUEST", validationError.code)
        assertEquals("Не заполнен вопрос", validationError.message)
    }

    @Test
    fun `должен преобразовать не найденные данные в NotFound`(){
        val response = RelevantQuerySearchResponseDto(
            success = true,
            normalizedQuestion = "количество дефектов",
            templates = listOf(),
            successfulQueries = listOf()
        )
        val result = response.toRelevantQuerySearchResult()
        assertEquals(RelevantQuerySearchResult.NotFound, result)
    }

    @Test
    fun `должен преобразовать ошибку сервера в IntegrationError`(){
        val response = RelevantQuerySearchResponseDto(
            success = false,
            error = RelevantQuerySearchErrorDto(
                code = "INTERNAL_SERVER_ERROR",
                message = "внутренняя ошибка сервера"
            )
        )

        val result = response.toRelevantQuerySearchResult()
        val integrationError = assertIs<RelevantQuerySearchResult.IntegrationError>(result)
        assertEquals("INTERNAL_SERVER_ERROR", integrationError.code)
        assertEquals("внутренняя ошибка сервера", integrationError.message)
    }


    @Test
    fun `должен преобразовать неизвестный код ошибки в UnexpectedError`(){
        val response = RelevantQuerySearchResponseDto(
            success = false,
            error = RelevantQuerySearchErrorDto(
                code = "UNKNOWN_ERROR",
                message = "внутренняя ошибка сервера"
            )
        )

        val result = response.toRelevantQuerySearchResult()
        val unexpectedError = assertIs<RelevantQuerySearchResult.UnexpectedError>(result)
        assertEquals("внутренняя ошибка сервера", unexpectedError.message)
    }

    @Test
    fun `должен преобразовать ошибку без описания в UnexpectedError`(){
        val response = RelevantQuerySearchResponseDto(
            success = false
        )

        val result = response.toRelevantQuerySearchResult()
        val unexpectedError = assertIs<RelevantQuerySearchResult.UnexpectedError>(result)
        assertEquals("Unknown error", unexpectedError.message)
    }

}