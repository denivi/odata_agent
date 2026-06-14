# Kotlin Learning Notes

Этот файл содержит короткие практические заметки по Kotlin, backend-разработке и agent engineering.

Цель файла - фиксировать знания, которые возникают во время реальной разработки агента. Это не учебник Kotlin, а рабочий Obsidian-friendly конспект для повторения.

## Правила Ведения

1. Записывать только то, что реально встретилось в проекте.
2. Объяснять коротко и практически.
3. Добавлять маленький пример кода, если он помогает.
4. Не превращать файл в полный учебник Kotlin.
5. Предпочитать формулировки, которые легко перечитать через несколько недель.

## Темы Для Накопления

### `data class`

Кратко:

`data class` удобно использовать для DTO и простых моделей данных. Kotlin автоматически создает `toString`, `equals`, `hashCode` и `copy`.

Пример:

```kotlin
data class ToolRequest(
    val query: String,
    val limit: Int = 10
)
```

Когда использовать:

- для входных и выходных DTO;
- для неизменяемых структур данных;
- когда класс в основном хранит значения.

Типичные ошибки:

- помещать бизнес-логику в DTO;
- использовать `var`, если значения не должны изменяться.

---

### Nullable Types

Кратко:

Тип `String?` означает, что значение может быть `null`. Тип `String` не может быть `null`.

Пример:

```kotlin
val name: String? = null
```

Когда использовать:

- когда поле реально может отсутствовать во внешнем JSON;
- когда отсутствие значения является частью контракта.

Типичные ошибки:

- делать поле nullable "на всякий случай";
- использовать `!!`, если можно явно обработать отсутствие значения.

---

### Safe Call Operator `?.`

Кратко:

Оператор `?.` вызывает свойство или функцию только если значение не `null`.

Пример:

```kotlin
val length = name?.length
```

Если `name == null`, результат тоже будет `null`.

Когда использовать:

- при чтении nullable-полей;
- в простых цепочках доступа к данным.

Типичные ошибки:

- скрывать важную ошибку молчаливым `null`;
- строить длинные цепочки `?.`, где лучше явно разобрать результат.

---

### Elvis Operator `?:`

Кратко:

Оператор `?:` задает значение по умолчанию, если слева `null`.

Пример:

```kotlin
val displayName = name ?: "Без имени"
```

Когда использовать:

- для безопасного fallback-значения;
- когда дефолтное значение действительно допустимо по смыслу.

Типичные ошибки:

- подставлять дефолт и тем самым скрывать проблему контракта;
- заменять отсутствующие бизнес-данные выдуманным значением.

---

### Sealed Interface / Sealed Class

Кратко:

`sealed interface` или `sealed class` задает закрытый набор вариантов результата. Это удобно для явных result types.

Пример:

```kotlin
sealed interface ToolResult<out T> {
    data class Success<T>(val data: T) : ToolResult<T>
    data class NotFound(val message: String) : ToolResult<Nothing>
    data class IntegrationError(val message: String) : ToolResult<Nothing>
}
```

Когда использовать:

- для результата внешнего tool-вызова;
- когда нужно явно различать success, not found, validation error и integration error.

Типичные ошибки:

- бросать исключение для ожидаемого business-case;
- добавлять слишком общий `Unknown`, когда можно описать конкретный вариант.

---

### `suspend` Function

Кратко:

`suspend fun` - функция, которую можно приостановить без блокировки потока. Обычно используется для HTTP-вызовов, работы с БД и других I/O-операций.

Пример:

```kotlin
suspend fun loadObject(id: String): ToolResult<ObjectCard>
```

Когда использовать:

- для Ktor HTTP client calls;
- для функций, которые вызывают другие `suspend` функции.

Типичные ошибки:

- путать coroutine suspension с созданием нового потока;
- блокировать поток внутри `suspend` функции через тяжелые синхронные операции.

---

### Coroutines

Кратко:

Coroutines позволяют писать асинхронный код в последовательном стиле. Для MVP важно использовать их просто и предсказуемо.

Пример:

```kotlin
suspend fun callTool(request: ToolRequest): ToolResult<ToolResponse> {
    return client.execute(request)
}
```

Когда использовать:

- когда API уже построен на `suspend`;
- для неблокирующих HTTP-вызовов.

Типичные ошибки:

- преждевременно усложнять код параллельным выполнением;
- запускать background work без понятного жизненного цикла.

---

### Ktor Routing

Кратко:

Ktor routing описывает HTTP endpoints приложения.

Пример:

```kotlin
routing {
    post("/chat") {
        // receive request, call agent, respond
    }
}
```

Когда использовать:

- для входных HTTP API агента;
- в thin-controller стиле: принять запрос, вызвать application/service слой, вернуть ответ.

