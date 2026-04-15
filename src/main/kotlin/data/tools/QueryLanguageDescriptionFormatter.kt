package data.tools

import data.dto.QueryLanguageDescriptionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object QueryLanguageDescriptionFormatter {

    private val toolJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private const val MAX_DESC_LEN = 12_000   // чтобы справка не «съела» контекст
    private const val MAX_EXAMPLES_LEN = 16_000

    fun format(rawResponse: String, requestedId: String?): String {
        val dto = try {
            toolJson.decodeFromString<QueryLanguageDescriptionResponse>(rawResponse)
        } catch (e: Exception) {
            return error(
                requestedId = requestedId,
                message = "Невалидный ответ инструмента getQueryLanguageDescription (JSON/контракт)",
                details = e.message ?: "unknown",
                raw = rawResponse.take(4000)
            )
        }

        val r = dto.response

        // мягкое ограничение размера — особенно важно для примеров
        val safeDescription = r.description.take(MAX_DESC_LEN)
        val safeExamples = r.exemples.take(MAX_EXAMPLES_LEN)

        val sectionJson = buildJsonObject {
            put("kind", r.kind)
            put("doc", r.doc)
            put("version", r.version)
            put("id", r.id)
            put("title", r.title)
            put("description", safeDescription)
            put("exemples", safeExamples)
        }

        return payload(
            ok = true,
            requestedId = requestedId,
            section = sectionJson,
            warnings = buildList {
                if (r.description.length > MAX_DESC_LEN) add("description обрезан по длине до $MAX_DESC_LEN символов")
                if (r.exemples.length > MAX_EXAMPLES_LEN) add("exemples обрезан по длине до $MAX_EXAMPLES_LEN символов")
            }
        )
    }

    fun error(requestedId: String?, message: String, details: String, raw: String? = null): String {
        val err = buildJsonObject {
            put("type", "language_description_retrieval_failed")
            put("message", message)
            put("details", details)
            raw?.let { put("raw", it) }
        }
        return payload(ok = false, requestedId = requestedId, error = err)
    }

    private fun payload(
        ok: Boolean,
        requestedId: String?,
        section: JsonObject? = null,
        error: JsonObject? = null,
        warnings: List<String> = emptyList()
    ): String {
        val obj = buildJsonObject {
            put("tool", "getQueryLanguageDescription")
            put("ok", ok)
            put("requested_id", requestedId?.trim().orEmpty())
            section?.let { put("section", it) }
            error?.let { put("error", it) }
            if (warnings.isNotEmpty()) {
                put("warnings", warnings.joinToString("; "))
            }
        }
        return obj.toString()
    }
}