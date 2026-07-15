package data.agent.guard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

enum class TaskKind {
    DATA_QUERY,
    METADATA_INFO,
    REFERENCE_INFO,
    CHAT,
    UNKNOWN
}

data class AgentTurnState(
    val sessionId: String,
    val originalUserMessage: String,
    val taskKind: TaskKind,

    var similarMetadataLoaded: Boolean = false,
    var classMetadataLoaded: Boolean = false,

    var executeQueryCalled: Boolean = false,
    var executeQuerySucceeded: Boolean = false,
    var executeQueryFailed: Boolean = false,

    var lastExecuteQuery: String? = null,
    var lastExecuteQueryError: String? = null,

    var deniedFinalAttempts: Int = 0,

    val toolCallCounters: MutableMap<String, Int> = linkedMapOf(),
    val toolNameCounters: MutableMap<String, Int> = linkedMapOf(),

    var loopDetected: Boolean = false,
    var loopReason: String? = null,
    var loopSuggestion: String? = null
)

object AgentRunStateRegistry {

    private const val MAX_DENIED_FINAL_ATTEMPTS = 4

    private val states = ConcurrentHashMap<String, AgentTurnState>()

    fun startUserTurn(sessionId: String, message: String) {
        states[sessionId] = AgentTurnState(
            sessionId = sessionId,
            originalUserMessage = message,
            taskKind = classifyTask(message)
        )
    }

    fun get(sessionId: String): AgentTurnState? =
        states[sessionId]

    fun markSimilarMetadataLoaded(sessionId: String) {
        states[sessionId]?.similarMetadataLoaded = true
    }

    fun markClassMetadataLoaded(sessionId: String) {
        states[sessionId]?.classMetadataLoaded = true
    }

    fun markExecuteQueryResult(sessionId: String, toolPayload: String) {
        val state = states[sessionId] ?: return

        state.executeQueryCalled = true
        state.lastExecuteQueryError = null

        runCatching {
            val root = Json.parseToJsonElement(toolPayload).jsonObject
            val ok = root["ok"]?.jsonPrimitive?.booleanOrNull == true
            val finalAllowed = root["final_answer_allowed"]?.jsonPrimitive?.booleanOrNull == true

            state.executeQuerySucceeded = ok && finalAllowed
            state.executeQueryFailed = !ok

            state.lastExecuteQuery =
                root["query"]?.jsonPrimitive?.contentOrNullSafe()

            if (!ok) {
                state.lastExecuteQueryError =
                    root["error"]
                        ?.jsonObject
                        ?.get("error_message")
                        ?.jsonPrimitive
                        ?.contentOrNullSafe()
            }
        }.onFailure {
            state.executeQuerySucceeded = false
            state.executeQueryFailed = true
            state.lastExecuteQueryError = "Не удалось разобрать payload executeQuery: ${it.message}"
        }
    }

    fun canFinish(sessionId: String): Boolean {
        val state = states[sessionId] ?: return true

        return when (state.taskKind) {
            TaskKind.DATA_QUERY ->
                state.executeQuerySucceeded

            TaskKind.METADATA_INFO ->
                state.similarMetadataLoaded || state.classMetadataLoaded

            TaskKind.REFERENCE_INFO ->
                state.classMetadataLoaded

            TaskKind.CHAT ->
                true

            TaskKind.UNKNOWN ->
                true
        }
    }

    fun registerDeniedFinalAttempt(sessionId: String): Boolean {

        val state = states[sessionId] ?: return false
        state.deniedFinalAttempts += 1

        val limit = if (state.loopDetected) 2 else MAX_DENIED_FINAL_ATTEMPTS

        return state.deniedFinalAttempts <= limit
    }

