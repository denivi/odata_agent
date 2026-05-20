package data.agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.Message
import java.util.concurrent.ConcurrentHashMap

class LoggingInMemoryChatHistoryProvider : ChatHistoryProvider {

    private val storage = ConcurrentHashMap<String, List<Message>>()

    override suspend fun load(conversationId: String): List<Message> {
        val messages = storage[conversationId].orEmpty()
        println("🧠 ChatMemory.load: conversationId=$conversationId messages=${messages.size}")
        return messages
    }

    override suspend fun store(conversationId: String, messages: List<Message>) {
        println("🧠 ChatMemory.store: conversationId=$conversationId messages=${messages.size}")
        storage[conversationId] = messages
    }

    fun clear(conversationId: String) {
        storage.remove(conversationId)
        println("🧠 ChatMemory.clear: conversationId=$conversationId")
    }


}