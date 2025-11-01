package org.example.data.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

@LLMDescription("Tools for working with the Odata interface. " +
        "They allow you to receive various data from their remote information system.")
class OdataToolSet: ToolSet {

    @Tool
    @LLMDescription("The tool gets a description of all metadata objects of the remote system." +
            "The response is received in json format, where the name attribute is the name of the metadata object, " +
            "and the url attribute is a link to the metadata object." +
            "list object metadata:" +
            "Constant - Константы")
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

}