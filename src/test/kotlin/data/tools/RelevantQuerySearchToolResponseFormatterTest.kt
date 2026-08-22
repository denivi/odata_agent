package data.tools

import application.relevantqueries.RelevantQuerySearchResult
import application.relevantqueries.RelevantQueryTemplate
import application.relevantqueries.SuccessfulQuery
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class RelevantQuerySearchToolResponseFormatterTest {

    @Test
    fun `должен проверить ответ NotFound`() {

        val result = RelevantQuerySearchResult.NotFound
        val formatResult = RelevantQuerySearchToolResponseFormatter.format(result)
        assertContains(formatResult, "Статус: не найдено")
        assertContains(formatResult, "Финальный ответ пользователю: запрещён")
        assertContains(formatResult, "Нужно выполнить новый executeQuery: да")
        assertContains(formatResult, "Сохранённые релевантные запросы не найдены.")
        assertContains(
            formatResult,
            "Продолжи обычный путь: получи метаданные, сформируй и выполни новый executeQuery.",
        )
        assertContains(
            formatResult,
            "Не отвечай пользователю без успешного executeQuery.",
        )
    }

    @Test
    fun `должен проверить ответ Found`() {

        val result = RelevantQuerySearchResult.Found(
            normalizedQuestion = "количество объект_обслуживания",
            templates = listOf(
                RelevantQueryTemplate(
                    templateId = "template-1",
                    name = "Подсчет количества элементов с отбором",
                    fullName = "Подсчет количества элементов по условию",
                    normalizedIntent = "COUNT элементы FILTER параметр",
                    queryTemplate = "ВЫБРАТЬ КОЛИЧЕСТВО(Ссылка)",
                    comment = "Тестовый шаблон",
                    matchedQuestion = "Сколько объектов обслуживания в системе?",
                    similarityScore = 1.0
                )
            ),
            successfulQueries = listOf(
                SuccessfulQuery(
                    sourceQuestion = "Сколько объектов обслуживания в системе?",
                    queryText = "ВЫБРАТЬ КОЛИЧЕСТВО(ОбъектыОбслуживания.Код)",
                    templateId = "template-1",
                    similarityScore = 1.0
                )
            )
        )

        val formatResult = RelevantQuerySearchToolResponseFormatter.format(result)
        assertContains(formatResult, "Статус: найдено")
        assertContains(formatResult, "Финальный ответ пользователю: запрещён")
        assertContains(formatResult, "Нужно выполнить новый executeQuery: да")
        assertContains(formatResult, "Нормализованный вопрос: количество объект_обслуживания")
        assertContains(formatResult, "Найдено шаблонов: 1")
        assertContains(formatResult, "Найдено успешных запросов: 1")

        assertContains(formatResult, "ID шаблона: template-1")
        assertContains(formatResult, "Наименование: Подсчет количества элементов с отбором")
        assertContains(formatResult, "Подходящая формулировка: Сколько объектов обслуживания в системе?")
        assertContains(formatResult, "ВЫБРАТЬ КОЛИЧЕСТВО(Ссылка)")

        assertContains(formatResult, "Исходный вопрос: Сколько объектов обслуживания в системе?")
        assertContains(formatResult, "ВЫБРАТЬ КОЛИЧЕСТВО(ОбъектыОбслуживания.Код)")
        assertContains(formatResult, "Описание: Подсчет количества элементов по условию")
        assertContains(formatResult, "Нормализованное намерение: COUNT элементы FILTER параметр")
        assertContains(formatResult, "Комментарий: Тестовый шаблон")
        assertContains(formatResult, "Сходство: 1.0")
        assertContains(formatResult, "```1c")
        assertContains(
            formatResult,
            "Не отвечай пользователю данными или итогами до успешного `executeQuery`.",
        )
    }

    @Test
    fun `должен проверить ответ Found только с шаблонами`() {

        val result = RelevantQuerySearchResult.Found(
            normalizedQuestion = "количество объект_обслуживания",
            templates = listOf(
                RelevantQueryTemplate(
                    templateId = "template-1",
                    name = "Подсчет количества элементов с отбором",
                    fullName = "Подсчет количества элементов по условию",
                    normalizedIntent = "COUNT элементы FILTER параметр",
                    queryTemplate = "ВЫБРАТЬ КОЛИЧЕСТВО(Ссылка)",
                    comment = "Тестовый шаблон",
                    matchedQuestion = "Сколько объектов обслуживания в системе?",
                    similarityScore = 1.0
                )
            ),
            successfulQueries = emptyList()
        )

        val formatResult = RelevantQuerySearchToolResponseFormatter.format(result)
        assertContains(formatResult, "## ШАБЛОНЫ")
        assertFalse(formatResult.contains("## УСПЕШНЫЕ ЗАПРОСЫ"))
        assertContains(formatResult, "Найдено шаблонов: 1")
        assertContains(formatResult, "Найдено успешных запросов: 0")
    }

    @Test
    fun `должен проверить ответ Found только с запросами`() {
        val result = RelevantQuerySearchResult.Found(
            normalizedQuestion = "количество объект_обслуживания",
            templates = emptyList(),
            successfulQueries = listOf(
                SuccessfulQuery(
                    sourceQuestion = "Сколько объектов обслуживания в системе?",
                    queryText = "ВЫБРАТЬ КОЛИЧЕСТВО(ОбъектыОбслуживания.Код)",
                    templateId = "template-1",
                    similarityScore = 1.0
                )
            )
        )

        val formatResult = RelevantQuerySearchToolResponseFormatter.format(result)
        assertFalse(formatResult.contains("## ШАБЛОНЫ"))
        assertContains(formatResult, "## УСПЕШНЫЕ ЗАПРОСЫ")
        assertContains(formatResult, "Найдено успешных запросов: 1")
        assertContains(formatResult, "Найдено шаблонов: 0")
    }

    @Test
    fun `должен проверить ответ ValidationError`() {

        val result = RelevantQuerySearchResult.ValidationError(
            code = "INVALID_REQUEST",
            message = "Не заполнен или некорректно задан вопрос пользователя."
        )

        val formatResult = RelevantQuerySearchToolResponseFormatter.format(result)
        assertContains(formatResult, "Статус: ошибка валидации")
        assertContains(formatResult, "Код ошибки: INVALID_REQUEST")
        assertContains(formatResult, "Сообщение: Не заполнен или некорректно задан вопрос пользователя.")
        assertContains(
            formatResult,
            "Не трактуй ошибку валидации как отсутствие найденных запросов.",
        )
        assertContains(
            formatResult,
            "Не выдавай пользователю данные без успешного executeQuery.",
        )

    }

    @Test
    fun `должен проверить ответ IntegrationError`() {

        val result = RelevantQuerySearchResult.IntegrationError(
            code = "INTERNAL_SERVER_ERROR",
            message = "ошибка сервиса"
        )

        val formatResult = RelevantQuerySearchToolResponseFormatter.format(result)
        assertContains(formatResult, "Статус: ошибка интеграции")
        assertContains(formatResult, "Код ошибки: INTERNAL_SERVER_ERROR")
        assertContains(formatResult, "Сообщение: ошибка сервиса")
        assertContains(
            formatResult,
            "Не трактуй ошибку интеграции как отсутствие найденных запросов.",
        )
        assertContains(formatResult, "Не придумывай шаблоны, запросы или результаты.")

    }

    @Test
    fun `должен проверить ответ UnexpectedError`() {

        val result = RelevantQuerySearchResult.UnexpectedError(
            message = "превышен лимит ожидания ответа"
        )

        val formatResult = RelevantQuerySearchToolResponseFormatter.format(result)
        assertContains(formatResult, "Статус: непредвиденная ошибка ответа")
        assertContains(formatResult, "Сообщение: превышен лимит ожидания ответа")
        assertContains(
            formatResult,
            "Не трактуй этот ответ как отсутствие найденных запросов.",
        )
        assertContains(
            formatResult,
            "Не используй неполные или противоречивые данные как шаблон запроса.",
        )
    }
}