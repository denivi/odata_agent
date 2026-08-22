# Kotlin-тесты

## Назначение

Тест проверяет одно конкретное поведение кода автоматически. Для DTO это обычно проверка: фактический JSON внешнего API корректно десериализуется в Kotlin-класс.

## Настройка проекта

В `build.gradle.kts` должна быть зависимость:

```kotlin
testImplementation(kotlin("test"))
```

Тестовые файлы размещаются в `src/test/kotlin` и повторяют package production-кода.

## Структура теста DTO

```kotlin
class ResponseDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `должен десериализовать успешный ответ`() {
        val rawResponse = """{ ... }""".trimIndent()

        val response = json.decodeFromString<ResponseDto>(rawResponse)

        assertTrue(response.success)
        assertNull(response.error)
    }
}
```

Имя теста в обратных кавычках читается как требование. Удобная форма:

```text
должен <ожидаемый результат> когда <условие>
```

Например: `должен вернуть null для списков когда API вернул ошибку`.

## Основные проверки

- `assertEquals(expected, actual)` — значения совпадают;
- `assertTrue` / `assertFalse` — условие истинно или ложно;
- `assertNull` — значение отсутствует;
- `assertNotNull` — значение есть и его можно безопасно использовать дальше.

Для каждого внешнего API нужны минимум два теста: успешный JSON и JSON ошибки. Так изменение DTO или контракта 1С обнаруживается до запуска агента.

## Запуск

```powershell
.\gradlew.bat test
```

Если тест не проходит, сначала сравните фактический JSON API с DTO: имя поля, тип, обязательность и вложенность.
