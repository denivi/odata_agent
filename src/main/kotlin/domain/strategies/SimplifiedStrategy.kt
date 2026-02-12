package org.example.domain.strategies

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeAppendPrompt
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall

/**
 * Упрощенная стратегия с проверкой качества ответа для AI агента ТОИР
 *
 * Особенности:
 * 1. Проверяет качество всех ответов на наличие общих фраз
 * 2. Автоматически запрашивает уточнение при обнаружении общих ответов
 * 3. Ограничивает количество итераций для предотвращения зацикливания
 */
fun createSimplifiedStrategy(): AIAgentGraphStrategy<String, String> {
    return strategy("SimplifiedDataQueryStrategy") {
        val nodeSendInput by nodeLLMRequest()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult()

        // Узел для добавления строгого промпта при обнаружении общего ответа
        val nodeAddStrictPrompt by nodeAppendPrompt<String>("addStrictPrompt") {
            system("⚠️ ВАЖНО: предыдущий ответ был слишком общим!")
            user("""
                    Пользователь задал простой вопрос и ожидает КОРОТКИЙ КОНКРЕТНЫЙ ответ.
                    
                    ❌ НЕ давай инструкций "как искать"
                    ❌ НЕ говори "используйте следующие рекомендации"
                    ❌ НЕ перечисляй варианты действий
                    
                    ✅ НАЙДИ конкретный объект в данных, которые ты УЖЕ получил
                    ✅ ОТВЕТЬ ПРЯМО на вопрос пользователя
                    
                    Формат ответа должен быть коротким:
                    "[Объект] - это [тип] системы"
                    или
                    "[Объект] относится к [категория]"
                    
                    Пример правильного ответа:
                    "Ресурсы - это справочник системы"
                """.trimIndent())

        }

        // Узел для повторного запроса LLM после добавления строгого промпта
        val nodeRequestClarification by nodeLLMRequest("requestClarification")

        // Счетчики
        var toolIterations = 0
        var clarificationAttempts = 0

        // Функция проверки качества ответа
        suspend fun isGeneralResponse(response: String): Boolean {
            val badPhrases = listOf(
                "используйте следующие рекомендации",
                "как искать:",
                "можете найти",
                "попробуйте использовать",
                "вы можете",
                "чтобы найти",
                "для поиска используйте",
                "рекомендации по поиску",
                "следующие шаги"
            )

            return badPhrases.any { response.contains(it, ignoreCase = true) }
        }

        // === ГРАФ СТРАТЕГИИ ===

        edge(nodeStart forwardTo nodeSendInput)

        // LLM ответил без инструментов - проверяем качество
        edge(
            (nodeSendInput forwardTo nodeFinish)
                .onAssistantMessage { message ->
                    val isGeneral = isGeneralResponse(message.content)

                    if (!isGeneral) {
                        println("✅ LLM ответил без инструментов - ответ конкретный")
                        true
                    } else {
                        println("⚠️ LLM ответил без инструментов - ответ общий")
                        false
                    }
                }
        )

        // LLM ответил общим ответом без инструментов - запрашиваем уточнение
        edge(
            (nodeSendInput forwardTo nodeAddStrictPrompt)
                .onAssistantMessage { message ->
                    val isGeneral = isGeneralResponse(message.content)

                    if (isGeneral && clarificationAttempts < 2) {
                        clarificationAttempts++
                        println("🔄 Ответ общий, добавляю строгий промпт (попытка $clarificationAttempts)")
                        true
                    } else {
                        false
                    }
                }
                .transformed { message ->
                    message.trimIndent()
                }
        )

        // После добавления строгого промпта - запрашиваем LLM
        edge(nodeAddStrictPrompt forwardTo nodeRequestClarification)

        // После уточнения - проверяем новый ответ
        edge(
            (nodeRequestClarification forwardTo nodeFinish)
                .onAssistantMessage { message ->
                    val isGeneral = isGeneralResponse(message.content)

                    if (!isGeneral) {
                        println("✅ После уточнения получен конкретный ответ")
                        true
                    } else {
                        println("⚠️ После уточнения ответ всё ещё общий")
                        // Если исчерпаны попытки - завершаем
                        clarificationAttempts >= 2
                    }
                }
        )

        // После уточнения LLM хочет вызвать инструмент
        edge(
            (nodeRequestClarification forwardTo nodeExecuteTool)
                .onToolCall {
                    toolIterations++
                    println("🔧 После уточнения LLM вызывает инструмент")
                    toolIterations < 5
                }
        )

        // LLM вызывает инструмент с самого начала
        edge(
            (nodeSendInput forwardTo nodeExecuteTool)
                .onToolCall {
                    println("🔧 LLM вызывает инструмент")
                    toolIterations < 5
                }
        )

        edge(nodeExecuteTool forwardTo nodeSendToolResult)

        // После инструмента - еще инструмент
        edge(
            (nodeSendToolResult forwardTo nodeExecuteTool)
                .onToolCall {
                    toolIterations++
                    println("🔄 Нужен еще инструмент (итерация $toolIterations)")
                    toolIterations < 5
                }
        )

        // После инструмента - LLM дал ответ - проверяем качество
        edge(
            (nodeSendToolResult forwardTo nodeFinish)
                .onAssistantMessage { message ->
                    val isGeneral = isGeneralResponse(message.content)

                    if (!isGeneral) {
                        println("🎯 LLM сформировал конкретный ответ")
                        true
                    } else {
                        println("⚠️ LLM сформировал общий ответ")
                        false
                    }
                }
        )

        // После инструмента - LLM дал общий ответ - запрашиваем уточнение
        edge(
            (nodeSendToolResult forwardTo nodeAddStrictPrompt)
                .onAssistantMessage { message ->
                    val isGeneral = isGeneralResponse(message.content)

                    if (isGeneral && clarificationAttempts < 2) {
                        clarificationAttempts++
                        println("🔄 Ответ после инструментов общий, добавляю строгий промпт (попытка $clarificationAttempts)")
                        true
                    } else {
                        false
                    }
                }
                .transformed { message ->
                    message.trimIndent()
                }
        )

        // Превышен лимит итераций инструментов
        edge(
            (nodeSendToolResult forwardTo nodeFinish)
                .onCondition {
                    toolIterations >= 5
                }
                .transformed {
                    "⚠️ Превышено максимальное количество шагов (5). Запрос слишком сложный."
                }
        )
    }
}
