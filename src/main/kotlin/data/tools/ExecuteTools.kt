package data.tools

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

suspend fun executeGetTool(url: String, toolName: String): String {

    return try {

        HttpClient(CIO).use { client ->
            val response: HttpResponse = client.get(url) {
                header(HttpHeaders.Accept, "application/json")
                timeout { requestTimeoutMillis = 15000 }
            }

            val responseBody = response.bodyAsText()
            println("🔧 [TOOL] $toolName - Status: ${response.status}")

            responseBody
        }
    } catch (e: Exception) {
        throw Exception("HTTP ошибка при вызове $toolName: ${e.message}")
    }

}

// Специализированный метод для POST-запросов
suspend fun executePostTool(url: String, requestBody: String, toolName: String): String {
    return try {
        HttpClient(CIO).use { client ->
            val response: HttpResponse = client.post(url) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Accept, "application/json")
                setBody(requestBody)
                timeout {
                    requestTimeoutMillis = 20000
                    connectTimeoutMillis = 10000
                }
            }

            val responseBody = response.bodyAsText()
            println("🔧 [TOOL] $toolName - Status: ${response.status}")
            println("📤 [REQUEST] $requestBody")

            responseBody
        }
    } catch (e: Exception) {
        throw Exception("HTTP ошибка при вызове $toolName: ${e.message}")
    }
}