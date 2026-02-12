package org.example.domain.strategies

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall

private fun createGuardedStrategy(): AIAgentGraphStrategy<String, String> {
    return strategy("GuardedDataQueryStrategy") {
        val nodeSendInput by nodeLLMRequest()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult()

        // Счетчик итераций для защиты от бесконечных циклов
        var iterationCount = 0

        edge(nodeStart forwardTo nodeSendInput)

        // LLM отвечает сразу - завершаем
        edge(
            (nodeSendInput forwardTo nodeFinish)
                .onAssistantMessage {
                    println("✅ LLM ответил сразу без вызова инструментов")
                    true }
        )

        // LLM вызывает инструмент (максимум 5 раз)
        edge(
            (nodeSendInput forwardTo nodeExecuteTool)
                .onToolCall {
                    println("🔧 LLM вызывает инструменты")
                    iterationCount < 5 // Ограничение на 5 итераций
                }
        )

        edge(nodeExecuteTool forwardTo nodeSendToolResult)

        // После результата инструмента - снова проверяем
        edge(
            (nodeSendToolResult forwardTo nodeExecuteTool)
                .onToolCall {
                    iterationCount++ < 5 // Увеличиваем счетчик
                }
        )

        edge(
            (nodeSendToolResult forwardTo nodeFinish)
                .onAssistantMessage {
                    println("🎯 LLM сформировал финальный ответ")
                    true }
        )

        // Если превышен лимит итераций - завершаем с ошибкой
        edge(
            (nodeSendToolResult forwardTo nodeFinish)
                .onCondition {
                    iterationCount >= 5
                }
                .transformed() {
                    "⚠️ Превышено максимальное количество шагов (5). Запрос слишком сложный."
                }
        )
    }
}