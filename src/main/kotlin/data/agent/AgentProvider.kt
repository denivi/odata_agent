package org.example.data.agent

import ai.koog.agents.core.agent.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.Config
import org.example.PROMPT
import org.example.data.dto.ChatResponse
import org.example.data.tools.DataQueryToolSet

class AgentProvider {

    // Модель LLM, с которой будет работать агент (через Ollama).
    // Включаем нужные возможности: температура, структурированный JSON и tools.
    private val ollamaModel: LLModel by lazy {
        LLModel(
            provider = LLMProvider.Ollama,
            id = Config.MODEL_NAME,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Tools
            ),
            contextLength = 40_960
        )
    }

    // Реестр инструментов, доступных агенту.
    private val toolRegistry = ToolRegistry {
        tools(DataQueryToolSet())
    }

    //  - функциональная стратегия выполнения.
    private val functionalStrategy = functionalStrategy { input: String ->
        // 1) Шлём запрос в LLM и получаем список ответов (может содержать tool-calls).
        var responses = requestLLMMultiple(input)

        // 2) Пока LLM запрашивает инструменты — выполняем их и отправляем результаты обратно.
        while (responses.containsToolCalls()) {
            val pendingCalls = extractToolCalls(responses)       // извлечь вызовы инструментов
            val results = executeMultipleTools(pendingCalls)     // выполнить инструменты
            responses = sendMultipleToolResults(results)         // вернуть результаты в LLM
        }

        // 3) По завершении цикла должен остаться один финальный ответ ассистента.
        responses.single().asAssistantMessage().content

    }

    // Создаём новый агент под каждый запрос:
    //  - передаём системный промпт PROMPT,
    //  - подключаем экзекьютор (Ollama),
    //  - инструменты,
    //  - задаём простую функциональную стратегию выполнения.
    private fun newAgent() =
        AIAgent(
            systemPrompt = PROMPT,
            promptExecutor = simpleOllamaAIExecutor(Config.BASE_URL_LLM),
            temperature = 0.15,
            llmModel = ollamaModel,
            toolRegistry = toolRegistry,
            strategy = functionalStrategy
        )

    // Публичный метод: создать нового агента и получает ответ.
    // Вынесено в IO-диспетчер, чтобы не блокировать основной поток.
    suspend fun ask(message: String): ChatResponse = withContext(Dispatchers.IO) {
        try {
            val result = newAgent().run(agentInput = message)
            ChatResponse(success = true, answer = result)
        } catch (e: Exception) {
            ChatResponse(success = false, error = e.message)
        }
    }
}
