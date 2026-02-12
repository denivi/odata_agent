package org.example.data.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.example.data.dto.AllMetaDataResponse
import org.example.data.dto.NotRefClassMetaDataResponse
import org.example.data.dto.PropertyClass
import org.example.data.dto.RefClassMetaDataResponse
import org.example.data.dto.TypesMetaDataResponse

@LLMDescription(
    """
        Инструменты для генерации запросов на языке 1С и получения
        "произвольных данных из учетной системы""")
class DataQueryToolSet(
    private val baseUrl: String = "http://77.95.56.147:65525/DevelopDaily/hs/agent_smart_api_v1"
) : ToolSet {

    @Tool
    @LLMDescription("""
        Получает все типы метаданных учетной системы
        Типы следует использовать для того чтобы получить список классов метаданных определенного типа
        Например:
        Инструмент возвращает следующий список:
         - Документы
         - Справочники
         - РегистрыСведений
         - РегистрыНакопления
         Для того чтобы получить список классов выбранного типа нужно вызвать инструмент GetMetadataByType 
         и передать выбранный тип параметром GetMetadataByType(Справочники) 
    """)
    suspend fun getTypesMetaData(): String{

        val url = "$baseUrl/get-types-metadata"
        return try {
            val response = executeGetTool(url, "getTypesMetaData")
            val json = Json.parseToJsonElement(response)

            val formatedResponse = MetadataFormatter.formatGetTypesMetaDataForLLM(json)
            println("📤 форматированный ответ инструмента  get-types-metadata $formatedResponse")
            formatedResponse
        }catch (e: Exception){
            println("❌ Ошибка в get-types-metadata: ${e.message}")

            // Простая структурированная ошибка
            buildString {
                appendLine("ОШИБКА при получении метаданных:")
                appendLine("• Сообщение: ${e.message}")
                appendLine("• Тип: ${e.javaClass.simpleName}")
                appendLine()
                appendLine("Рекомендации:")
                appendLine("1. Проверьте доступность сервера")
                appendLine("2. Убедитесь что API возвращает корректный JSON")
                appendLine("3. Проверьте права доступа")
            }
        }

    }

    @Tool
    @LLMDescription(
        """
Получает каталог метаданных определенного типа.
Запрещено использовать без предварительного вызова инструмента getTypesMetaData()
Требуется использовать для каждого типа метаданных, чтобы найти самый правильный ответ

ДЛЯ ИНФОРМАЦИОННЫХ ВОПРОСОВ ("что такое X?", "к какому типу X?"):
1. Вызови этот инструмент с параметром тип метаданных, возможно этот инструмент придется вызвать для каждого 
    типа метаданных
2. НАЙДИ конкретный объект в результатах
3. ОТВЕТЬ ПРЯМО пользователю

Пример:
- Вопрос: "К какому типу относятся ресурсы?"
- Находишь: {type: "Справочники", name: "Ресурсы"}
- Отвечаешь: "Ресурсы - это справочник системы"

❌ НЕ говори "используйте поиск..."
✅ Давай конкретный ответ

ДЛЯ ЗАПРОСОВ ДАННЫХ:
- Используй для поиска нужного объекта метаданных
- Затем вызывай getClassMetadata для изучения структуры
- Затем генерируй и выполняй SQL-запрос

СТРУКТУРА ОТВЕТА:
- type: тип объекта (Справочники, Документы, РегистрыСведений)
- id: идентификатор для запросов
- name: системное имя
- title: русское название
"""
    )
    suspend fun getMetadataByType(type: String): String {

        val url = "$baseUrl/get-all-metadata"
        return try {
            val requestBody = """
        {
            "request": {
                "type": "$type"
            }
        }
        """.trimIndent()

            val response = executePostTool(url, requestBody, "getMetadataByType")

            // Парсим JSON
            val json = Json.parseToJsonElement(response)

            // Формируем удобный для LLM текст
            val formatedResponse = MetadataFormatter.formatAllMetaDataForLLM(json)
            println("📤 форматированный ответ инструмента  get-metadata-by-type $formatedResponse")
            formatedResponse

        } catch (e: Exception) {
            println("❌ Ошибка в get-metadata-by-type: ${e.message}")
            e.printStackTrace()

            // Простая структурированная ошибка
            buildString {
                appendLine("ОШИБКА при получении метаданных:")
                appendLine("• Сообщение: ${e.message}")
                appendLine("• Тип: ${e.javaClass.simpleName}")
                appendLine()
                appendLine("Рекомендации:")
                appendLine("1. Проверьте доступность сервера")
                appendLine("2. Убедитесь что API возвращает корректный JSON")
                appendLine("3. Проверьте права доступа")
            }
        }

    }

    @Tool
    @LLMDescription(
        """
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
    
"""
    )
    suspend fun getClassMetadata(metaDataType: String, metaDataClass: String): String {

        val url = "$baseUrl/get-class-metadata"

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
            val json = Json.parseToJsonElement(response)
            val isRef: Boolean = json.jsonObject["response"]?.jsonObject["is_ref"].toString().toBoolean()
            val formatClassMetadata: String by lazy {
                if (isRef) {
                    MetadataFormatter.formatRefClassMetaDataForLLM(json)
                } else {
                    MetadataFormatter.formatNotRefClassMetaDataForLLM(json)
                }
            }

            //println("📤 форматированный ответ инструмента  get_class_metadata \n $formatClassMetadata")
            formatClassMetadata

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
    @LLMDescription("""""
    Инструмент получает описание справку по заданному объекту системы
        
    Параметры:
    - metaDataType: тип объекта (Справочники, Документы, РегистрыСведений, РегистрыНакопления, ПланыСчетов, etc)
    - metaDataClass: системное имя класса (например: "ОбъектыОбслуживания", "ЗаявкиНаРемонт")
    
    ВОЗВРАЩАЕМАЯ ИНФОРМАЦИЯ:
    - описание функционала (функциональная роль) объекта в учетной системе
    - общее справочное описание полей объекта
    - общее описание связанных с объектом сущностей (другие объекты системы, специальные алгоритмы, и прочее)
    
    ПРИМЕРЫ ИСПОЛЬЗОВАНИЯ:
    - getReference("Справочники", "ОбъектыОбслуживания") - справочная информация по запрошенному объекту системы
    
     ПРЕДУПРЕЖДЕНИЕ:
    - Тип и класс должны точно соответствовать значениям из getAllMetadata
    - Используй searchMetadata если не уверен в точном названии
    """")
    suspend fun getReference(metaDataType: String, metaDataClass: String): String {

        val url = "$baseUrl/get-reference"

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

            val response = executePostTool(url, requestBody, "getReference")
            val json = Json.parseToJsonElement(response)
            val textResponse = json.jsonObject["response"].toString()
            println("📤 форматированный ответ инструмента  get_reference \n $textResponse")
            textResponse

        } catch (e: Exception) {
            """{
            "error_type": "get_reference_retrieval_failed",
            "message": "Не удалось получить справку по объекту ТОиР",
            "details": "${e.message}",
            "suggestion": "Проверьте доступность сервера и повторите запрос"
        }"""
        }
    }

    @Tool
    @LLMDescription(
        """
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
"""
    )
    suspend fun getQueryLanguageDescription(): String {

        val url = "$baseUrl/get-query-language-description"
        return try {
            val response = executeGetTool(url, "getQueryLanguageDescription")
            val json = Json.parseToJsonElement(response)
            val textResponse = json.jsonObject["response"].toString()
            //println("📤 форматированный ответ инструмента  get_query_language_description \n $textResponse")
            textResponse
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
    @LLMDescription(
        """
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
"""
    )
    suspend fun executeQuery(query: String): String {

        val url = "$baseUrl/execute-query"

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

object MetadataFormatter {
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun formatGetTypesMetaDataForLLM(jsonElement: JsonElement): String{
        return try {
            val apiData = jsonConfig.decodeFromJsonElement<TypesMetaDataResponse>(jsonElement)
            val types = apiData.response.types
            if (types.isEmpty()) return "⚠️ Каталог пуст"
            buildString {
                appendLine("# КАТАЛОГ ДОСТУПНЫХ ТИПОВ МЕТАДАННЫХ СИСТЕМЫ")

                types.forEach { type ->
                        if (type.isEmpty()) return@forEach

                        // Используем Markdown-заголовки (эффективнее для LLM)
                        appendLine(" - $type")
                }
            }
        }catch (e: Exception){
            "❌ Ошибка данных: ${e.localizedMessage}"
        }
    }

    fun formatAllMetaDataForLLM(jsonElement: JsonElement): String {
        return try {
            // 1. Десериализация в типизированные объекты
            val apiData = jsonConfig.decodeFromJsonElement<AllMetaDataResponse>(jsonElement)
            val type = apiData.response.type
            val classes = apiData.response.classes

            if (classes.isEmpty()) return "⚠️ Каталог пуст"

            buildString {
                appendLine("# СИСТЕМНЫЙ КАТАЛОГ МЕТАДАННЫХ")
                appendLine("## Тип: $type")
                classes.forEach {item ->
                            // Сжатый формат: Название как ключевой элемент
                            appendLine("- **${item.title}**")
                            appendLine("  ID: ${item.id} | SysName: ${item.name}")
                }

                appendLine("\n### Инструкция по поиску:")
                appendLine("Используй `title` для поиска. Если нет совпадений, проверь `SysName`. " +
                        "Если результат не найден вызови инструмент снова со следующим параметром  - типом")
            }
        } catch (e: Exception) {
            "❌ Ошибка данных: ${e.localizedMessage}"
        }
    }

    fun formatRefClassMetaDataForLLM(jsonElement: JsonElement): String {
        return try {
            val apiData = jsonConfig.decodeFromJsonElement<RefClassMetaDataResponse>(jsonElement)

            val objectProperties = apiData.response.properties
            if (objectProperties.isEmpty()) return "⚠️ Каталог пуст"

            buildString {
                appendLine("# ОПИСАНИЕ ПОЛЕЙ МЕТАДАННЫХ")
                appendLine("## РЕКВИЗИТЫ")
                objectProperties.forEachIndexed { index, item ->
                    val property = item.property
                    appendLine(
                        "---\n ${index + 1} ID: ${property.name} | Имя: ${property.title.ifEmpty { property.name }}"
                    )
                    appendLine("**типы данных**")
                    val types = property.typesDescription.types
                    val enums = property.typesDescription.enums
                    types.forEach { item ->
                        appendLine(" - ${item.type}")
                    }
                }
                appendLine("## ТАБЛИЧНЫЕ ЧАСТИ")
                val objectTables = apiData.response.tables
                objectTables.forEach { item ->
                    val table = item.table
                    appendLine("** Табличная часть ${table.name} , синоним ${table.title}")
                    val properties = table.properties
                    properties.forEachIndexed { index, item ->
                        val property = item.property
                        appendLine("---\n ${index + 1}. ID: ${property.name} | Имя: ${property.title}")
                        appendLine("**типы данных**")
                        val types = property.typesDescription.types
                        types.forEach { item ->
                            appendLine(" - ${item.type}")
                        }
                        val enums = property.typesDescription.enums
                        if (enums.isNotEmpty()) {
                                appendLine("**значения перечислений**")
                                enums.forEach { item ->
                                    appendLine(" - ${item}")
                                }
                            }else ""
                    }
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка данных: ${e.localizedMessage}"
        }
    }

    fun formatNotRefClassMetaDataForLLM(jsonElement: JsonElement): String {
        return try {
            val apiData = jsonConfig.decodeFromJsonElement<NotRefClassMetaDataResponse>(jsonElement)
            val data = apiData.response
            buildString {
                appendLine("# МЕТАДАННЫЕ РЕГИСТРА: ${data.name.uppercase()}")

                // Вызываем общую логику для каждой категории
                appendSection("ИЗМЕРЕНИЯ", data.dimensions)
                appendSection("РЕСУРСЫ", data.resources)
                appendSection("РЕКВИЗИТЫ", data.attributes)
            }

        } catch (e: Exception) {
            "❌ Ошибка данных: ${e.localizedMessage}"
        }
    }

    private fun StringBuilder.appendSection(title: String, items: List<PropertyClass>) {
        if (items.isEmpty()) return

        appendLine("\n## $title")
        items.forEachIndexed { index, wrapper ->
            val prop = wrapper.property
            val types = prop.typesDescription.types.joinToString(", ") { it.type }
            val enums = prop.typesDescription.enums.joinToString(", ") {it}
            val name = prop.title.ifBlank { prop.name }

            // Компактный формат: Индекс. Название [ID] (Типы)
            appendLine("${index + 1}. **$name**")
            appendLine("   ID: `${prop.name}` | Типы: [$types] | Перечисления: [$enums]")
        }
    }

}
