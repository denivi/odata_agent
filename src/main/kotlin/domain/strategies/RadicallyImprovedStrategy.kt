package org.example.domain.strategies

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeAppendPrompt
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult

fun createRadicallyImprovedStrategy(): AIAgentGraphStrategy<String, String> {
    return strategy("RadicallyImprovedStrategy") {
        val nodeSendInput by nodeLLMRequest()
        val nodeExecuteTool by nodeExecuteTool()
        val nodeSendToolResult by nodeLLMSendToolResult()

        // КЛЮЧЕВОЙ УЗЕЛ: Принудительный анализ результатов инструмента
        val nodeForceAnalysis by nodeAppendPrompt<String>("forceAnalysis") {
            user("""
                🔍 КРИТИЧЕСКИ ВАЖНО - ПРОАНАЛИЗИРУЙ ПОЛУЧЕННЫЕ ДАННЫЕ!
                
                Ты только что получил данные из инструмента getAllMetadata().
                Если вопрос пользователя про метаданные системы, то
                В этих данных ЕСТЬ ответ на вопрос пользователя.
                
                Если требуется найти данные а не метаданные системы нужен инструмент executeQuery
                Чтобы правильно построить запрос нужно получить описание объекта метаданных с помощью 
                инструмента getClassMetadata и описание синтаксиса запроса с помощью инструмента getQueryLanguageDescription
                
                ТВОЯ ЗАДАЧА:
                1. НАЙДИ в полученных данных объект, о котором спрашивает пользователь
                2. ИЗВЛЕКИ его характеристики (type, name, title)
                3. ОТВЕТЬ пользователю КОРОТКО и КОНКРЕТНО
                
                ФОРМАТ ОТВЕТА (выбери подходящий):
                - "[Объект] - это [тип] системы"
                - "[Объект] относится к [категория]"
                - "[Объект] является [тип] (системное имя: [name])"
                
                ❌ НЕ говори "можете найти", "используйте поиск"
                ❌ НЕ давай инструкций
                ✅ ПРОСТО ОТВЕТЬ на основе данных, которые ты УЖЕ получил
                
                Пример:
                Вопрос: "К какому типу относятся ресурсы?"
                Данные содержат: {type: "Справочники", name: "Ресурсы"}
                Правильный ответ: "Ресурсы - это справочник системы"
            """.trimIndent())
        }

        // Узел для повторного запроса после принудительного анализа
        val nodeAnalysisRequest by nodeLLMRequest("analysisRequest")

        // Узел проверки качества - принимает String, возвращает QualityCheck
        val nodeCheckQuality by node<String, QualityCheck>("checkQuality") { response ->
            val badPhrases = listOf(
                "используйте следующие рекомендации",
                "как искать:",
                "можете найти",
                "попробуйте использовать",
                "вы можете найти",
                "чтобы найти нужный",
                "для поиска используйте",
                "рекомендации"
            )

            val isGeneralResponse = badPhrases.any {
                response.contains(it, ignoreCase = true)
            }

            // Проверяем наличие конкретной информации
            val hasConcreteInfo = response.contains(Regex(
                "(это справочник|это документ|относится к|является|системное имя)",
                RegexOption.IGNORE_CASE
            ))

            if (isGeneralResponse) {
                println("❌ ОБНАРУЖЕН ОБЩИЙ ОТВЕТ!")
                QualityCheck.Bad(response)
            } else if (hasConcreteInfo || response.length < 300) {
                println("✅ Ответ конкретный")
                QualityCheck.Good(response)
            } else {
                println("⚠️ Ответ сомнительный")
                QualityCheck.Uncertain(response)
            }
        }

        // Узел финального строгого промпта
        val nodeFinalStrictPrompt by nodeAppendPrompt<String>("finalStrictPrompt") {
            user("""
                ⛔ СТОП! Твой ответ неприемлем!
                
                Пользователь задал ПРОСТОЙ вопрос.
                Ты уже получил ДАННЫЕ из системы.
                
                НЕ давай инструкций - ПРОСТО ОТВЕТЬ!
                
                Один из этих форматов:
                • "X - это справочник"
                • "X - это документ"  
                • "X относится к категории Y"
                
                ОДНО короткое предложение. Всё.
            """.trimIndent())
        }

        val nodeFinalRequest by nodeLLMRequest("finalRequest")

        // Счетчики
        var toolIterations = 0
        var forceAnalysisUsed = false
        var finalStrictUsed = false

        // === ГРАФ ===

        edge(nodeStart forwardTo nodeSendInput)

        // LLM ответил без инструментов - проверяем
        edge(
            (nodeSendInput forwardTo nodeCheckQuality)
                .onAssistantMessage { message ->
                    println("📝 LLM ответил без инструментов")
                    true
                }
        )

        // LLM вызывает инструмент
        edge(
            (nodeSendInput forwardTo nodeExecuteTool)
                .onToolCall {
                    println("🔧 LLM вызывает инструмент")
                    toolIterations < 5
                }
        )

        edge(nodeExecuteTool forwardTo nodeSendToolResult)

        // После инструмента - еще инструмент
        edge(
            (nodeSendToolResult forwardTo nodeExecuteTool)
                .onToolCall {
                    toolIterations++
                    println("🔄 Еще инструмент (итерация $toolIterations)")
                    toolIterations < 5
                }
        )

        // КЛЮЧЕВОЕ: После инструмента LLM дает ответ
        // Проверяем - нужен ли принудительный анализ
        edge(
            (nodeSendToolResult forwardTo nodeCheckQuality)
                .onAssistantMessage { message ->
                    val needsAnalysis = message.content.contains(
                        Regex("(рекомендаци|можете найти|как искать|используйте)",
                            RegexOption.IGNORE_CASE)
                    ) || message.content.length > 500

                    if (!needsAnalysis || forceAnalysisUsed) {
                        // Если анализ не нужен или уже был - идем на проверку
                        println("📝 Проверяю ответ после инструмента")
                        true
                    } else {
                        // Если нужен анализ - идем на nodeForceAnalysis
                        false
                    }
                }
        )

        // ПРИНУДИТЕЛЬНЫЙ АНАЛИЗ: если ответ после инструмента плохой
        edge(
            (nodeSendToolResult forwardTo nodeForceAnalysis)
                .onAssistantMessage { message ->
                    val needsAnalysis = message.content.contains(
                        Regex("(рекомендаци|можете найти|как искать|используйте)",
                            RegexOption.IGNORE_CASE)
                    ) || message.content.length > 500

                    if (needsAnalysis && !forceAnalysisUsed) {
                        println("⚡ ПРИНУДИТЕЛЬНЫЙ АНАЛИЗ ДАННЫХ")
                        forceAnalysisUsed = true
                        true
                    } else {
                        false
                    }
                }

        )

        // После принудительного анализа - запрос LLM
        edge(nodeForceAnalysis forwardTo nodeAnalysisRequest)

        // После анализа - проверяем результат
        edge(
            (nodeAnalysisRequest forwardTo nodeCheckQuality)
                .onAssistantMessage { message ->
                    println("📊 Проверяю результат после анализа")
                    true
                }
        )

        // После анализа LLM может вызвать инструмент
        edge(
            (nodeAnalysisRequest forwardTo nodeExecuteTool)
                .onToolCall {
                    toolIterations++
                    println("🔧 После анализа нужен инструмент")
                    toolIterations < 5
                }
        )

        // === ОБРАБОТКА РЕЗУЛЬТАТОВ ПРОВЕРКИ ===

        // Хороший ответ - завершаем
        edge(
            (nodeCheckQuality forwardTo nodeFinish)
                .onCondition { it is QualityCheck.Good }
                .transformed { (it as QualityCheck.Good).response }
        )

        // Неопределенный ответ - считаем хорошим
        edge(
            (nodeCheckQuality forwardTo nodeFinish)
                .onCondition { it is QualityCheck.Uncertain }
                .transformed { (it as QualityCheck.Uncertain).response }
        )

        // Плохой ответ - последняя попытка
        edge(
            (nodeCheckQuality forwardTo nodeFinalStrictPrompt)
                .onCondition {
                    it is QualityCheck.Bad && !finalStrictUsed
                }
                .transformed {
                    finalStrictUsed = true
                    println("🔴 ПОСЛЕДНЯЯ ПОПЫТКА")
                    (it as QualityCheck.Bad).response
                }
        )

        edge(nodeFinalStrictPrompt forwardTo nodeFinalRequest)

        edge(
            (nodeFinalRequest forwardTo nodeFinish)
                .onAssistantMessage {
                    println("🏁 Финальный ответ")
                    true
                }
        )

        // Плохой ответ, но уже использовали все попытки - отдаем как есть
        edge(
            (nodeCheckQuality forwardTo nodeFinish)
                .onCondition {
                    it is QualityCheck.Bad && finalStrictUsed
                }
                .transformed {
                    println("⚠️ Отдаю как есть - попытки исчерпаны")
                    (it as QualityCheck.Bad).response
                }
        )

        // Превышен лимит инструментов
        edge(
            (nodeSendToolResult forwardTo nodeFinish)
                .onCondition { toolIterations >= 5 }
                .transformed {
                    "⚠️ Превышено максимальное количество шагов (5)."
                }
        )
    }
}

// === ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ ===

sealed class QualityCheck {
    data class Good(val response: String) : QualityCheck()
    data class Bad(val response: String) : QualityCheck()
    data class Uncertain(val response: String) : QualityCheck()
}