    fun buildContinueInstruction(sessionId: String): String {
        val state = states[sessionId]

        return when (state?.taskKind) {
            TaskKind.DATA_QUERY -> buildString {
                appendLine("Служебная инструкция выполнения.")
                appendLine("Предыдущий ответ был промежуточным и не должен быть отдан пользователю.")
                appendLine("Исходный вопрос пользователя: ${state.originalUserMessage}")
                appendLine()
                appendLine("Это вопрос по данным системы.")
                appendLine("Финальный ответ пользователю запрещен, пока не выполнен успешный executeQuery.")
                appendLine()
                appendLine("Продолжи выполнение задачи:")
                appendLine("1. Если не хватает структуры объекта — вызови getClassMetadata.")
                appendLine("2. Если не хватает синтаксиса — вызови getQueryLanguageDescription.")
                appendLine("3. Если предыдущий executeQuery завершился ошибкой — исправь запрос и повтори executeQuery.")
                appendLine("4. Если executeQuery еще не вызывался — сформируй корректный запрос и вызови executeQuery.")
                appendLine("5. Не пиши промежуточный текст пользователю.")
                appendLine()

                state.lastExecuteQueryError?.let {
                    appendLine("Последняя ошибка executeQuery:")
                    appendLine(it)
                    appendLine()
                }

                state.lastExecuteQuery?.let {
                    appendLine("Последний запрос:")
                    appendLine(it)
                    appendLine()
                }
            }

            TaskKind.METADATA_INFO -> """
                Служебная инструкция выполнения.
                Предыдущий ответ был промежуточным.
                Заверши ответ только на основании найденных метаданных.
                Если данных метаданных недостаточно — вызови getClassMetadata.
                Не пиши промежуточный текст пользователю.
            """.trimIndent()

            TaskKind.REFERENCE_INFO -> """
                Служебная инструкция выполнения.
                Предыдущий ответ был промежуточным.
                Для справки используй метаданные и, если разрешено, getReference.
                Не пиши промежуточный текст пользователю.
            """.trimIndent()

            else -> """
                Служебная инструкция выполнения.
                Предыдущий ответ был промежуточным.
                Продолжи выполнение задачи и дай только финальный ответ.
            """.trimIndent()
        }
    }

    fun buildHardStopMessage(sessionId: String): String {

        val state = states[sessionId]

        return buildString {
            appendLine("Не удалось автоматически завершить задачу корректно.")

            if (state?.loopDetected == true) {
                appendLine()
                appendLine("Обнаружено зацикливание при выборе инструментов.")
                state.loopReason?.let {
                    appendLine("Причина: $it")
                }
                state.loopSuggestion?.let {
                    appendLine()
                    appendLine("Рекомендуемое направление:")
                    appendLine(it)
                }
            }

            if (state?.taskKind == TaskKind.DATA_QUERY && !state.executeQuerySucceeded) {
                appendLine()
                appendLine("Вопрос требует получения данных, но успешный executeQuery не был выполнен.")
            }

            state?.lastExecuteQueryError?.let {
                appendLine()
                appendLine("Последняя ошибка executeQuery:")
                appendLine(it)
            }
        }.trim()
    }

