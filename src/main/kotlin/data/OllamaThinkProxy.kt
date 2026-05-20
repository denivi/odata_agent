package data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.copyTo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive

private val proxyJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

private const val PROXY_PORT = 11435
private const val OLLAMA_BASE_URL = "http://77.95.56.147:65526"

fun main() {
    embeddedServer(
        factory = Netty,
        host = "127.0.0.1",
        port = PROXY_PORT,
        module = Application::ollamaThinkProxyModule
    ).start(wait = true)
}

fun Application.ollamaThinkProxyModule() {
    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 600_000
            socketTimeoutMillis = 600_000
        }
    }

    routing {
        post("/api/chat") {
            call.forwardWithThinkFalse(
                client = client,
                targetUrl = "$OLLAMA_BASE_URL/api/chat"
            )
        }

        post("/api/generate") {
            call.forwardWithThinkFalse(
                client = client,
                targetUrl = "$OLLAMA_BASE_URL/api/generate"
            )
        }
    }
}

private suspend fun ApplicationCall.forwardWithThinkFalse(
    client: HttpClient,
    targetUrl: String
) {
    val rawBody = receiveText()
    val patchedBody = forceThinkFalse(rawBody)

    logPatchedRequest(targetUrl, rawBody, patchedBody)

    val startedAt = System.currentTimeMillis()

    val upstream = client.post(targetUrl) {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Accept, "application/json")
        setBody(patchedBody)
    }

    val responseText = upstream.bodyAsText()
    val elapsedMs = System.currentTimeMillis() - startedAt

    logOllamaResponse(responseText, elapsedMs)

    val contentType = upstream.headers[HttpHeaders.ContentType]
        ?: ContentType.Application.Json.toString()

    respondText(
        text = responseText,
        contentType = ContentType.parse(contentType),
        status = upstream.status
    )
}

private fun forceThinkFalse(rawBody: String): String {
    val root = proxyJson.parseToJsonElement(rawBody).jsonObject

    val patched = buildJsonObject {
        root.forEach { (key, value) ->
            if (key != "think") {
                put(key, value)
            }
        }

        // Ключевая правка
        put("think", false)
    }

    return proxyJson.encodeToString(JsonObject.serializer(), patched)
}

private fun logPatchedRequest(targetUrl: String, rawBody: String, patchedBody: String) {
    val raw = proxyJson.parseToJsonElement(rawBody).jsonObject
    val patched = proxyJson.parseToJsonElement(patchedBody).jsonObject

    val model = patched["model"]?.jsonPrimitive?.contentOrNull
    val rawThink = raw["think"]?.jsonPrimitive?.booleanOrNull
    val patchedThink = patched["think"]?.jsonPrimitive?.booleanOrNull
    val stream = patched["stream"]?.jsonPrimitive?.booleanOrNull
    val messagesCount = patched["messages"]?.jsonArray?.size ?: 0
    val toolsCount = patched["tools"]?.jsonArray?.size ?: 0

    val lastMessage = patched["messages"]
        ?.jsonArray
        ?.lastOrNull()
        ?.jsonObject

    val lastRole = lastMessage?.get("role")?.jsonPrimitive?.contentOrNull
    val lastContent = lastMessage?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()

    println(
        """
        ➡️ Ollama proxy request
        target=$targetUrl
        model=$model
        rawThink=$rawThink
        patchedThink=$patchedThink
        stream=$stream
        messages=$messagesCount
        tools=$toolsCount
        rawChars=${rawBody.length}
        patchedChars=${patchedBody.length}
        lastRole=$lastRole
        lastContentPreview=${lastContent.take(300).replace("\n", " ")}
        """.trimIndent()
    )
}

private fun logOllamaResponse(responseText: String, elapsedMs: Long) {
    val root = runCatching {
        proxyJson.parseToJsonElement(responseText).jsonObject
    }.getOrNull()

    if (root == null) {
        println(
            """
            ⬅️ Ollama proxy response
            elapsedMs=$elapsedMs
            nonJsonChars=${responseText.length}
            preview=${responseText.take(500)}
            """.trimIndent()
        )
        return
    }

    val message = root["message"]?.jsonObject
    val content = message?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
    val thinking = message?.get("thinking")?.jsonPrimitive?.contentOrNull.orEmpty()
    val toolCalls = message?.get("tool_calls")?.jsonArray?.size ?: 0

    val promptEvalCount = root["prompt_eval_count"]?.jsonPrimitive?.contentOrNull
    val evalCount = root["eval_count"]?.jsonPrimitive?.contentOrNull
    val totalDuration = root["total_duration"]?.jsonPrimitive?.contentOrNull
    val promptEvalDuration = root["prompt_eval_duration"]?.jsonPrimitive?.contentOrNull
    val evalDuration = root["eval_duration"]?.jsonPrimitive?.contentOrNull

    println(
        """
        ⬅️ Ollama proxy response
        elapsedMs=$elapsedMs
        contentChars=${content.length}
        thinkingChars=${thinking.length}
        toolCalls=$toolCalls
        promptEvalCount=$promptEvalCount
        evalCount=$evalCount
        totalDurationNs=$totalDuration
        promptEvalDurationNs=$promptEvalDuration
        evalDurationNs=$evalDuration
        contentPreview=${content.take(300).replace("\n", " ")}
        thinkingPreview=${thinking.take(300).replace("\n", " ")}
        """.trimIndent()
    )
}