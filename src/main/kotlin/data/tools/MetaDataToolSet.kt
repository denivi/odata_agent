package data.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.example.Config

class MetaDataToolSet(
    private val baseUrl: String = Config.BASE_URL_TOOL_SET
): ToolSet {

    @Tool
    @LLMDescription(
        "Получает описание метаданных системы по поисковому шаблону." +
                "Поисковый шаблон может содержать часть наименования объекта метаданных, инструмент вернет описание всех" +
                "объектов метаданных, имена которых содержат поисковый шаблон" +
                "Пример: searchTemplate = 'объек'  вернется несколько объектов метаданных, " +
                "например справочник Объекты обслуживания" +
                "Параметр инструмента: searchTemplate - часть имени, или имя целиком искомых объектов метаданных"
    )
    suspend fun getSimilarMetaData(searchTemplate: String): String {

        val url = "$baseUrl/get-similar-metadata"
        return try {
            val requestBody = """
        {
            "request": {
                "search_template": "$searchTemplate"
            }
        }
        """.trimIndent()

            val response = executePostTool(url, requestBody, "getSimilarMetaData")
            val json = Json.parseToJsonElement(response)
            val formatedResponse = MetaDataFormatter.formatGetSimilarMetaDataForLLM(json)
            //println("📤 форматированный ответ инструмента  get-similar-metadata $formatedResponse")
            formatedResponse

        } catch (e: Exception) {
            println("❌ Ошибка в get-similar-metadata: ${e.message}")
            e.printStackTrace()

            // Простая структурированная ошибка
            buildString {
                appendLine("ОШИБКА при получении метаданных:")
                appendLine("• Сообщение: ${e.message}")
                appendLine("• Тип: ${e.javaClass.simpleName}")
                appendLine()
                appendLine("Рекомендации:")
                appendLine("1. Проверьте доступность сервера")
                appendLine("2. Убедитесь что API возвращает корректный JSON")
                appendLine("3. Проверьте права доступа")
            }
        }
    }

    @Tool
    @LLMDescription(
        """
Возвращает детальную структуру КОНКРЕТНОГО объекта метаданных: поля, типы, значения перечислений и табличные части.

ПАРАМЕТРЫ:
- metaDataType: тип метаданных (например: "Справочники", "Документы", "РегистрыСведений", "РегистрыНакопления" и т.п.)
- metaDataClass: системное имя класса (берётся из getMetadataByType().name), например: "ОбъектыОбслуживания"

ВАЖНО (ДЛЯ LLM):
- Этот инструмент — источник истины по доступным полям и их типам.
- Перед executeQuery НУЖНО подтвердить все используемые в запросе поля через getClassMetadata
  (если поля ещё не подтверждены в рамках текущей сессии).
- Если executeQuery вернул “Поле не найдено …” — ты обязан вернуться сюда и исправить имя/путь поля.
- Для перечислений: используй только значения из блока enums (если он есть).
- Для булево: в условиях используй только Истина/Ложь.

КАК ИСПОЛЬЗОВАТЬ (ПРАВИЛЬНЫЙ СБОР ЗАПРОСА):
1) Определи объект через getMetadataByType и возьми:
   - id (qualified name) → для ИЗ в запросе (например: Справочник.ОбъектыОбслуживания)
   - name → для metaDataClass и псевдонима (КАК ОбъектыОбслуживания)
2) Вызови getClassMetadata(metaDataType, metaDataClass=name).
3) Сопоставь требуемые пользователем поля с property.name и их типами.
4) Сформируй запрос:
   - ИЗ <id> КАК <alias>
   - Поля выбирай как <alias>.<property.name>
5) Выполни executeQuery.

ОСОБЕННОСТИ СТРУКТУРЫ ОТВЕТА (ДЛЯ ПОСТРОЕНИЯ ЗАПРОСОВ):
- properties → “реквизиты” объекта (alias.<property.name>)
- tables → табличные части (используются как вложенные таблицы/источники, только если это требуется задачей)
- types_description.types → тип данных поля (ссылка/строка/число/булево/перечисление)
- types_description.enums → допустимые значения перечисления (используй их дословно)

ЗАПРЕЩЕНО:
- Придумывать поля, которых нет в properties/tables.
- Использовать значения перечислений, которых нет в enums.
- Делать вывод “данных нет”, если executeQuery вернул is_error=true (это ошибка запроса, а не пустая выборка).
"""
    )
    suspend fun getClassMetadata(metaDataType: String, metaDataClass: String): String {

        val url = "$baseUrl/get-class-metadata"

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

            val response = executePostTool(url, requestBody, "getClassMetadata")
            val json = Json.parseToJsonElement(response)
            val isRef: Boolean = json.jsonObject["response"]?.jsonObject["is_ref"].toString().toBoolean()
            val formatClassMetadata: String by lazy {
                if (isRef) {
                    MetaDataFormatter.formatRefClassMetaDataForLLM(json)
                } else {
                    MetaDataFormatter.formatNotRefClassMetaDataForLLM(json)
                }
            }

            //println("📤 форматированный ответ инструмента  get_class_metadata \n $formatClassMetadata")
            formatClassMetadata

        } catch (e: Exception) {
            """{
            "error_type": "class_metadata_retrieval_failed",
            "message": "Не удалось получить описание объекта метаданных",
            "requested_type": "$metaDataType",
            "requested_class": "$metaDataClass", 
            "details": "${e.message}",
            "suggestions": [
                "Проверьте правильность типа и класса через getAllMetadata",
                "Используйте searchMetadata для поиска похожих объектов",
                "Убедитесь, что тип и класс написаны без опечаток"
            ]
        }"""
        }
    }

}
