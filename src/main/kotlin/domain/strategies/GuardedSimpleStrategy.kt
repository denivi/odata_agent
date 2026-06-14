package domain.strategies

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import data.agent.guard.AgentExecutionContext
import data.agent.guard.AgentRunStateRegistry

private const val CONTINUE_MARKER = "__TOIR_CONTINUE_REQUIRED__"
private const val HARD_STOP_PREFIX = "__TOIR_HARD_STOP__:"

fun guardedSimpleStrategy(): AIAgentGraphStrategy<String, String> =
    strategy(name = "Guarded-Simple-Strategy") {

        val callLLM by nodeLLMRequest()
        val executeTool by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()

        val validateFinalAnswer by node<String, String>("validateFinalAnswer") { assistantText ->
            val sessionId = AgentExecutionContext.sessionIdOrNull()

            if (sessionId == null) {
                return@node assistantText
            }

            if (AgentRunStateRegistry.canFinish(sessionId)) {
                return@node assistantText
            }

            val canContinue = AgentRunStateRegistry.registerDeniedFinalAttempt(sessionId)

            if (!canContinue) {
                return@node HARD_STOP_PREFIX + AgentRunStateRegistry.buildHardStopMessage(sessionId)
            }

            llm.writeSession {
                updatePrompt {
                    user(AgentRunStateRegistry.buildContinueInstruction(sessionId))
                }
            }

            CONTINUE_MARKER
        }

        val hardStop by node<String, String>("hardStop") { marker ->
            marker.removePrefix(HARD_STOP_PREFIX)
        }

        edge(nodeStart forwardTo callLLM)

        // Tool-call всегда приоритетнее текста.
        edge(callLLM forwardTo executeTool onToolCall { true })
        edge(callLLM forwardTo validateFinalAnswer onAssistantMessage { true })

        edge(executeTool forwardTo sendToolResult)

        // После результата инструмента снова сначала tool-call.
        edge(sendToolResult forwardTo executeTool onToolCall { true })
        edge(sendToolResult forwardTo validateFinalAnswer onAssistantMessage { true })

        // Gate разрешил финальный ответ.
        edge(validateFinalAnswer forwardTo nodeFinish onCondition { output ->
            output != CONTINUE_MARKER && !output.startsWith(HARD_STOP_PREFIX)
        })

        // Gate запретил финальный ответ и добавил служебную инструкцию в prompt.
        edge(validateFinalAnswer forwardTo callLLM onCondition { output ->
            output == CONTINUE_MARKER
        })

        // Превышено количество попыток.
        edge(validateFinalAnswer forwardTo hardStop onCondition { output ->
            output.startsWith(HARD_STOP_PREFIX)
        })

        edge(hardStop forwardTo nodeFinish)
    }