package data.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.example.data.dto.AllMetaDataResponse
import org.example.data.dto.NotRefClassMetaDataResponse
import org.example.data.dto.PropertyClass
import org.example.data.dto.RefClassMetaDataResponse
import org.example.data.dto.TypesMetaDataResponse
import kotlin.text.ifEmpty

object MetaDataFormatter {
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun formatGetTypesMetaDataForLLM(jsonElement: JsonElement): String{
        return try {
            val apiData = jsonConfig.decodeFromJsonElement<TypesMetaDataResponse>(jsonElement)
            val types = apiData.response.types
            if (types.isEmpty()) return "⚠️ Каталог пуст"
            buildString {
                appendLine("# КАТАЛОГ ДОСТУПНЫХ ТИПОВ МЕТАДАННЫХ СИСТЕМЫ")

                types.forEach { type ->
                    if (type.isEmpty()) return@forEach

                    // Используем Markdown-заголовки (эффективнее для LLM)
                    appendLine(" - $type")
                }
            }
        }catch (e: Exception){
            "❌ Ошибка данных: ${e.localizedMessage}"
        }
    }

    fun formatAllMetaDataForLLM(jsonElement: JsonElement): String {
        return try {
            // 1. Десериализация в типизированные объекты
            val apiData = jsonConfig.decodeFromJsonElement<AllMetaDataResponse>(jsonElement)
            val type = apiData.response.type
            val classes = apiData.response.classes

            if (classes.isEmpty()) return "⚠️ Каталог пуст"

            buildString {

                appendLine("# СИСТЕМНЫЙ КАТАЛОГ МЕТАДАННЫХ")
                appendLine("# МЕТАДАННЫЕ: $type")
                appendLine("КРИТИЧЕСКИЕ ПРАВИЛА ДЛЯ ЗАПРОСА 1С:")
                appendLine("1) В секции ИЗ используй ТОЛЬКО QUERY_SOURCE (qualified id), НЕ SysName.")
                appendLine("2) SysName нужен только как имя класса для getClassMetadata и как псевдоним (КАК).")
                appendLine("")
                classes.forEach {item ->
                    // Сжатый формат: Название как ключевой элемент
                    appendLine("- **${item.title}**")
                    appendLine("  ID: ${item.id} | SysName: ${item.name}")
                    appendLine("- **Для использования в запросах:**")
                    appendLine("  QUERY_SOURCE (ИЗ): `${item.id}`")
                    appendLine("  CLASS_NAME (для getClassMetadata): `${item.name}`")
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка данных: ${e.localizedMessage}"
        }
    }

    fun formatRefClassMetaDataForLLM(jsonElement: JsonElement): String {
        return try {
            val apiData = jsonConfig.decodeFromJsonElement<RefClassMetaDataResponse>(jsonElement)

            val objectProperties = apiData.response.properties
            if (objectProperties.isEmpty()) return "⚠️ Каталог пуст"

            buildString {
                appendLine("# ОПИСАНИЕ ПОЛЕЙ МЕТАДАННЫХ: ${apiData.response.name}")
                appendLine("QUERY_SOURCE (ИЗ): `Справочник.${apiData.response.name}`") // если тип — справочник
                appendLine("ALIAS (КАК): `${apiData.response.name}`")
                appendLine("ПРАВИЛО: В запросе используй только ID поля (property.name): `ALIAS.<ID>`.")
                appendLine("")
                appendLine("## РЕКВИЗИТЫ")
                objectProperties.forEachIndexed { index, item ->
                    val property = item.property
                    appendLine(
                        "---\n ${index + 1} ID: ${property.name} | Имя: ${property.title.ifEmpty { property.name }}"
                    )
                    appendLine("**типы данных**")
                    val types = property.typesDescription.types
                    val enums = property.typesDescription.enums
                    types.forEach { item ->
                        appendLine(" - ${item.type}")
                    }
                }
                appendLine("## ТАБЛИЧНЫЕ ЧАСТИ")
                val objectTables = apiData.response.tables
                objectTables.forEach { item ->
                    val table = item.table
                    appendLine("** Табличная часть ${table.name} , синоним ${table.title}")
                    val properties = table.properties
                    properties.forEachIndexed { index, item ->
                        val property = item.property
                        appendLine("---\n ${index + 1}. ID: ${property.name} | Имя: ${property.title}")
                        appendLine("**типы данных**")
                        val types = property.typesDescription.types
                        types.forEach { item ->
                            appendLine(" - ${item.type}")
                        }
                        val enums = property.typesDescription.enums
                        if (enums.isNotEmpty()) {
                            appendLine("**значения перечислений**")
                            enums.forEach { item ->
                                appendLine(" - ${item}")
                            }
                        }else ""
                    }
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка данных: ${e.localizedMessage}"
        }
    }

    fun formatNotRefClassMetaDataForLLM(jsonElement: JsonElement): String {
        return try {
            val apiData = jsonConfig.decodeFromJsonElement<NotRefClassMetaDataResponse>(jsonElement)
            val data = apiData.response
            buildString {
                appendLine("# МЕТАДАННЫЕ РЕГИСТРА: ${data.name.uppercase()}")

                // Вызываем общую логику для каждой категории
                appendSection("ИЗМЕРЕНИЯ", data.dimensions)
                appendSection("РЕСУРСЫ", data.resources)
                appendSection("РЕКВИЗИТЫ", data.attributes)
            }

        } catch (e: Exception) {
            "❌ Ошибка данных: ${e.localizedMessage}"
        }
    }

    private fun StringBuilder.appendSection(title: String, items: List<PropertyClass>) {
        if (items.isEmpty()) return

        appendLine("\n## $title")
        items.forEachIndexed { index, wrapper ->
            val prop = wrapper.property
            val types = prop.typesDescription.types.joinToString(", ") { it.type }
            val enums = prop.typesDescription.enums.joinToString(", ") {it}
            val name = prop.title.ifBlank { prop.name }

            // Компактный формат: Индекс. Название [ID] (Типы)
            appendLine("${index + 1}. **$name**")
            appendLine("   ID: `${prop.name}` | Типы: [$types] | Перечисления: [$enums]")
        }
    }

}
