package data.agent.guard

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

object AgentExecutionContext {

    private val currentSessionId = ThreadLocal<String?>()

    fun sessionIdOrNull(): String? =
        currentSessionId.get()

    suspend fun <T> withSession(
        sessionId: String,
        block: suspend () -> T
    ): T {
        return withContext(currentSessionId.asContextElement(sessionId)) {
            block()
        }
    }
}