    private fun classifyTask(message: String): TaskKind {
        val text = message.lowercase()

        val dataMarkers = listOf(
            "сколько",
            "посчитай",
            "покажи",
            "найди",
            "выведи",
            "какие",
            "список",
            "количество",
            "с отбором",
            "где"
        )

        val metadataMarkers = listOf(
            "что такое",
            "какие поля",
            "поля этого",
            "к какому типу",
            "метаданные",
            "реквизиты",
            "табличные части"
        )

        val referenceMarkers = listOf(
            "сформируй справку",
            "сделай справку",
            "подготовь справку",
            "опиши объект",
            "описание объекта"
        )

        return when {
            referenceMarkers.any { it in text } -> TaskKind.REFERENCE_INFO
            metadataMarkers.any { it in text } -> TaskKind.METADATA_INFO
            dataMarkers.any { it in text } -> TaskKind.DATA_QUERY
            else -> TaskKind.CHAT
        }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()

    fun beforeToolCall(
        sessionId: String,
        toolName: String,
        args: Map<String, String>
    ): ToolCallDecision {
        val state = states[sessionId] ?: return ToolCallDecision.Allow

        val signature = buildToolSignature(toolName, args)

        val exactCount = (state.toolCallCounters[signature] ?: 0) + 1
        state.toolCallCounters[signature] = exactCount

        val toolCount = (state.toolNameCounters[toolName] ?: 0) + 1
        state.toolNameCounters[toolName] = toolCount

        // 1. Один и тот же tool + те же аргументы нельзя дергать бесконечно.
        if (exactCount > 2) {
            state.loopDetected = true
            state.loopReason = "Повторный вызов инструмента $toolName с теми же аргументами."
            state.loopSuggestion = buildLoopSuggestion(toolName, args, state)

            return ToolCallDecision.Deny(
                payload = buildToolLoopPayload(
                    toolName = toolName,
                    args = args,
                    reason = state.loopReason!!,
                    suggestion = state.loopSuggestion!!
                )
            )
        }

        // 2. Для getSimilarMetaData ограничиваем количество поисков на один пользовательский запрос.
        if (toolName == "getSimilarMetaData" && toolCount > 6) {
            state.loopDetected = true
            state.loopReason = "Слишком много поисков метаданных без выполнения запроса."
            state.loopSuggestion = buildLoopSuggestion(toolName, args, state)

            return ToolCallDecision.Deny(
                payload = buildToolLoopPayload(
                    toolName = toolName,
                    args = args,
                    reason = state.loopReason!!,
                    suggestion = state.loopSuggestion!!
                )
            )
        }

        return ToolCallDecision.Allow
    }

    private fun buildToolSignature(
        toolName: String,
        args: Map<String, String>
    ): String {
        val normalizedArgs = args
            .toSortedMap()
            .mapValues { (_, value) -> normalizeToolArg(value) }

        return "$toolName:$normalizedArgs"
    }

    private fun normalizeToolArg(value: String): String {
        return value
            .trim()
            .lowercase()
            .replace("ё", "е")
            .replace(Regex("\\s+"), " ")
    }

    private fun buildLoopSuggestion(
        toolName: String,
        args: Map<String, String>,
        state: AgentTurnState
    ): String {
        return when {
            state.taskKind == TaskKind.DATA_QUERY &&
                    toolName == "getSimilarMetaData" -> """
            Прекрати повторять поиск метаданных.
            Это вопрос по данным, а не по поиску метаданных.
            
            Если объект метаданных уже определен, переходи к:
            1. getClassMetadata, если структура еще не подтверждена;
            2. getQueryLanguageDescription, если нужен синтаксис;
            3. executeQuery, если структура достаточна.
            
            Для ссылочных полей не ищи значение как метаданные.
            Если поле имеет тип СправочникСсылка.X, а пользователь задал текстовое значение,
            строй условие через ALIAS.Поле.Наименование = "значение"
            или через соединение со справочником X.
            """.trimIndent()

            state.taskKind == TaskKind.DATA_QUERY -> """
            Прекрати повторять тот же инструмент.
            Для вопроса по данным нужно перейти к формированию и выполнению executeQuery.
            """.trimIndent()

            else -> """
            Прекрати повторять тот же инструмент с теми же аргументами.
            Используй уже полученный результат или задай уточняющий вопрос.
            """.trimIndent()
        }
    }

    private fun buildToolLoopPayload(
        toolName: String,
        args: Map<String, String>,
        reason: String,
        suggestion: String
    ): String {
        return buildString {
            appendLine("{")
            appendLine("""  "tool": "ToolLoopGuard",""")
            appendLine("""  "ok": false,""")
            appendLine("""  "type": "repeated_tool_call",""")
            appendLine("""  "blocked_tool": "$toolName",""")
            appendLine("""  "blocked_args": ${args.toJsonObjectString()},""")
            appendLine("""  "reason": ${reason.jsonString()},""")
            appendLine("""  "must_not_repeat": true,""")
            appendLine("""  "next_action_required": true,""")
            appendLine("""  "suggestion": ${suggestion.jsonString()}""")
            appendLine("}")
        }
    }

    private fun Map<String, String>.toJsonObjectString(): String {
        return entries.joinToString(
            prefix = "{",
            postfix = "}"
        ) { (k, v) ->
            "${k.jsonString()}: ${v.jsonString()}"
        }
    }

    private fun String.jsonString(): String {
        return "\"" + this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""
    }
}