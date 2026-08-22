package data.tools

import application.relevantqueries.RelevantQuerySearchResult

object RelevantQuerySearchToolResponseFormatter {

    fun format(result: RelevantQuerySearchResult): String {
        return when (result) {
            RelevantQuerySearchResult.NotFound -> formatNotFound()
            is RelevantQuerySearchResult.Found -> formatFound(result)
            is RelevantQuerySearchResult.ValidationError -> formatValidationError(result)
            is RelevantQuerySearchResult.IntegrationError -> formatIntegrationError(result)
            is RelevantQuerySearchResult.UnexpectedError -> formatUnexpectedError(result)
        }
    }

    private fun formatNotFound(): String {
        return buildString {
            appendLine("# ПОИСК РЕЛЕВАНТНЫХ УСПЕШНЫХ ЗАПРОСОВ")
            appendLine("Статус: не найдено")
            appendLine("Финальный ответ пользователю: запрещён")
            appendLine("Нужно выполнить новый executeQuery: да")
            appendLine("Сохранённые релевантные запросы не найдены.")
            appendLine("Продолжи обычный путь: получи метаданные, сформируй и выполни новый executeQuery.")
            appendLine("Не отвечай пользователю без успешного executeQuery.")
        }
    }

    private fun formatFound(result: RelevantQuerySearchResult.Found): String {

        val templates = result.templates
        val successfulQueries = result.successfulQueries
        return buildString {
            appendLine("# ПОИСК РЕЛЕВАНТНЫХ УСПЕШНЫХ ЗАПРОСОВ")
            appendLine("Статус: найдено")
            appendLine("Финальный ответ пользователю: запрещён")
            appendLine("Нужно выполнить новый executeQuery: да")
            appendLine("Нормализованный вопрос: ${result.normalizedQuestion}")
            appendLine("Найдено шаблонов: ${result.templates.size}")
            appendLine("Найдено успешных запросов: ${result.successfulQueries.size}")

            if (templates.isNotEmpty()) {
                appendLine("## ШАБЛОНЫ")
                templates.forEachIndexed { index, template ->
                    appendLine("### Шаблон ${index + 1}")
                    appendLine("ID шаблона: ${template.templateId}")
                    appendLine("Сходство: ${template.similarityScore}")
                    appendLine("Наименование: ${template.name}")
                    appendLine("Описание: ${template.fullName}")
                    appendLine("Подходящая формулировка: ${template.matchedQuestion}")
                    appendLine("Нормализованное намерение: ${template.normalizedIntent}")
                    appendLine("Шаблон запроса 1С:")
                    appendLine("```1c")
                    appendLine(template.queryTemplate)
                    appendLine("```")
                    if (template.comment.isNotBlank()) {
                        appendLine("Комментарий: ${template.comment}")
                    }
                }
            }
            if (successfulQueries.isNotEmpty()) {
                appendLine("## УСПЕШНЫЕ ЗАПРОСЫ")
                successfulQueries.forEachIndexed { index, query ->
                    appendLine("### Успешный запрос ${index + 1}")
                    appendLine("Исходный вопрос: ${query.sourceQuestion}")
                    appendLine("ID шаблона: ${query.templateId}")
                    appendLine("Сходство: ${query.similarityScore}")
                    appendLine("Ранее успешный запрос:")
                    appendLine("```1c")
                    appendLine(query.queryText)
                    appendLine("```")

                }
            }

            appendLine("## ОБЯЗАТЕЛЬНЫЕ ДЕЙСТВИЯ")
            appendLine("1. Шаблоны и успешные запросы — ориентиры, а не актуальный ответ.")
            appendLine("2. Проверь метаданные и адаптируй запрос к текущему вопросу пользователя.")
            appendLine("3. Выполни новый `executeQuery`.")
            appendLine("4. Не отвечай пользователю данными или итогами до успешного `executeQuery`.")
        }
    }

    private fun formatValidationError(result: RelevantQuerySearchResult.ValidationError): String {
        return buildString {
            appendLine("# ПОИСК РЕЛЕВАНТНЫХ УСПЕШНЫХ ЗАПРОСОВ")
            appendLine("Статус: ошибка валидации")
            appendLine("Финальный ответ пользователю: запрещён")
            appendLine("Код ошибки: ${result.code}")
            appendLine("Сообщение: ${result.message}")
            appendLine("## ОБЯЗАТЕЛЬНЫЕ ДЕЙСТВИЯ")
            appendLine("1. Не трактуй ошибку валидации как отсутствие найденных запросов.")
            appendLine("2. Не повторяй тот же вызов с теми же некорректными параметрами.")
            appendLine("3. Если вопрос пользователя пустой или неоднозначный — запроси уточнение.")
            appendLine("4. Не выдавай пользователю данные без успешного executeQuery.")
        }
    }

    private fun formatIntegrationError(result: RelevantQuerySearchResult.IntegrationError): String {
        return buildString {
            appendLine("# ПОИСК РЕЛЕВАНТНЫХ УСПЕШНЫХ ЗАПРОСОВ")
            appendLine("Статус: ошибка интеграции")
            appendLine("Финальный ответ пользователю: запрещён")
            appendLine("Код ошибки: ${result.code}")
            appendLine("Сообщение: ${result.message}")
            appendLine("## ОБЯЗАТЕЛЬНЫЕ ДЕЙСТВИЯ")
            appendLine("1. Не трактуй ошибку интеграции как отсутствие найденных запросов.")
            appendLine("2. Не придумывай шаблоны, запросы или результаты.")
            appendLine(
                "3. Если остальные инструменты доступны, продолжи обычный путь: " +
                        "метаданные → формирование запроса → executeQuery."
            )
            appendLine("4. Не выдавай пользователю данные без успешного executeQuery.")
        }
    }

    private fun formatUnexpectedError(result: RelevantQuerySearchResult.UnexpectedError): String {
        return buildString {
            appendLine("# ПОИСК РЕЛЕВАНТНЫХ УСПЕШНЫХ ЗАПРОСОВ")
            appendLine("Статус: непредвиденная ошибка ответа")
            appendLine("Финальный ответ пользователю: запрещён")
            appendLine("Сообщение: ${result.message}")
            appendLine("## ОБЯЗАТЕЛЬНЫЕ ДЕЙСТВИЯ")
            appendLine("1. Не трактуй этот ответ как отсутствие найденных запросов.")
            appendLine("2. Не используй неполные или противоречивые данные как шаблон запроса.")
            appendLine("3. Продолжи обычный путь через метаданные и новый executeQuery.")
            appendLine("4. Не выдавай пользователю данные без успешного executeQuery.")
        }

    }
}
