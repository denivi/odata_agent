package domain.strategies

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult

private const val START_HISTORY_MSG_LIMIT = 12

private suspend fun AIAgentContext.historyIsLongAtStart(): Boolean =
    llm.readSession { prompt.messages.size > START_HISTORY_MSG_LIMIT }

fun shortBasicSimpleStrategy(): AIAgentGraphStrategy<String, String> =
    strategy(name = "Basic-Simple-Strategy") {

        val callLLM by nodeLLMRequest()
        val executeTool by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()

        edge(nodeStart forwardTo callLLM)

        edge(callLLM forwardTo executeTool onToolCall { true })
        edge(callLLM forwardTo nodeFinish onAssistantMessage { true })

        edge(executeTool forwardTo sendToolResult)

        edge(sendToolResult forwardTo executeTool onToolCall { true })
        edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
    }