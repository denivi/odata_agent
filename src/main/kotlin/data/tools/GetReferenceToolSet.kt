package data.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.example.Config

class GetReferenceToolSet(
    private val baseUrl: String = Config.BASE_URL_TOOL_SET
) : ToolSet {
    @Tool
    @LLMDescription("""""
    Инструмент получает описание справку по заданному объекту системы
        
    Параметры:
    - metaDataType: тип объекта (Справочники, Документы, РегистрыСведений, РегистрыНакопления, ПланыСчетов, etc)
    - metaDataClass: системное имя класса (например: "ОбъектыОбслуживания", "ЗаявкиНаРемонт")
    
    ВОЗВРАЩАЕМАЯ ИНФОРМАЦИЯ:
    - описание функционала (функциональная роль) объекта в учетной системе
    - общее справочное описание полей объекта
    - общее описание связанных с объектом сущностей (другие объекты системы, специальные алгоритмы, и прочее)
    
    ПРИМЕРЫ ИСПОЛЬЗОВАНИЯ:
    - getReference("Справочники", "ОбъектыОбслуживания") - справочная информация по запрошенному объекту системы
    
     ПРЕДУПРЕЖДЕНИЕ:
    - Тип и класс должны точно соответствовать значениям из getAllMetadata
    - Используй searchMetadata если не уверен в точном названии
    """")
    suspend fun getReference(metaDataType: String, metaDataClass: String): String {

        val url = "$baseUrl/get-reference"

        return try {
            // Формируем JSON тело запроса
            val requestBody = """
        {
            "request": {
                "type": "$metaDataType",
                "class": "$metaDataClass"
            }
        }
        """.trimIndent()

            val response = executePostTool(url, requestBody, "getReference")
            val json = Json.parseToJsonElement(response)
            val textResponse = json.jsonObject["response"].toString()
            println("📤 форматированный ответ инструмента  get_reference \n $textResponse")
            textResponse

        } catch (e: Exception) {
            """{
            "error_type": "get_reference_retrieval_failed",
            "message": "Не удалось получить справку по объекту ТОиР",
            "details": "${e.message}",
            "suggestion": "Проверьте доступность сервера и повторите запрос"
        }"""
        }
    }


}