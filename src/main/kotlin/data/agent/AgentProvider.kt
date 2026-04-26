package data.agent

import EXPERIMENTAL_PROMPT
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import data.tools.GetReferenceToolSet
import data.tools.MetaDataToolSet
import data.tools.QueryToolSet
import domain.strategies.basicSimpleStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.Config
import PROMPT
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
        params = LLMParams(
            temperature = 0.1,
            numberOfChoices = 1,
            toolChoice = LLMParams.ToolChoice.Required
        )
    ){
        system(EXPERIMENTAL_PROMPT.trimIndent())
    }

    private val agentConfig = AIAgentConfig(
        prompt = prompt,
        model = ollamaModel,
        maxAgentIterations = 40
    )

    private val promptExecutor = simpleOllamaAIExecutor(Config.BASE_URL_LLM)
    private val persistenceStorage = InMemoryPersistenceStorageProvider()

    private val agentService = AIAgentService(
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        strategy = basicSimpleStrategy(),
        toolRegistry = toolRegistry
    ) {
        install(Persistence) {
            storage = persistenceStorage
            enableAutomaticPersistence = true
            rollbackStrategy = RollbackStrategy.MessageHistoryOnly
        }
    }

private val locks = ConcurrentHashMap<String, Mutex>()

suspend fun ask(sessionId: String, message: String): ChatResponse = withContext(Dispatchers.IO) {
    val mutex = locks.computeIfAbsent(sessionId) { Mutex() }

    mutex.withLock {
        println("📥 [$sessionId] USER: '$message'")

        val result: String = agentService.createAgentAndRun(
            id = sessionId,    // важно: один и тот же id = одна и та же “сессия” в persistence
            agentInput = message
        )

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

