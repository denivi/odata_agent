package org.example.data.html_client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class HttpClients {

    // Базовый Http клиент
    val default: HttpClient by lazy { createDefault() }

    fun createDefault(): HttpClient {
        val json = Json {
            ignoreUnknownKeys = true   // лишние поля из ответа не ломают парсинг
            prettyPrint = true
            isLenient = true           // терпим лишние запятые/формат
        }

        return HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis  = 30_000
            }

            // Логи HTTP-клиента (метод, URL, статусы).
            install(Logging) {
                level = LogLevel.INFO
            }

            // Значения по умолчанию для всех запросов
            defaultRequest {
                contentType(ContentType.Application.Json)
            }

            // Не бросать исключение на 4xx/5xx — сами решаем по resp.status
            expectSuccess = false
        }
    }
}
