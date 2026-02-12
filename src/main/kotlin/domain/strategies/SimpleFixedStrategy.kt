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
 * Максимально упрощенная стратегия с одной проверкой
 */
fun simpleFixedStrategy(): AIAgentGraphStrategy<String, String> {
    return strategy("SimpleFixedStrategy") {
        val nodeSendInput by nodeLLMRequest()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult()

        // Один узел для проверки и добавления строгого промпта если нужно
        val nodeCheckAndFix by node<String, String>("checkAndFix") { response ->
            val badPhrases = listOf(
                "используйте следующие рекомендации",
                "как искать:",
                "можете найти",
                "рекомендации"
            )

            val needsFix = badPhrases.any {
                response.contains(it, ignoreCase = true)
            }

            if (needsFix) {
                println("⚠️ Обнаружен общий ответ - добавляю маркер для исправления")
                "NEEDS_FIX:$response"
            } else {
                println("✅ Ответ выглядит конкретным")
                response
            }
        }

        // Узел добавления строгого промпта
        val nodeAddFix by nodeAppendPrompt<String>("addFix") {
            user("""
                ⛔ СТОП! Предыдущий ответ был общим и неконкретным!
                
                Ты УЖЕ получил данные из getAllMetadata().
                НАЙДИ в них нужный объект и ОТВЕТЬ коротко.
                
                Формат: "[Объект] - это [тип]"
                
                НЕ давай инструкций!
            """.trimIndent())
        }

        val nodeFixRequest by nodeLLMRequest("fixRequest")

        var toolIterations = 0
        var fixAttempted = false

        edge(nodeStart forwardTo nodeSendInput)

        // Ответ без инструментов -> проверяем
        edge(
            (nodeSendInput forwardTo nodeCheckAndFix)
                .onAssistantMessage {
                    println("📝 Ответ без инструментов")
                    true
                }
        )

        // Вызов инструмента
        edge(
            (nodeSendInput forwardTo nodeExecuteTool)
                .onToolCall {
                    println("🔧 Вызов инструмента")
                    toolIterations < 5
                }
        )

        edge(nodeExecuteTool forwardTo nodeSendToolResult)

        // Еще инструмент
        edge(
            (nodeSendToolResult forwardTo nodeExecuteTool)
                .onToolCall {
                    toolIterations++
                    toolIterations < 5
                }
        )

        // После инструмента -> проверяем
        edge(
            (nodeSendToolResult forwardTo nodeCheckAndFix)
                .onAssistantMessage {
                    println("📝 Ответ после инструмента")
                    true
                }
        )

        // Если не нужно исправление - завершаем
        edge(
            (nodeCheckAndFix forwardTo nodeFinish)
                .onCondition { !it.startsWith("NEEDS_FIX:") }
        )

        // Если нужно исправление и еще не пробовали - пробуем
        edge(
            (nodeCheckAndFix forwardTo nodeAddFix)
                .onCondition {
                    it.startsWith("NEEDS_FIX:") && !fixAttempted
                }
                .transformed {
                    fixAttempted = true
                    println("🔄 Попытка исправления")
                    it.removePrefix("NEEDS_FIX:")
                }
        )

        edge(nodeAddFix forwardTo nodeFixRequest)

        edge(
            (nodeFixRequest forwardTo nodeFinish)
                .onAssistantMessage {
                    println("✅ Исправленный ответ")
                    true
                }
        )

        // Если уже пробовали исправить - отдаем как есть
        edge(
            (nodeCheckAndFix forwardTo nodeFinish)
                .onCondition {
                    it.startsWith("NEEDS_FIX:") && fixAttempted
                }
                .transformed {
                    println("⚠️ Исправление не помогло, отдаю как есть")
                    it.removePrefix("NEEDS_FIX:")
                }
        )

        // Превышен лимит
        edge(
            (nodeSendToolResult forwardTo nodeFinish)
                .onCondition { toolIterations >= 5 }
                .transformed { "⚠️ Превышен лимит шагов" }
        )
    }
}