Типичные ошибки:

- помещать бизнес-логику прямо в route;
- смешивать parsing, orchestration, HTTP client calls и response mapping в одном блоке.

---

### Ktor HTTP Client

Кратко:

Ktor HTTP client используется для вызова внешних HTTP API, включая 1C-инструменты.

Пример:

```kotlin
val response = httpClient.post(url) {
    setBody(requestDto)
}
```

Когда использовать:

- в integration client/adapters;
- для обращения к внешним tool endpoints.

Типичные ошибки:

- вызывать HTTP client прямо из agent orchestration code;
- отдавать технические исключения напрямую пользователю.

---

### kotlinx.serialization

Кратко:

`@Serializable` позволяет Kotlin-классу сериализоваться в JSON и обратно.

Пример:

```kotlin
@Serializable
data class ObjectCardDto(
    val id: String,
    val name: String
)
```

Когда использовать:

- для JSON-контрактов внешних tools;
- для request/response DTO.

Типичные ошибки:

- менять DTO без теста на сериализацию;
- смешивать внешний JSON-контракт и внутреннюю доменную модель.

---

### DTO vs Domain Model

Кратко:

DTO - структура для обмена данными с внешним API.

Domain/internal model - внутренняя модель, отражающая смысл данных внутри приложения.

Когда использовать:

- DTO на границе HTTP/JSON;
- internal model внутри orchestration и agent behavior.

Типичные ошибки:

- протаскивать внешний DTO через все слои;
- добавлять бизнес-решения в DTO.

---

### Mapper

Кратко:

Mapper явно преобразует внешний DTO во внутреннюю модель.

Пример:

```kotlin
fun ObjectCardDto.toDomain(): ObjectCard {
    return ObjectCard(
        id = id,
        name = name
    )
}
```

Когда использовать:

- после получения ответа от внешнего инструмента;
- когда нужно отделить внешний контракт от внутреннего представления.

Типичные ошибки:

- делать неявный маппинг в середине orchestration code;
- молча игнорировать важные поля или ошибки.

---

### Result Type

Кратко:

Result type описывает ожидаемые исходы операции без исключений для нормальных ситуаций.

Пример:

```kotlin
sealed interface ExternalToolResult<out T> {
    data class Success<T>(val data: T) : ExternalToolResult<T>
    data class NotFound(val message: String) : ExternalToolResult<Nothing>
    data class Ambiguous(val message: String) : ExternalToolResult<Nothing>
    data class ValidationError(val message: String) : ExternalToolResult<Nothing>
    data class IntegrationError(val message: String) : ExternalToolResult<Nothing>
}
```

Когда использовать:

- для внешних tool calls;
- когда агент должен по-разному отвечать на разные исходы.

Типичные ошибки:

- превращать все ошибки в один `Error`;
- использовать exception там, где результат ожидаем и должен быть обработан.

---

### Gradle Kotlin DSL

Кратко:

Gradle Kotlin DSL - это конфигурация Gradle на Kotlin в файлах `*.gradle.kts`.

Пример:

```kotlin
dependencies {
    testImplementation(kotlin("test"))
}
```

Когда использовать:

- для dependencies, plugins, test configuration.

Типичные ошибки:

- добавлять зависимости без проверки, действительно ли они нужны;
- менять build logic ради локальной задачи.

---

### Koog Tool

Кратко:

Koog tool - инструмент, который агент может вызвать во время reasoning/orchestration.

Когда использовать:

- когда агенту нужны данные из внешней системы;
- когда есть четкий контракт входа, выхода и ошибок.

Типичные ошибки:

- делать tool слишком широким;
- позволять агенту интерпретировать неоднозначный результат как точный.

---

### Agent Tool Contract

Кратко:

Agent tool contract описывает назначение инструмента, входные параметры, выходной JSON и ожидаемые ошибки.

Когда использовать:

- перед реализацией нового инструмента;
- при ревью интеграции с 1C-инструментом.

Типичные ошибки:

- начинать с Kotlin-кода до ясного контракта;
- не описывать поведение агента для `not found`, `ambiguous`, `validation error` и `integration error`.

---

## Lessons Learned

### 2026-06-14 - Режим Работы Codex

Контекст:

Проект является одновременно рабочей разработкой Kotlin agent backend и обучающим проектом по Kotlin/backend и AI-agent engineering.

Что было важно зафиксировать:

- Codex работает как mentor/reviewer/advisor, а не как генератор большого объема кода.
- Архитектурное обсуждение можно вести на senior-уровне.
- Kotlin-объяснения должны быть beginner-friendly.
- Новые tools начинаются с контракта и поведения агента, а не с реализации.

Что запомнить:

Понятность, тестируемость и явные границы важнее, чем быстрый большой патч.
