import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json
import org.example.Config.HTTP_PORT
import org.example.data.agent.AgentProvider
import org.example.data.dto.ChatRequest
import org.example.data.dto.ChatResponse
import org.example.data.dto.ChatSession
import java.util.*

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

fun main() {
    embeddedServer(
        factory = Netty,
        port = HTTP_PORT,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // Логгер приложения (вместо println)
    val log = environment.log

    install(CallLogging)

    install(ContentNegotiation) {
        json(json)
    }

    // Требует зависимости io.ktor:ktor-server-status-pages(-jvm)
    install(StatusPages) {
        exception<ContentTransformationException> { call, cause ->
            log.warn("Invalid request body: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                ChatResponse(success = false, error = "Invalid JSON body")
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            log.warn("Bad request: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                ChatResponse(success = false, error = cause.message ?: "Bad request")
            )
        }

        exception<Throwable> { call, cause ->
            log.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ChatResponse(success = false, error = "Internal server error")
            )
        }
    }

    install(CORS) {
        // Для production лучше задать allowHost(...) / allowOrigins(...) под ваш фронт.
        // anyHost() — только для dev.
        anyHost()
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Session-Id")
        allowCredentials = true
    }

    install(Sessions) {
        cookie<ChatSession>("chat_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            // cookie.secure = true // включить при HTTPS
            // cookie.extensions["SameSite"] = "Lax"
        }
    }

    val agentProvider = AgentProvider()

    routing {
        get("/health") {
            call.respondText("OK", ContentType.Text.Plain)
        }

        post("/chat") {
            // Нормальный JSON receive (ContentNegotiation)
            val request = call.receive<ChatRequest>()
            require(request.message.isNotBlank()) { "message must not be blank" }

            val sessionId = call.getOrCreateSessionId()
            val response = agentProvider.ask(sessionId = sessionId, message = request.message)

            val status = if (response.success) HttpStatusCode.OK else HttpStatusCode.InternalServerError
            call.respond(status, response)
        }

        post("/chat/reset") {
            val sessionId = call.getSessionIdOrNull()
                ?: throw IllegalArgumentException("Missing X-Session-Id header or session cookie")

            agentProvider.reset(sessionId)
            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun ApplicationCall.getSessionIdOrNull(): String? {
    val headerId = request.headers["X-Session-Id"]?.trim()?.takeIf { it.isNotBlank() }
    val cookieId = this.sessions.get<ChatSession>()?.id?.trim()?.takeIf { it.isNotBlank() }
    return headerId ?: cookieId
}

private fun ApplicationCall.getOrCreateSessionId(): String {
    val headerId = request.headers["X-Session-Id"]?.trim()?.takeIf { it.isNotBlank() }
    val cookieId = this.sessions.get<ChatSession>()?.id?.trim()?.takeIf { it.isNotBlank() }

    val id = headerId ?: cookieId ?: UUID.randomUUID().toString()

    if (cookieId != id) {
        this.sessions.set(ChatSession(id))
    }

    response.headers.append("X-Session-Id", id)
    return id
}
