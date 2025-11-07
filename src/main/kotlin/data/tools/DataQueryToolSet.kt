package org.example.data.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@LLMDescription("Инструменты для генерации запросов на языке 1С и получения произвольных данных из учетной системы")
class DataQueryToolSet(
    private val baseUrl: String = "http://77.95.56.147:65525/DevelopDaily/hs/agent_smart_api_v1"
): ToolSet {

    @Tool
    @LLMDescription("""
    Инструмент получает полный каталог метаданных учетной системы.
    Возвращает структурированный список всех объектов системы: справочники, документы, регистры и т.д.
    
    СТРУКТУРА ОТВЕТА:
    - type: тип объекта (Справочники, Документы, РегистрыСведений, etc)
    - id: уникальный идентификатор для обращения к объекту
    - name: системное имя объекта
    - title: человеко-читаемое название на русском
    
    ИСПОЛЬЗОВАНИЕ:
    - Используй первым чтобы понять какие объекты есть в системе
    - Для детальной информации об объекте используй getClassMetadata
    - Для поиска конкретного объекта фильтруй по полям name или title
""")
    suspend fun getAllMetadata():String{

        val url =  "$baseUrl/get-all-metadata"
        return try {
            val response = executeGetTool(url, "getAllMetadata")

            // Парсим и переформатируем для лучшей читаемости LLM
            val formattedResponse = formatMetadataResponse(response)

            // Возвращаем чистые данные без лишних оберток
            formattedResponse
        } catch (e: Exception) {
            // Структурированная ошибка для LLM
            """{
            "error_type": "metadata_retrieval_failed",
            "message": "Не удалось получить метаданные системы",
            "details": "${e.message}",
            "suggestion": "Проверьте доступность сервера и повторите запрос"
        }"""
        }
    }

    @Tool
    @LLMDescription("""
    Инструмент получает детальное описание конкретного объекта метаданных системы.
    
    Параметры:
    - metaDataType: тип объекта (Справочники, Документы, РегистрыСведений, РегистрыНакопления, ПланыСчетов, etc)
    - metaDataClass: системное имя класса (например: "ОбъектыОбслуживания", "ЗаявкиНаРемонт")
    
    ВОЗВРАЩАЕМАЯ ИНФОРМАЦИЯ:
    - Структура объекта: все поля, их типы, описания
    - Ограничения и бизнес-правила
    - Связи с другими объектами системы
    
    ПРИМЕРЫ ИСПОЛЬЗОВАНИЯ:
    - getClassMetadata("Справочники", "ОбъектыОбслуживания") - полная структура справочника
    - getClassMetadata("Документы", "ЗаявкаНаРемонт") - структура документа с его реквизитами
    - getClassMetadata("РегистрыСведений", "Цены") - структура регистра с измерениями и ресурсами
    
    ПРЕДУПРЕЖДЕНИЕ:
    - Тип и класс должны точно соответствовать значениям из getAllMetadata
    - Используй searchMetadata если не уверен в точном названии
""")
    suspend fun getClassMetadata(metaDataType: String, metaDataClass: String):String{

        val url =  "$baseUrl/get-class-metadata"

        return try {
            // Формируем JSON тело запроса
            val requestBody = """
        {
            "request": {
                "type": "$metaDataType",
                "class": "$metaDataClass"
            }
        }
        """.trimIndent()

            val response = executePostTool(url, requestBody, "getClassMetadata")

            // Форматируем ответ для лучшей читаемости LLM
            formatClassMetadataResponse(response, metaDataType, metaDataClass)

        } catch (e: Exception) {
            """{
            "error_type": "class_metadata_retrieval_failed",
            "message": "Не удалось получить описание объекта метаданных",
            "requested_type": "$metaDataType",
            "requested_class": "$metaDataClass", 
            "details": "${e.message}",
            "suggestions": [
                "Проверьте правильность типа и класса через getAllMetadata",
                "Используйте searchMetadata для поиска похожих объектов",
                "Убедитесь, что тип и класс написаны без опечаток"
            ]
        }"""
        }
    }

    @Tool
    @LLMDescription("""
    Инструмент получает полную справку по языку запросов 1С - русскоязычному аналогу SQL.
    
    ВОЗВРАЩАЕМАЯ ИНФОРМАЦИЯ:
    - Ключевые слова: ВЫБРАТЬ, ИЗ, ГДЕ, УПОРЯДОЧИТЬ ПО, СГРУППИРОВАТЬ ПО и др.
    - Функции: строковые, математические, работы с датами, агрегатные
    - Операторы: арифметические, логические, сравнения
    - Соединения: ЛЕВОЕ, ПРАВОЕ, ПОЛНОЕ, ВНУТРЕННЕЕ
    - Примеры реальных запросов
    
    КОГДА ИСПОЛЬЗОВАТЬ:
    - При генерации нового запроса к системе 1С
    - При ошибках в синтаксисе запроса
    - Для изучения возможностей языка запросов
    - Для поиска конкретных функций или операторов
    
    СОВЕТЫ:
    - Язык запросов 1С использует русские ключевые слова
    - Структура похожа на SQL но с особенностями
    - Всегда проверяй существование таблиц и полей через getClassMetadata
""")
    suspend fun getQueryLanguageDescription():String{

        val url = "$baseUrl/get-query-language-description"
        return try {
            val response = executeGetTool(url, "getQueryLanguageDescription")

            // Форматируем ответ для лучшей читаемости LLM
            formatQueryLanguageResponse(response)
        } catch (e: Exception) {
            """{
            "error_type": "language_description_retrieval_failed",
            "message": "Не удалось получить справку по языку запросов 1С",
            "details": "${e.message}",
            "suggestion": "Проверьте доступность сервера и повторите запрос"
        }"""
        }
    }

    @Tool
    @LLMDescription("""
    Инструмент выполняет сгенерированный запрос на языке 1С и возвращает результат.
    
    КРИТИЧЕСКИ ВАЖНО:
    - Запрос должен быть синтаксически корректным
    - Все таблицы и поля должны существовать в системе
    - Используются только SELECT запросы
    - Рекомендуется сначала проверить структуру данных через getClassMetadata
    
    ПАРАМЕТРЫ:
    - query: строка с запросом на языке 1С (только SELECT)
    
    ВОЗВРАЩАЕМЫЙ РЕЗУЛЬТАТ:
    - Успешное выполнение: массив объектов с данными
    - Ошибка выполнения: структурированное описание ошибки
    - Предупреждения: информация о потенциальных проблемах
    
    ПРИМЕРЫ ЗАПРОСОВ:
    - ВЫБРАТЬ ПЕРВЫЕ 10 * ИЗ Справочник.ОбъектыОбслуживания
    - ВЫБРАТЬ Наименование, Статус ИЗ Справочник.ОбъектыОбслуживания ГДЕ Статус = 'Активный'
    - ВЫБРАТЬ Подразделение, КОЛИЧЕСТВО(*) КАК Количество ИЗ Справочник.ОбъектыОбслуживания СГРУППИРОВАТЬ ПО Подразделение
    
    СОВЕТЫ:
    - Всегда тестируй запросы с ПЕРВЫЕ N перед выполнением полной выборки
    - Используй псевдонимы (КАК) для улучшения читаемости результатов
    - Проверяй существование таблиц через getClassMetadata перед выполнением
""")
    suspend fun executeQuery(query: String):String{

        val url =  "$baseUrl/execute-query"

        return try {
            // Валидация базового синтаксиса
            val validationResult = validateQuerySyntax(query)
            if (!validationResult.isValid) {
                return createErrorResponse(
                    errorType = "query_validation_failed",
                    message = "Запрос не прошел базовую валидацию",
                    query = query,
                    details = validationResult.errors,
                    suggestions = listOf(
                        "Проверьте синтаксис запроса через getQueryLanguageDescription",
                        "Убедитесь, что используются только разрешенные ключевые слова",
                        "Используйте ПЕРВЫЕ N для ограничения больших выборок"
                    )
                )
            }

            // Формируем JSON тело запроса
            val requestBody = """
        {
            "request": {
                "text_query": "${escapeJsonString(query)}"
            }
        }
        """.trimIndent()

            val rawResponse = executePostTool(url, requestBody, "executeQuery")

            // Обрабатываем и форматируем ответ
            formatQueryResponse(rawResponse, query)

        } catch (e: Exception) {
            createErrorResponse(
                errorType = "query_execution_failed",
                message = "Ошибка при выполнении запроса",
                query = query,
                details = listOf(e.message ?: "Неизвестная ошибка"),
                suggestions = listOf(
                    "Проверьте корректность SQL синтаксиса",
                    "Убедитесь, что все таблицы и поля существуют",
                    "Попробуйте упростить запрос",
                    "Используйте getClassMetadata для проверки структуры данных"
                )
            )
        }
    }

    // Новый вспомогательный метод для формата нового API
    private suspend fun executeGetTool(url: String, toolName: String): String {

        return try {

            HttpClient(CIO).use { client ->
                val response: HttpResponse = client.get(url) {
                    header(HttpHeaders.Accept, "application/json")
                    timeout { requestTimeoutMillis = 15000 }
                }

                val responseBody = response.bodyAsText()
                println("🔧 [TOOL] $toolName - Status: ${response.status}")

                responseBody
            }
        } catch (e: Exception) {
                throw Exception("HTTP ошибка при вызове $toolName: ${e.message}")
        }

    }

    // Специализированный метод для POST-запросов
    private suspend fun executePostTool(url: String, requestBody: String, toolName: String): String {
        return try {
            HttpClient(CIO).use { client ->
            val response: HttpResponse = client.post(url) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Accept, "application/json")
                setBody(requestBody)
                timeout {
                    requestTimeoutMillis = 20000
                    connectTimeoutMillis = 10000
                }
            }

            val responseBody = response.bodyAsText()
            println("🔧 [TOOL] $toolName - Status: ${response.status}")
            println("📤 [REQUEST] $requestBody")

            responseBody
            }
        } catch (e: Exception) {
            throw Exception("HTTP ошибка при вызове $toolName: ${e.message}")
        }
    }

    // Улучшенный форматировщик для нового формата метаданных
    private fun formatMetadataResponse(rawResponse: String): String {
        return try {
            val json = Json.parseToJsonElement(rawResponse)

            // Создаем структурированный ответ с группировкой по типам
            val result = buildString {
                appendLine("{")
                appendLine("  \"metadata_summary\": \"Полный каталог объектов системы\",")
                appendLine("  \"total_categories\": ${json.jsonObject.size},")
                appendLine("  \"categories\": [")

                var firstCategory = true
                json.jsonObject.forEach { (categoryName, itemsArray) ->
                    if (!firstCategory) appendLine("    ,")
                    appendLine("    {")
                    appendLine("      \"category\": \"$categoryName\",")
                    appendLine("      \"count\": ${itemsArray.jsonArray.size},")
                    appendLine("      \"items\": [")

                    var firstItem = true
                    itemsArray.jsonArray.forEach { item ->
                        if (!firstItem) appendLine("        ,")
                        val itemObj = item.jsonObject
                        appendLine("        {")
                        appendLine("          \"type\": \"${itemObj["type"]?.jsonPrimitive?.content ?: ""}\",")
                        appendLine("          \"id\": \"${itemObj["id"]?.jsonPrimitive?.content ?: ""}\",")
                        appendLine("          \"name\": \"${itemObj["name"]?.jsonPrimitive?.content ?: ""}\",")
                        appendLine("          \"title\": \"${itemObj["title"]?.jsonPrimitive?.content ?: ""}\"")
                        append("        }")
                        firstItem = false
                    }
                    appendLine()
                    appendLine("      ]")
                    append("    }")
                    firstCategory = false
                }
                appendLine()
                appendLine("  ]")
                append("}")
            }

            result
        } catch (e: Exception) {
            // Если не удалось отформатировать, возвращаем как есть
            rawResponse
        }
    }

    // Форматирование ответа для детальной метаинформации
    private fun formatClassMetadataResponse(rawResponse: String, type: String, className: String): String {
        return try {
            val json = Json.parseToJsonElement(rawResponse)

            // Создаем структурированный ответ с анализом объекта
            buildString {
                appendLine("{")
                appendLine("  \"metadata_object\": {")
                appendLine("    \"type\": \"$type\",")
                appendLine("    \"class\": \"$className\",")

                // Извлекаем основные свойства если они есть
                val name = json.jsonObject["name"]?.jsonPrimitive?.content ?: className
                val title = json.jsonObject["title"]?.jsonPrimitive?.content ?: "Не указано"
                val description = json.jsonObject["description"]?.jsonPrimitive?.content ?: "Описание отсутствует"

                appendLine("    \"name\": \"$name\",")
                appendLine("    \"title\": \"$title\",")
                appendLine("    \"description\": \"$description\",")

                // Анализируем структуру объекта
                appendLine("    \"structure_analysis\": {")

                // Поля/реквизиты
                val fields = json.jsonObject["fields"]?.jsonArray
                if (fields != null) {
                    appendLine("      \"fields_count\": ${fields.size},")
                    appendLine("      \"fields_preview\": [")
                    fields.take(5).forEachIndexed { index, field ->
                        if (index > 0) appendLine("        ,")
                        val fieldName = field.jsonObject["name"]?.jsonPrimitive?.content ?: "unknown"
                        val fieldType = field.jsonObject["type"]?.jsonPrimitive?.content ?: "unknown"
                        appendLine("        {\"name\": \"$fieldName\", \"type\": \"$fieldType\"}")
                    }
                    if (fields.size > 5) appendLine("        ,{\"note\": \"... и еще ${fields.size - 5} полей\"}")
                    appendLine("      ]")
                } else {
                    appendLine("      \"fields_count\": 0,")
                    appendLine("      \"note\": \"Поля не определены или скрыты\"")
                }

                appendLine("    },")

                // Табличные части (для документов)
                val tableSections = json.jsonObject["tableSections"]?.jsonArray
                if (tableSections != null && tableSections.isNotEmpty()) {
                    appendLine("    \"table_sections\": [")
                    tableSections.forEachIndexed { index, section ->
                        if (index > 0) appendLine("      ,")
                        val sectionName = section.jsonObject["name"]?.jsonPrimitive?.content ?: "unknown"
                        appendLine("      \"$sectionName\"")
                    }
                    appendLine("    ],")
                }

                // Методы и операции
                val methods = json.jsonObject["methods"]?.jsonArray
                if (methods != null && methods.isNotEmpty()) {
                    appendLine("    \"available_methods_count\": ${methods.size},")
                }

                // Полные исходные данные
                appendLine("    \"raw_metadata\": $rawResponse")

                appendLine("  }")
                append("}")
            }
        } catch (e: Exception) {
            // Если не удалось отформатировать, возвращаем как есть с базовой информацией
            """{
            "metadata_object": {
                "type": "$type",
                "class": "$className", 
                "raw_response": $rawResponse,
                "format_note": "Ответ не был отформатирован из-за ошибки: ${e.message}"
            }
        }"""
        }
    }

    private fun formatQueryLanguageResponse(rawResponse: String): String {
        return try {
            val json = Json.parseToJsonElement(rawResponse)
            val responseObj = json.jsonObject["response"]?.jsonObject ?: return rawResponse

            // Извлекаем и очищаем тексты из каждой области
            val keywords = cleanQueryText(responseObj["keywords"]?.jsonPrimitive?.content ?: "")
            val functions = cleanQueryText(responseObj["functions"]?.jsonPrimitive?.content ?: "")
            val operators = cleanQueryText(responseObj["operators"]?.jsonPrimitive?.content ?: "")
            val join = cleanQueryText(responseObj["join"]?.jsonPrimitive?.content ?: "")

            // Создаем структурированный ответ
            buildString {
                appendLine("{")
                appendLine("  \"query_language_reference\": \"Язык запросов 1С - полная справка\",")
                appendLine("  \"sections\": {")

                // Раздел ключевых слов
                appendLine("    \"keywords\": {")
                appendLine("      \"description\": \"Основные ключевые слова языка запросов\",")
                appendLine("      \"quick_reference\": [")
                appendLine("        \"ВЫБРАТЬ - начало запроса, выбор полей\",")
                appendLine("        \"ИЗ - указание источника данных\",")
                appendLine("        \"ГДЕ - условия отбора\",")
                appendLine("        \"УПОРЯДОЧИТЬ ПО - сортировка результатов\",")
                appendLine("        \"СГРУППИРОВАТЬ ПО - группировка и агрегация\",")
                appendLine("        \"ПЕРВЫЕ N - ограничение количества записей\",")
                appendLine("        \"РАЗРЕШЕННЫЕ - с учетом прав доступа RLS\",")
                appendLine("        \"РАЗЛИЧНЫЕ - удаление дубликатов\"")
                appendLine("      ],")
                appendLine("      \"detailed_explanation\": \"$keywords\",")
                appendLine("      \"common_use_cases\": [")
                appendLine("        \"Простые SELECT запросы\",")
                appendLine("        \"Группировка и агрегация данных\",")
                appendLine("        \"Сортировка и ограничение выборки\",")
                appendLine("        \"Работа с временными таблицами\"")
                appendLine("      ]")
                appendLine("    },")

                // Раздел функций
                appendLine("    \"functions\": {")
                appendLine("      \"description\": \"Встроенные функции языка запросов\",")
                appendLine("      \"categories\": {")
                appendLine("        \"string_functions\": \"ДлинаСтроки, Врег, Нрег, СтрНайти, СтрЗаменить, ПОДСТРОКА\",")
                appendLine("        \"math_functions\": \"ACos, ASin, ATan, Cos, Sin, Exp, Log, Pow, Sqrt, Окр, Цел\",")
                appendLine("        \"date_functions\": \"ГОД, МЕСЯЦ, ДЕНЬ, НАЧАЛОПЕРИОДА, КОНЕЦПЕРИОДА, РАЗНОСТЬДАТ\",")
                appendLine("        \"aggregate_functions\": \"СУММА, КОЛИЧЕСТВО, СРЕДНЕЕ, МАКСИМУМ, МИНИМУМ\",")
                appendLine("        \"type_functions\": \"ТИП, ТИПЗНАЧЕНИЯ, ПРЕДСТАВЛЕНИЕ, ЕСТЬNULL\"")
                appendLine("      },")
                appendLine("      \"detailed_explanation\": \"$functions\"")
                appendLine("    },")

                // Раздел операторов
                appendLine("    \"operators\": {")
                appendLine("      \"description\": \"Операторы для условий и выражений\",")
                appendLine("      \"types\": {")
                appendLine("        \"arithmetic\": \"+, -, *, / (для чисел), + (для строк)\",")
                appendLine("        \"comparison\": \">, <, =, >=, <=, <>\",")
                appendLine("        \"logical\": \"И, ИЛИ, НЕ\",")
                appendLine("        \"special\": \"ПОДОБНО (LIKE), МЕЖДУ (BETWEEN), В (IN), ЕСТЬ NULL (IS NULL)\",")
                appendLine("        \"type_operators\": \"ВЫБОР (CASE), ВЫРАЗИТЬ (CAST), ССЫЛКА (TYPE CHECK)\"")
                appendLine("      },")
                appendLine("      \"detailed_explanation\": \"$operators\"")
                appendLine("    },")

                // Раздел соединений
                appendLine("    \"joins\": {")
                appendLine("      \"description\": \"Типы соединений таблиц\",")
                appendLine("      \"join_types\": [")
                appendLine("        \"ЛЕВОЕ СОЕДИНЕНИЕ - LEFT JOIN\",")
                appendLine("        \"ПРАВОЕ СОЕДИНЕНИЕ - RIGHT JOIN\",")
                appendLine("        \"ПОЛНОЕ СОЕДИНЕНИЕ - FULL OUTER JOIN\",")
                appendLine("        \"ВНУТРЕННЕЕ СОЕДИНЕНИЕ - INNER JOIN\"")
                appendLine("      ],")
                appendLine("      \"syntax_example\": \"ЛЕВОЕ СОЕДИНЕНИЕ Таблица2 ПО Таблица1.Поле = Таблица2.Поле\",")
                appendLine("      \"special_notes\": [")
                appendLine("        \"Конструктор запросов не поддерживает ПРАВОЕ СОЕДИНЕНИЕ\",")
                appendLine("        \"CROSS JOIN реализуется через ПО ИСТИНА\",")
                appendLine("        \"Тип соединения влияет на результат при пустых таблицах\"")
                appendLine("      ],")
                appendLine("      \"detailed_explanation\": \"$join\"")
                appendLine("    }")
                appendLine("  },")

                // Практические примеры
                appendLine("  \"practical_examples\": {")
                appendLine("    \"simple_select\": \"ВЫБРАТЬ * ИЗ Справочник.ОбъектыОбслуживания\",")
                appendLine("    \"select_with_conditions\": \"ВЫБРАТЬ Наименование, Статус ИЗ Справочник.ОбъектыОбслуживания ГДЕ Статус = 'Активный' УПОРЯДОЧИТЬ ПО Наименование\",")
                appendLine("    \"aggregation\": \"ВЫБРАТЬ Подразделение, КОЛИЧЕСТВО(*) КАК КоличествоОбъектов ИЗ Справочник.ОбъектыОбслуживания СГРУППИРОВАТЬ ПО Подразделение\",")
                appendLine("    \"join_example\": \"ВЫБРАТЬ Объекты.Наименование, Подразделения.Наименование КАК Подразделение ИЗ Справочник.ОбъектыОбслуживания КАК Объекты ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Подразделения КАК Подразделения ПО Объекты.Подразделение = Подразделения.Ссылка\"")
                appendLine("  },")
                appendLine("  \"quick_tips\": [")
                appendLine("    \"Всегда используй псевдонимы (КАК) для полей и таблиц\",")
                appendLine("    \"Проверяй существование таблиц через getClassMetadata перед генерацией запроса\",")
                appendLine("    \"Для отладки сначала тестируй простые SELECT * запросы\",")
                appendLine("    \"Используй ПЕРВЫЕ N для ограничения больших выборок при тестировании\",")
                appendLine("    \"Учитывай права доступа - используй РАЗРЕШЕННЫЕ при работе с защищенными данными\"")
                appendLine("  ]")
                append("}")
            }
        } catch (e: Exception) {
            // Возвращаем исходный ответ если не удалось отформатировать
            rawResponse
        }
    }

    // Очистка текста от лишних форматирований
    private fun cleanQueryText(text: String): String {
        return text
            .replace("#Область[^\\n]+\\n".toRegex(), "")
            .replace("#КонецОбласти".toRegex(), "")
            .replace("\t", "  ")
            .replace("\"", "\\\"")
            .replace(Regex("\\n\\s*\\n"), "\n")
            .trim()
    }

    private fun validateQuerySyntax(query: String): QueryValidationResult {
        val errors = mutableListOf<String>()
        val upperQuery = query.uppercase()

        // Проверка на запрещенные операции
        val forbiddenKeywords = listOf(
            "INSERT", "UPDATE", "DELETE", "DROP", "CREATE",
            "ALTER", "EXEC", "EXECUTE", "GRANT", "REVOKE",
            "TRUNCATE", "MERGE", "BACKUP", "RESTORE"
        )

        forbiddenKeywords.forEach { keyword ->
            if (upperQuery.contains(keyword)) {
                errors.add("Обнаружена запрещенная операция: $keyword")
            }
        }

        // Проверка обязательных ключевых слов
        if (!upperQuery.contains("ВЫБРАТЬ") || !upperQuery.contains("ИЗ")) {
            errors.add("Запрос должен содержать ключевые слова ВЫБРАТЬ и ИЗ")
        }

        // Проверка длины запроса
        if (query.length > 5000) {
            errors.add("Запрос слишком длинный (максимум 5000 символов)")
        }

        // Проверка на потенциально опасные конструкции
        if (upperQuery.contains(";") && !upperQuery.contains("ВЫБРАТЬ")) {
            errors.add("Обнаружены потенциально опасные конструкции")
        }

        return QueryValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    private fun createErrorResponse(
        errorType: String,
        message: String,
        query: String,
        details: List<String>,
        suggestions: List<String>
    ): String {
        return """
    {
        "query_execution": {
            "status": "error",
            "error_type": "$errorType",
            "message": "$message",
            "original_query": "${escapeJsonString(query)}",
            "details": ${Json.encodeToString(details)},
            "suggestions": ${Json.encodeToString(suggestions)},
            "next_steps": [
                "Исправьте синтаксис запроса",
                "Проверьте существование таблиц через getClassMetadata", 
                "Изучите документацию через getQueryLanguageDescription",
                "Попробуйте выполнить упрощенную версию запроса"
            ]
        }
    }
    """.trimIndent()
    }

    private fun escapeJsonString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun formatQueryResponse(rawResponse: String, originalQuery: String): String {
        return try {
            val json = Json.parseToJsonElement(rawResponse)
            val responseArray = json.jsonObject["response"]?.jsonArray

            if (responseArray == null) {
                // Возможно, это ошибка от сервера
                return analyzeErrorResponse(rawResponse, originalQuery)
            }

            // Анализируем результат
            val resultCount = responseArray.size
            val sampleData = if (resultCount > 0) {
                responseArray.take(3).map { it.jsonObject }
            } else {
                emptyList()
            }

            // Определяем поля из первого элемента (если есть)
            val fields = if (sampleData.isNotEmpty()) {
                sampleData.first().keys.toList()
            } else {
                emptyList()
            }

            buildString {
                appendLine("{")
                appendLine("  \"query_execution\": {")
                appendLine("    \"status\": \"success\",")
                appendLine("    \"original_query\": \"${escapeJsonString(originalQuery)}\",")
                appendLine("    \"results_summary\": {")
                appendLine("      \"total_records\": $resultCount,")
                appendLine("      \"fields_count\": ${fields.size},")
                appendLine("      \"fields\": ${Json.encodeToString(fields)}")
                appendLine("    },")

                if (resultCount > 0) {
                    appendLine("    \"sample_data\": ${Json.encodeToString(sampleData)},")
                    appendLine("    \"data_analysis\": {")

                    // Простой анализ данных
                    if (resultCount > 100) {
                        appendLine("      \"note\": \"Большой объем данных, рассмотрите использование фильтров или агрегации\",")
                    }

                    if (fields.any { it.contains("Дата") || it.contains("дата") }) {
                        appendLine("      \"has_dates\": true,")
                    }

                    if (fields.any { it.contains("Количеств") || it.contains("Сумм") }) {
                        appendLine("      \"has_numeric_data\": true,")
                    }

                    appendLine("      \"recommendations\": [")
                    appendLine("        \"Для больших наборов используйте СГРУППИРОВАТЬ ПО и агрегатные функции\",")
                    appendLine("        \"Используйте ГДЕ для фильтрации ненужных данных\",")
                    appendLine("        \"Рассмотрите использование ПЕРВЫЕ N для ограничения выборки\"")
                    appendLine("      ]")
                    appendLine("    },")
                }

                appendLine("    \"full_response\": $rawResponse")
                appendLine("  }")
                append("}")
            }
        } catch (e: Exception) {
            // Если не удалось отформатировать, возвращаем исходный ответ
            """{
            "query_execution": {
                "status": "success_raw",
                "original_query": "${escapeJsonString(originalQuery)}", 
                "raw_response": $rawResponse,
                "format_note": "Ответ не был отформатирован из-за ошибки: ${e.message}"
            }
        }"""
        }
    }

    private fun analyzeErrorResponse(errorResponse: String, originalQuery: String): String {
        return try {
            val json = Json.parseToJsonElement(errorResponse)
            val errorMessage = json.jsonObject["error"]?.jsonPrimitive?.content
                ?: json.jsonObject["message"]?.jsonPrimitive?.content
                ?: "Неизвестная ошибка сервера"

            createErrorResponse(
                errorType = "server_error",
                message = "Сервер вернул ошибку",
                query = originalQuery,
                details = listOf(errorMessage),
                suggestions = listOf(
                    "Проверьте синтаксис запроса",
                    "Убедитесь, что все таблицы и поля существуют",
                    "Используйте getClassMetadata для проверки структуры данных",
                    "Упростите запрос и попробуйте выполнить его по частям"
                )
            )
        } catch (e: Exception) {
            createErrorResponse(
                errorType = "unknown_error",
                message = "Неизвестная ошибка при обработке ответа",
                query = originalQuery,
                details = listOf("Raw response: $errorResponse"),
                suggestions = listOf(
                    "Проверьте логи сервера",
                    "Упростите запрос",
                    "Обратитесь к администратору системы"
                )
            )
        }
    }

}
