package data.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import data.agent.guard.AgentExecutionContext
import data.agent.guard.AgentRunStateRegistry
import data.agent.guard.ToolCallDecision
import data.dto.DataQueryRequest
import data.dto.DataQueryResponse
import data.dto.TextQuery
import data.http_client.HttpClients
import integration.toir.relevantqueries.ToirRelevantQuerySearchClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.example.Config
import kotlin.math.min

class QueryToolSet(

    private val baseUrl: String = Config.BASE_URL_TOOL_SET,
    private val relevantQuerySearchClient: ToirRelevantQuerySearchClient =
        ToirRelevantQuerySearchClient(
            httpClient = HttpClients().default,
            baseUrl = baseUrl,
        )

) : ToolSet {

    private companion object {
        const val DEFAULT_MAX_TEMPLATES = 3
        const val DEFAULT_MAX_SUCCESSFUL_QUERIES = 5
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
    }

    /**
     * Сколько строк максимум показываем LLM в preview.
     * Это НЕ влияет на сам запрос (не добавляем ПЕРВЫЕ).
     */
    private val defaultPreviewLimit = 15

    /** Максимальная длина строкового значения в preview (чтобы не взрывать контекст). */
    private val maxValueLen = 220

    /** Максимальное число колонок, которое передаём в columns (чтобы не раздуть ответ). */
    private val maxColumns = 80


    @Tool
    @LLMDescription(
        """
Инструмент выполняет переданный запрос на языке запросов 1С и возвращает результат.

ВАЖНО:
- Инструмент НЕ изменяет запрос (не добавляет ПЕРВЫЕ/ТОП и т.п.)
- Разрешены только SELECT-запросы (должен начинаться с 'ВЫБРАТЬ')
- При ошибке возвращаются: error_message + row_number + column_number (для анализа LLM)

ПАРАМЕТРЫ:
- query: строка запроса (только 'ВЫБРАТЬ ...')

РЕЗУЛЬТАТ:
- ok=true: result содержит данные результата
- ok=false: error содержит error_message, row_number, column_number

ОБЯЗАТЕЛЬНОЕ ПОВЕДЕНИЕ LLM ПРИ ОШИБКЕ:
- Если is_error=true: 
  1) вызови getQueryLanguageDescription() без параметров (получи INDEX),
  2) выбери нужную секцию и вызови getQueryLanguageDescription(sectionId),
  3) пересобери запрос корректно и повтори executeQuery.
- Не пытайся “угадывать” синтаксис без справки.
"""
    )
    suspend fun executeQuery(query: String): String {
        val url = "$baseUrl/execute-query"
        val normalizedQuery = normalizeQuery(query)

        AgentExecutionContext.sessionIdOrNull()?.let { sessionId ->
            when (
                val decision = AgentRunStateRegistry.beforeToolCall(
                    sessionId = sessionId,
                    toolName = "executeQuery",
                    args = mapOf("query" to normalizedQuery)
                )
            ) {
                is ToolCallDecision.Allow -> Unit
                is ToolCallDecision.Deny -> return decision.payload
            }
        }

        validateSelectOnly(normalizedQuery)?.let { msg ->
            val payload = llmToolPayload(
                ok = false,
                query = normalizedQuery,
                error = buildJsonObject {
                    put("type", "invalid_query")
                    put("error_message", msg)
                    put("row_number", 0)
                    put("column_number", 0)
                    put("must_retry", true)
                },
                suggestions = listOf(
                    "Разрешены только запросы, начинающиеся с 'ВЫБРАТЬ'.",
                    "Проверьте таблицы/поля через getClassMetadata(...) и пересоберите запрос."
                )
            )

            AgentExecutionContext.sessionIdOrNull()?.let { sessionId ->
                AgentRunStateRegistry.markExecuteQueryResult(sessionId, payload)
            }

            return payload
        }

        validateQueryShape(normalizedQuery)?.let { msg ->
            val payload = llmToolPayload(
                ok = false,
                query = normalizedQuery,
                error = buildJsonObject {
                    put("type", "invalid_query")
                    put("error_message", msg)
                    put("row_number", 0)
                    put("column_number", 0)
                    put("must_retry", true)
                },
                suggestions = listOf(
                    "Разрешены только запросы, начинающиеся с 'ВЫБРАТЬ'.",
                    "Проверьте таблицы/поля через getClassMetadata(...) и пересоберите запрос."
                )
            )

            AgentExecutionContext.sessionIdOrNull()?.let { sessionId ->
                AgentRunStateRegistry.markExecuteQueryResult(sessionId, payload)
            }

            return payload
        }

        return try {
            val requestBody = json.encodeToString(
                DataQueryRequest(request = TextQuery(textQuery = normalizedQuery))
            )

            val rawResponse = executePostTool(url, requestBody, "executeQuery")

            // 2) Единственный формат на успех/ошибку сервера 1С
            val formattedResponse = formatQueryResponse(rawResponse = rawResponse, query = normalizedQuery)
            AgentExecutionContext.sessionIdOrNull()?.let { sessionId ->
                AgentRunStateRegistry.markExecuteQueryResult(
                    sessionId = sessionId,
                    toolPayload = formattedResponse
                )
            }

            formattedResponse

        } catch (e: Exception) {
            val payload = llmToolPayload(
                ok = false,
                query = normalizedQuery,
                error = buildJsonObject {
                    put("type", "invalid_query")
                    put("error_message", e.message)
                    put("row_number", 0)
                    put("column_number", 0)
                    put("must_retry", true)
                },
                suggestions = listOf(
                    "Разрешены только запросы, начинающиеся с 'ВЫБРАТЬ'.",
                    "Проверьте таблицы/поля через getClassMetadata(...) и пересоберите запрос."
                )
            )

            AgentExecutionContext.sessionIdOrNull()?.let { sessionId ->
                AgentRunStateRegistry.markExecuteQueryResult(sessionId, payload)
            }

            return payload
        }
    }

    @Tool
    @LLMDescription(
        """
Инструмент возвращает секционированную справку по языку запросов 1С (русскоязычный аналог SQL).

КАК ИСПОЛЬЗОВАТЬ:
- Если sectionId НЕ задан (null/пусто) — вернётся индекс разделов (id = INDEX) со списком идентификаторов.
- Если sectionId задан — вернётся конкретная секция справки.

ПАРАМЕТРЫ:
- sectionId (необязательный): идентификатор секции (например: QL_BASE, QL_WHERE, QL_GROUP, QL_JOIN, QL_TEMP, QL_FUNC_AGG и т.д.)

КОГДА ВЫЗЫВАТЬ (ОБЯЗАТЕЛЬНО ДЛЯ LLM):
1) Перед генерацией запроса средней/высокой сложности:
   - Сначала вызови без параметров (получи INDEX),
   - Затем вызови нужную секцию(и).
2) Если executeQuery вернул ошибку (is_error=true):
   - НЕЛЬЗЯ трактовать это как “0 записей” или “нет данных”.
   - Обязательно вызови этот инструмент (INDEX → секция по смыслу ошибки) и пересобери запрос.
3) Если сомневаешься в синтаксисе ключевых слов/функций/соединений — сначала справка, потом запрос.

СЕКЦИИ (sectionId → назначение):
- QL_BASE → базовый каркас запроса и ключевые ключевые слова: ВЫБРАТЬ / ИЗ / КАК / ГДЕ / СГРУППИРОВАТЬ ПО / ИМЕЮЩИЕ / УПОРЯДОЧИТЬ ПО.
- QL_ACCESS → РАЗРЕШЕННЫЕ (RLS) и влияние прав доступа на выборку.
- QL_RESULT_SHAPE → РАЗЛИЧНЫЕ / ПЕРВЫЕ / автоупорядочивание и общая “форма” результата.
- QL_WHERE → фильтрация (ГДЕ): предикаты, операторы, ПОДОБНО, В, МЕЖДУ, ЕСТЬ NULL и т.п.
- QL_EXPR_TYPES → выражения и работа с типами: ВЫБОР, ВЫРАЗИТЬ, ТИП, ТИПЗНАЧЕНИЯ, ЗНАЧЕНИЕ, ДАТАВРЕМЯ.
- QL_GROUP → группировки и агрегации: СГРУППИРОВАТЬ ПО, ИМЕЮЩИЕ, группирующие наборы.
- QL_ORDER → сортировка: УПОРЯДОЧИТЬ ПО, ИЕРАРХИЯ, ВОЗР/УБЫВ.
- QL_SETOPS → операции над результатами: ОБЪЕДИНИТЬ, ОБЪЕДИНИТЬ ВСЕ, ПУСТАЯТАБЛИЦА.
- QL_TEMP → временные таблицы: ПОМЕСТИТЬ, ДОБАВИТЬ, УНИЧТОЖИТЬ, ИНДЕКСИРОВАТЬ ПО.
- QL_JOIN → соединения: ЛЕВОЕ/ПРАВОЕ/ПОЛНОЕ/ВНУТРЕННЕЕ СОЕДИНЕНИЕ, условие ПО.
- QL_TOTALS → итоги: ИТОГИ ПО ... (общие/иерархия/периодам и т.п.).
- QL_FUNC_CORE → базовые функции: ПРЕДСТАВЛЕНИЕ, ЕСТЬNULL, УНИКАЛЬНЫЙИДЕНТИФИКАТОР,  и др.
- QL_FUNC_STR → строковые функции (ДлинаСтроки, Лев/Прав, СтрЗаменить и т.п.).
- QL_FUNC_DATE → функции дат/времени (ГОД, МЕСЯЦ, НАЧАЛОПЕРИОДА, ДОБАВИТЬКДАТЕ и т.п.).
- QL_FUNC_MATH → математические функции (Окр, Цел, Sin/Cos и т.п.).
- QL_FUNC_AGG → агрегатные функции (КОЛИЧЕСТВО, СУММА, МАКСИМУМ, МИНИМУМ, СРЕДНЕЕ, РАЗЛИЧНЫЕ в агрегатах).

КАК ВЫБРАТЬ СЕКЦИЮ ПО ОШИБКЕ executeQuery:
- “Синтаксическая ошибка …” → начни с QL_BASE, затем уточни по контексту:
  - ошибка в функциях/агрегации → QL_FUNC_AGG / QL_FUNC_CORE
  - ошибка в ГДЕ/операторах → QL_WHERE
  - ошибка в СГРУППИРОВАТЬ/ИМЕЮЩИЕ → QL_GROUP
  - ошибка в СОЕДИНЕНИЕ/ПО → QL_JOIN
  - ошибка во временных таблицах → QL_TEMP
  - ошибка в ОБЪЕДИНИТЬ → QL_SETOPS
- “Поле не найдено …” → это не про синтаксис: сначала getClassMetadata и исправь имя/путь поля, при необходимости QL_BASE.
- “Объект/таблица не найдены …” → сначала getMetadataByType и используй qualified id (например: Справочник.<Имя>), при необходимости QL_BASE.

ВАЖНО:
- Этот инструмент — источник истины по синтаксису. Не придумывай ключевые слова/функции “по памяти”.
- Секции можно вызывать несколько раз, но избегай лишних вызовов: выбирай наиболее релевантную секцию по задаче/ошибке.
"""
    )
    suspend fun getQueryLanguageDescription(sectionId: String? = null): String {

        AgentExecutionContext.sessionIdOrNull()?.let { sessionId ->
            when (
                val decision = AgentRunStateRegistry.beforeToolCall(
                    sessionId = sessionId,
                    toolName = "getQueryLanguageDescription",
                    args = mapOf("sectionId" to (sectionId ?: "INDEX"))
                )
            ) {
                is ToolCallDecision.Allow -> Unit
                is ToolCallDecision.Deny -> return decision.payload
            }
        }

        val url = "${Config.BASE_URL_TOOL_SET}/get-query-language-description"

        val requestBody = buildJsonObject {
            putJsonObject("request") {
                val id = sectionId?.trim().orEmpty()
                if (id.isNotEmpty()) put("id", id)
                else put("id", "")
            }
        }.toString()

        return try {
            val raw = executePostTool(url, requestBody, "getQueryLanguageDescription")
            val formatedResponse = QueryLanguageDescriptionFormatter.format(rawResponse = raw, requestedId = sectionId)
            println("📤 форматированный ответ инструмента  get-types-metadata $formatedResponse")
            formatedResponse

        } catch (e: Exception) {
            QueryLanguageDescriptionFormatter.error(
                requestedId = sectionId,
                message = "Не удалось получить справку по языку запросов 1С",
                details = e.message ?: "unknown"
            )
        }
    }

    @Tool
    @LLMDescription(
        """
Ищет сохранённые успешные запросы и шаблоны, релевантные вопросу пользователя.

ПАРАМЕТРЫ:
- question: исходный вопрос пользователя о данных системы.

ВАЖНО:
- Найденные шаблоны и успешные запросы — только ориентиры.
- Они не являются актуальным ответом.
- После получения результата нужно сформировать и выполнить новый executeQuery.
- Если ничего не найдено, продолжи обычный путь через метаданные.
"""
    )
    suspend fun getSimilarSuccessfulQueries(question: String): String {
        AgentExecutionContext.sessionIdOrNull()?.let { sessionId ->
            when (
                val decision = AgentRunStateRegistry.beforeToolCall(
                    sessionId = sessionId,
                    toolName = "getSimilarSuccessfulQueries",
                    args = mapOf("question" to question),
                )
            ) {
                is ToolCallDecision.Allow -> Unit
                is ToolCallDecision.Deny -> return decision.payload
            }
        }

        val result = relevantQuerySearchClient.search(
            question = question,
            maxTemplates = DEFAULT_MAX_TEMPLATES,
            maxSuccessfulQueries = DEFAULT_MAX_SUCCESSFUL_QUERIES,
        )

        val formattedResult = RelevantQuerySearchToolResponseFormatter.format(result)
        println(
            "🔧 [TOOL] getSimilarSuccessfulQueries → LLM payload:\n$formattedResult"
        )
        return formattedResult
    }

    /**
     * Готовит LLM-friendly payload:
     * - Если is_error=true: возвращает структурированную ошибку с координатами.
     * - Если is_error=false: возвращает preview первых N строк + мета.
     */
    fun formatQueryResponse(
        rawResponse: String,
        query: String,
        previewLimit: Int = defaultPreviewLimit
    ): String {
        val dto = try {
            json.decodeFromString<DataQueryResponse>(rawResponse)
        } catch (e: Exception) {
            return llmToolPayload(
                ok = false,
                query = query,
                error = buildJsonObject {
                    put("type", "invalid_tool_response")
                    put("error_message", "Не удалось разобрать ответ execute-query (невалидный JSON/контракт).")
                    put("details", e.message ?: "unknown")
                    put("row_number", 0)
                    put("column_number", 0)
                    put("must_retry", true)
                },
                suggestions = listOf(
                    "Проверьте контракт ответа /execute-query.",
                    "Убедитесь, что DataQueryResponse соответствует JSON."
                ),
            )
        }

        val r = dto.response

        // ✅ Ошибка: строго ok=false, без result/preview
        if (r.isError) {
            val msg = r.errorMessage.takeIf { it.isNotBlank() } ?: "Неизвестная ошибка выполнения запроса"
            return llmToolPayload(
                ok = false,
                query = query,
                error = buildJsonObject {
                    put("type", "query_error")
                    put("error_message", msg)
                    put("row_number", r.rowNumber)
                    put("column_number", r.columnNumber)
                    put("must_retry", true)
                },
                suggestions = listOf(
                    "Вызовите getQueryLanguageDescription(INDEX → нужная секция) и исправьте синтаксис.",
                    "Сверьте структуру через getClassMetadata(...) и пересоберите запрос (таблица, псевдонимы, поля).",
                    "Повторите executeQuery после исправления."
                ),
            )
        }

        // ✅ Успех: query_result — JsonArray
        val rows = r.queryResult
        val rowCount = rows.size
        val showCount = min(previewLimit.coerceAtLeast(0), rowCount)

        val warnings = mutableListOf<String>()
        if (rowCount > showCount) warnings += "Результат содержит $rowCount строк; показаны первые $showCount."

        // Собираем колонки по preview
        val columns = LinkedHashSet<String>()
        for (i in 0 until showCount) {
            val obj = rows[i].asJsonObjectOrNull() ?: continue
            for (k in obj.keys) {
                if (columns.size >= maxColumns) break
                columns.add(k)
            }
            if (columns.size >= maxColumns) break
        }
        if (columns.size >= maxColumns) warnings += "Колонок много; список columns ограничен первыми $maxColumns."

        // Preview
        val previewArray = buildJsonArray {
            for (i in 0 until showCount) {
                val obj = rows[i].asJsonObjectOrNull()
                add(if (obj == null) rows[i] else truncateRow(obj))
            }
        }

        // Scalar: 1 строка + 1 поле
        val scalar = if (rowCount == 1) {
            val obj = rows[0].asJsonObjectOrNull()
            if (obj != null && obj.size == 1) {
                val (k, v) = obj.entries.first()
                buildJsonObject {
                    put("name", k)
                    put("value", truncateElement(v))
                }
            } else null
        } else null

        val result = buildJsonObject {
            put("row_count", rowCount)
            put("has_more", rowCount > showCount)
            putJsonArray("columns") { columns.forEach { add(it) } }
            put("preview", previewArray)
            scalar?.let { put("scalar", it) }
        }

        return llmToolPayload(
            ok = true,
            query = query,
            result = result,
            warnings = warnings,
        )
    }

    private fun llmToolPayload(
        ok: Boolean,
        query: String,
        result: JsonObject? = null,
        error: JsonObject? = null,
        warnings: List<String> = emptyList(),
        suggestions: List<String> = emptyList()
    ): String {
        val obj = buildJsonObject {
            put("tool", "executeQuery")
            put("ok", ok)
            put("final_answer_allowed", ok) // ключевой барьер
            put("query", query)
            result?.let { put("result", it) }
            error?.let { put("error", it) }

            if (warnings.isNotEmpty()) putJsonArray("warnings") { warnings.forEach { add(it) } }
            if (suggestions.isNotEmpty()) putJsonArray("suggestions") { suggestions.forEach { add(it) } }
        }
        return obj.toString()
    }

    private fun normalizeQuery(q: String): String =
        q.replace("\u00A0", " ").trim()

    private fun validateSelectOnly(q: String): String? {
        if (q.isBlank()) return "Пустой запрос."

        val trimmed = q.trimStart()
        if (!trimmed.startsWith("ВЫБРАТЬ", ignoreCase = true)) {
            return "Запрос отклонён: разрешены только SELECT-запросы языка 1С (должен начинаться с 'ВЫБРАТЬ')."
        }

        // Простая защита: отсечём очевидные DML/DDL
        val forbidden = listOf(
            "УДАЛИТЬ", "ОБНОВИТЬ", "ВСТАВИТЬ", "ИЗМЕНИТЬ", "СОЗДАТЬ",
            "DROP", "DELETE", "UPDATE", "INSERT"
        )
        if (forbidden.any { trimmed.contains(it, ignoreCase = true) }) {
            return "Запрос отклонён: обнаружены запрещённые операции. Разрешён только 'ВЫБРАТЬ'."
        }

        return null
    }

    private fun validateQueryShape(q: String): String? {
        val s = q.trim()

        // базовый каркас
        if (!s.startsWith("ВЫБРАТЬ", ignoreCase = true)) {
            return "Запрос должен начинаться с 'ВЫБРАТЬ'."
        }
        if (!Regex("""\bИЗ\b""", RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            return "В запросе отсутствует секция 'ИЗ'. Каркас: ВЫБРАТЬ ... ИЗ <Таблица> КАК <Псевдоним> ..."
        }

        // 1) запрещаем одинарные кавычки (чтобы LLM не уходила в 'Утвержден')
        if (s.contains('\'')) {
            return "Одинарные кавычки запрещены. Строковые литералы задаются в двойных кавычках \"...\"."
        }

        // 2) агрегат КОЛИЧЕСТВО: пустые скобки
        if (Regex("""КОЛИЧЕСТВО\s*\(\s*\)""", RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            return "КОЛИЧЕСТВО() недопустимо. Укажите выражение, обычно: КОЛИЧЕСТВО(<Псевдоним>.Ссылка)."
        }

        // 3) агрегат КОЛИЧЕСТВО: звёздочка
        if (Regex("""КОЛИЧЕСТВО\s*\(\s*\*\s*\)""", RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            return "КОЛИЧЕСТВО(*) нежелательно/часто не поддерживается. Используйте КОЛИЧЕСТВО(<Псевдоним>.Ссылка)."
        }

        // 4) неверные конструкции перечислений, которые LLM часто выдумывает
        if (Regex("""ЗначениеПеречисления\.""", RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            return "Конструкция 'ЗначениеПеречисления.*' не поддерживается. Для перечислений используйте: ЗНАЧЕНИЕ(Перечисление.<ИмяПеречисления>.<Значение>)."
        }
        if (Regex("""ПеречислениеСсылка\.""", RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            return "Нельзя использовать 'ПеречислениеСсылка.*' как значение в запросе. Используйте: ЗНАЧЕНИЕ(Перечисление.<ИмяПеречисления>.<Значение>)."
        }

        return null
    }

    /** Обрезает все строковые значения в строке результата. */
    private fun truncateRow(obj: JsonObject): JsonObject =
        buildJsonObject {
            for ((k, v) in obj) {
                put(k, truncateElement(v))
            }
        }

    private fun truncateElement(el: JsonElement): JsonElement {
        return when (el) {
            is JsonPrimitive -> {
                if (el.isString) {
                    val s = el.content
                    JsonPrimitive(if (s.length > maxValueLen) s.take(maxValueLen) + "…" else s)
                } else el
            }

            else -> el
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        try {
            this.jsonObject
        } catch (_: Exception) {
            null
        }

    /** POST вызов тул-сервиса. */
    private suspend fun executePostTool(url: String, requestBody: String, toolName: String): String {
        val response = httpClient.post(url) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(HttpHeaders.Accept, "application/json")
            setBody(requestBody)
        }

        val responseBody = response.bodyAsText()

        // В проде лучше заменить на нормальный logger с уровнем debug
        println("🔧 [TOOL] $toolName - Status: ${response.status}")
        println("📤 [REQUEST] $requestBody") // обычно лучше не логировать полностью
        println("📥 [RESPONSE] ${responseBody.take(2000)}")

        return responseBody
    }


}
