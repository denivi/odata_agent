package org.example.data.agent

import ai.koog.agents.core.agent.AIAgent
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

class AgentSessionManager(
    private val agentFactory: () -> AIAgent<String, String>
) {
    data class Entry(
        val agent: AIAgent<String, String>,
        val mutex: Mutex = Mutex(),
        @Volatile var lastAccessMillis: Long = System.currentTimeMillis()
    )

    private val sessions = ConcurrentHashMap<String, Entry>()

    fun get(sessionId: String): Entry {
        val entry = sessions.computeIfAbsent(sessionId) { Entry(agentFactory()) }
        entry.lastAccessMillis = System.currentTimeMillis()
        return entry
    }

    fun clear(sessionId: String) {
        sessions.remove(sessionId)
    }

    fun cleanup(olderThanMillis: Long) {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { now - it.value.lastAccessMillis > olderThanMillis }
    }
}
