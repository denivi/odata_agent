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
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import data.tools.GetReferenceToolSet
import data.tools.MetaDataToolSet
import data.tools.QueryToolSet
import domain.strategies.basicSimpleStrategy
import domain.strategies.guardedSimpleStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.Config
import org.example.data.dto.ChatResponse
import java.util.concurrent.ConcurrentHashMap

class OpenAiAgentProvider {

    val LLMProvider.Companion.LMStudio by lazy {
        object : LLMProvider("lmstudio", "lmstudio") {}
    }

    private val lmStudioModel: LLModel by lazy {
        LLModel(
            provider = LLMProvider.LMStudio,
            id = Config.OPEN_API_MODEL_NAME,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Schema.JSON.Basic,
                LLMCapability.Completion,
                LLMCapability.Tools,
                LLMCapability.ToolChoice
            ),
            contextLength = 40_960,
            maxOutputTokens = 8_192
        )
    }

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
    ) {
        system(EXPERIMENTAL_PROMPT.trimIndent())
    }

    private val agentConfig = AIAgentConfig(
        prompt = prompt,
        model = lmStudioModel,
        maxAgentIterations = 40
    )

    private val openAiClientSettings = OpenAIClientSettings(
        baseUrl = Config.BASE_OPEN_API_URL_LLM, // например: "http://127.0.0.1:1234/v1"
        timeoutConfig = ConnectionTimeoutConfig(
            connectTimeoutMillis = 10_000,
            requestTimeoutMillis = 300_000,
            socketTimeoutMillis = 300_000
        )
        // остальные пути оставляем дефолтными
        // если в вашей версии конструктора нужны все параметры:
        // chatCompletionsPath = "v1/chat/completions",
        // responsesAPIPath   = "v1/responses",
        // embeddingsPath     = "v1/embeddings",
        // moderationsPath    = "v1/moderations",
        // modelsPath         = "v1/models"
    )

    private val openAiClient = OpenAILLMClient(
        apiKey = "lm-studio",
        settings = openAiClientSettings
    )

    private val promptExecutor = MultiLLMPromptExecutor(openAiClient)

    private val persistenceStorage = InMemoryPersistenceStorageProvider()

    private val agentService = AIAgentService(
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        strategy = guardedSimpleStrategy(),
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
                id = sessionId,
                agentInput = message
            )

            println("📤 [$sessionId] ASSISTANT: '$result'")
            ChatResponse(success = true, answer = result)
        }
    }

    fun reset(sessionId: String) {
        locks.remove(sessionId)
    }
}