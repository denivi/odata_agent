@file:OptIn(kotlin.time.ExperimentalTime::class)
package data.agent

import EXPERIMENTAL_PROMPT
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaParams
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import data.tools.GetReferenceToolSet
import data.tools.MetaDataToolSet
import data.tools.QueryToolSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.Config
import PROMPT
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.agents.core.agent.AIAgent
import data.agent.guard.AgentExecutionContext
import data.agent.guard.AgentRunStateRegistry
import domain.strategies.guardedSimpleStrategy
import org.example.data.dto.ChatResponse
import java.util.concurrent.ConcurrentHashMap


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
            contextLength = 32_768,
            maxOutputTokens = 4_096
        )
    }

    // Реестр инструментов, доступных агенту.
    private val toolRegistry = ToolRegistry {
        tools(MetaDataToolSet())
        tools(QueryToolSet())
        tools(GetReferenceToolSet())
    }

    private val prompt = prompt(
        id = "toir-assistant",
        params = OllamaParams(
            temperature = 0.1,
            numberOfChoices = 1,
            toolChoice = LLMParams.ToolChoice.Auto,
            think = false
        )
    ){
        system(EXPERIMENTAL_PROMPT.trimIndent())
    }

    private val agentConfig = AIAgentConfig(
        prompt = prompt,
        model = ollamaModel,
        maxAgentIterations = 40
    )

    private val promptExecutor = MultiLLMPromptExecutor(
        OllamaClient(baseUrl = Config.BASE_URL_LLM)
    )
    private val chatHistoryProvider = LoggingInMemoryChatHistoryProvider()
    private val agent = AIAgent<String, String>(
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        strategy = guardedSimpleStrategy(),
        toolRegistry = toolRegistry
    ) {
        install(ChatMemory) {
            // TODO: заменить in-memory ChatHistoryProvider на production-хранилище:
            // - Redis, если нужна быстрая временная история с TTL
            // - PostgreSQL/SQLite, если нужна долговременная история
            chatHistoryProvider = this@AgentProvider.chatHistoryProvider
            windowSize(20)
        }
    }

private val locks = ConcurrentHashMap<String, Mutex>()

suspend fun ask(sessionId: String, message: String): ChatResponse = withContext(Dispatchers.IO) {
    val mutex = locks.computeIfAbsent(sessionId) { Mutex() }

    mutex.withLock {
        println("📥 [$sessionId] USER: '$message'")

        AgentRunStateRegistry.startUserTurn(
            sessionId = sessionId,
            message = message
        )

        val result: String = AgentExecutionContext.withSession(sessionId) {
            agent.run(
                agentInput = message,
                sessionId = sessionId
            )
        }

        println("📤 [$sessionId] ASSISTANT: '$result'")
        ChatResponse(success = true, answer = result)
    }
}

    fun reset(sessionId: String) {
        // Самый надежный reset без плясок с внутренностями storage:
        // 1) на стороне клиента выдать новый X-Session-Id
        // 2) лок здесь можно удалить
        locks.remove(sessionId)
    }
}

