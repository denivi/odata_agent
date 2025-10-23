package org.example

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.event.Level
import org.example.Config.HTTP_PORT
import org.example.data.agent.AgentProvider
import java.util.UUID

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {

    // Создаем сервер
    val server = embeddedServer(
        factory = Netty,  // HTTP-движок (сервер) — Netty
        port = HTTP_PORT, // порт, на котором слушаем
        host = "0.0.0.0"  // адрес, к которому привязываемся
        ) {
        install(CallLogging)
        install(ContentNegotiation) { json() }
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
        }

        val agentProvider = AgentProvider()

        routing {
            get("/health") {
                call.respondText("OK", ContentType.Text.Plain)
            }

            post("/chat") {
                val request = call.receive<String>()
                val answer = agentProvider.ask(request)
                call.respond(
                    status = HttpStatusCode.OK,
                    message = answer
                )
            }
        }
    }

    server.start(wait = true)
}