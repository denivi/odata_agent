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

private const val START_HISTORY_MSG_LIMIT = 40
private const val AFTER_TOOL_HISTORY_MSG_LIMIT = 50
private const val KEEP_LAST_N_MESSAGES = 24

private suspend fun AIAgentContext.historyIsLongAtStart(): Boolean =
    llm.readSession {
        prompt.messages.size > START_HISTORY_MSG_LIMIT
    }

private suspend fun AIAgentContext.historyIsLongAfterTool(): Boolean =
    llm.readSession {
        prompt.messages.size > AFTER_TOOL_HISTORY_MSG_LIMIT
    }

fun basicSimpleStrategy(): AIAgentGraphStrategy<String, String> =
    strategy(name = "Basic-Simple-Strategy") {

        val callLLM by nodeLLMRequest()
        val executeTool by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()

        val compressBeforeCall by nodeLLMCompressHistory<String>(
            strategy = HistoryCompressionStrategy.FromLastNMessages(KEEP_LAST_N_MESSAGES),
            preserveMemory = true
        )

        val compressAfterTool by nodeLLMCompressHistory<ReceivedToolResult>(
            strategy = HistoryCompressionStrategy.FromLastNMessages(KEEP_LAST_N_MESSAGES),
            preserveMemory = true
        )

        // START -> optional compression -> LLM
        edge(nodeStart forwardTo compressBeforeCall onCondition {
            historyIsLongAtStart()
        })
        edge(compressBeforeCall forwardTo callLLM)

        edge(nodeStart forwardTo callLLM onCondition {
            !historyIsLongAtStart()
        })

        // ВАЖНО: tool-call должен иметь приоритет над assistant message.
        edge(callLLM forwardTo executeTool onToolCall {
            true
        })
        edge(callLLM forwardTo nodeFinish onAssistantMessage {
            true
        })

        // После инструмента compress только при реальной необходимости.
        edge(executeTool forwardTo compressAfterTool onCondition {
            historyIsLongAfterTool()
        })
        edge(compressAfterTool forwardTo sendToolResult)

        edge(executeTool forwardTo sendToolResult onCondition {
            !historyIsLongAfterTool()
        })

        // ВАЖНО: после tool result сначала проверяем следующий tool-call.
        edge(sendToolResult forwardTo executeTool onToolCall {
            true
        })

        // Завершаем только если LLM не запросила инструмент, а дала финальный текст.
        edge(sendToolResult forwardTo nodeFinish onAssistantMessage {
            true
        })
    }