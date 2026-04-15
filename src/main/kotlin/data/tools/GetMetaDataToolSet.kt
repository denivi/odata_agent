package data.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.example.Config

class GetMetaDataToolSet(

    private val baseUrl: String = Config.BASE_URL_TOOL_SET

) : ToolSet {

    @Tool
    @LLMDescription("""
        Получает все типы метаданных учетной системы
        Типы следует использовать для того чтобы получить список классов метаданных определенного типа
        Например:
        Инструмент возвращает следующий список:
         - Документы
         - Справочники
         - РегистрыСведений
         - РегистрыНакопления
         Для того чтобы получить список классов выбранного типа нужно вызвать инструмент GetMetadataByType 
         и передать выбранный тип параметром GetMetadataByType(Справочники) 
    """)
    suspend fun getTypesMetaData(): String{

        val url = "$baseUrl/get-types-metadata"
        return try {
            val response = executeGetTool(url, "getTypesMetaData")
            val json = Json.parseToJsonElement(response)

            val formatedResponse = MetaDataFormatter.formatGetTypesMetaDataForLLM(json)
            // println("📤 форматированный ответ инструмента  get-types-metadata $formatedResponse")
            formatedResponse
        }catch (e: Exception){
            println("❌ Ошибка в get-types-metadata: ${e.message}")

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
Получает каталог объектов метаданных указанного типа (например: Справочники, Документы, РегистрыСведений и т.д.).

ВАЖНО:
- НЕЛЬЗЯ вызывать без предварительного getTypesMetaData().
- Этот инструмент возвращает список классов метаданных и их идентификаторы.
- В ответе:
  - title — человекочитаемое название (для поиска по смыслу).
  - name — системное имя класса (параметр для getClassMetadata).
  - id — ПОЛНОЕ квалифицированное имя объекта (например: "Справочник.ОбъектыОбслуживания") — ИМЕННО ЕГО нужно использовать в секции ИЗ запроса.

КАК ИСПОЛЬЗОВАТЬ (ПОИСК ОБЪЕКТА):
1) Вызови getMetadataByType(type).
2) Найди нужный объект:
   - сначала по title (по смыслу вопроса пользователя),
   - затем по name (если title не очевиден).
3) Если объект не найден — вызови getMetadataByType со СЛЕДУЮЩИМ типом из списка getTypesMetaData() и повтори поиск.

ДЛЯ ВОПРОСОВ О МЕТАДАННЫХ (“что такое X?”, “есть ли X?”):
- Найди объект и ответь кратко:
  - Тип: <type>
  - SysName: <name>
  - ID: <id>
  - 1 фраза о назначении (если можно понять из title/контекста).

ДЛЯ ЗАПРОСОВ ДАННЫХ (“покажи/найди/сколько/какие”):
- Найди объект и ЗАПОМНИ:
  - id → для использования в запросе в части ИЗ
  - name → для getClassMetadata и для псевдонима таблицы (КАК ...)
- Затем вызови getClassMetadata(metaDataType, metaDataClass=name), чтобы подтвердить поля и типы.

ЗАПРЕЩЕНО:
- Просить пользователя “самому искать” в списке.
- Придумывать квалифицированное имя таблицы (ИЗ ...) без использования id из ответа инструмента.
"""
    )
    suspend fun getMetadataByType(type: String): String {

        val url = "$baseUrl/get-all-metadata"
        return try {
            val requestBody = """
        {
            "request": {
                "type": "$type"
            }
        }
        """.trimIndent()

            val response = executePostTool(url, requestBody, "getMetadataByType")

            // Парсим JSON
            val json = Json.parseToJsonElement(response)

            // Формируем удобный для LLM текст
            val formatedResponse = MetaDataFormatter.formatAllMetaDataForLLM(json)
            // println("📤 форматированный ответ инструмента  get-metadata-by-type $formatedResponse")
            formatedResponse

        } catch (e: Exception) {
            println("❌ Ошибка в get-metadata-by-type: ${e.message}")
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


