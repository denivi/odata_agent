package org.example.data.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@LLMDescription("Tools for working with the Odata interface. " +
        "They allow you to receive various data from their remote information system.")
class OdataToolSet: ToolSet {

    @Tool
    @LLMDescription("Инструмент для получения полного списка метаданных системы. " +
            "Возвращает JSON с массивом объектов, каждый содержит name (название) и url (ссылка). " +
            "Используй этот инструмент когда нужен полный список всех метаданных. " +
            "Префиксы метаданных: " +
            "'Constant_'  - константы" +
            "'AccumulationRegister_' - регистр накопления" +
            "'InformationRegister_' - регистр сведений")
    suspend fun getFullMetaData():String {

        val url = $$"http://77.95.56.147:65525/DevelopDaily/odata/standard.odata/?$format=json;odata=fullmetadata"
        try {
            val response = executeTool(url, "get_full_meta_data")
            return """{"tool": "getFullMetaData", "status": "success", "data": $response}"""
        }catch (e: Exception) {
            return """{"tool": "getFullMetaData", "status": "error", "error": "${e.message}"}"""
        }

    }

    private suspend fun executeTool(url: String, toolName: String): String {
        HttpClient(CIO).use { client ->
            val response: HttpResponse = client.get(url) {
                header(HttpHeaders.Accept, "application/json")
            }

            val responseBody = response.bodyAsText()
            println("🔧 Использован инструмент $toolName")

            // Возвращаем структурированный ответ
            return """
            {
                "url": "$url",
                "statusCode": ${response.status.value},
                "response": $responseBody
            }
            """.trimIndent()
        }
    }

    @Tool
    @LLMDescription("Инструмент для получения ТОЛЬКО констант системы. " +
            "Фильтрует метаданные и возвращает только объекты-константы. " +
            "Используй когда нужны именно константы. " +
            "Возвращает JSON массив с константами в формате {name: 'НазваниеНаРусском'}")
    suspend fun getConstants(): String {
        val url = "http://77.95.56.147:65525/DevelopDaily/odata/standard.odata/?\$format=json;odata=fullmetadata"
        try {
            val response = executeTool(url, "get_constants")

            // Парсим JSON и фильтруем только константы
            val jsonObject = Json.parseToJsonElement(response).jsonObject
            val valueArray = jsonObject["value"]?.jsonArray ?: emptyList()

            val constants = valueArray
                .map { it.jsonObject }
                .filter { it["name"]?.jsonPrimitive?.content?.startsWith("Constant_") == true }
                .map {
                    val originalName = it["name"]?.jsonPrimitive?.content ?: ""
                    // Убираем префикс "Constant_" и оставляем русское название
                    val russianName = originalName.removePrefix("Constant_")
                    mapOf("name" to russianName)
                }

            return Json.encodeToString(constants)

        } catch (e: Exception) {
            return """{"error": "${e.message}"}"""
        }
    }

}