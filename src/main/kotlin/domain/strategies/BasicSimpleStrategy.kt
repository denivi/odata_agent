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

fun basicSimpleStrategy(): AIAgentGraphStrategy<String, String> =
    strategy(name = "Basic-Simple-Strategy") {

        val callLLM by nodeLLMRequest()
        val executeTool by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()

        // Компрессия перед новым запросом к LLM, если сессия уже длинная
        val compressBeforeCall by nodeLLMCompressHistory<String>(
            strategy = HistoryCompressionStrategy.WholeHistory,
            preserveMemory = true
        )

        // Компрессия ПОСЛЕ КАЖДОГО tool-call — это ключевой момент
        val compressAfterTool by nodeLLMCompressHistory<ReceivedToolResult>(
            strategy = HistoryCompressionStrategy.WholeHistory,
            preserveMemory = true
        )

        // START -> (compress?) -> callLLM
        edge(nodeStart forwardTo compressBeforeCall onCondition { historyIsLongAtStart() })
        edge(compressBeforeCall forwardTo callLLM)
        edge(nodeStart forwardTo callLLM onCondition { !historyIsLongAtStart() })

        // LLM -> finish | tool
        edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
        edge(callLLM forwardTo executeTool onToolCall { true })

        // TOOL -> compress -> sendToolResult
        edge(executeTool forwardTo compressAfterTool)
        edge(compressAfterTool forwardTo sendToolResult)

        // sendToolResult -> finish | next tool
        edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
        edge(sendToolResult forwardTo executeTool onToolCall { true })
    }