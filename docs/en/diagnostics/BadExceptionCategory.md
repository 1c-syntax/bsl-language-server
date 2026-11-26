# Bad exception category (BadExceptionCategory)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
It is forbidden to use abstract, generic, or system error categories when throwing exceptions using the functional form of `Raise` (or `ВызватьИсключение`).

Specifying general categories such as `AllErrors`, `OtherError`, or base language execution/compilation error categories obscures the true semantic meaning of the exception. This prevents the calling code from properly classifying the issue, routing it to specialized logging channels, or performing targeted handling (e.g., retrying an operation on temporary network glitches).

Instead of generic groups, specific application-level error categories should always be preferred (e.g., `ErrorCategory.ConfigurationError`, `ErrorCategory.InvalidParameterValue`, etc.).

The diagnostic rule checks the second argument of the statement and reports an issue if any of the following forbidden categories are used:
* `AllErrors` / `ВсеОшибки`
* `OtherError` / `ПрочаяОшибка`
* `ScriptCompilationError` / `ОшибкаКомпиляцииВстроенногоЯзыка`
* `ScriptRuntimeError` / `ОшибкаВоВремяВыполненияВстроенногоЯзыка`
* `ScriptRaisedException` / `ИсключениеВызванноеИзВстроенногоЯзыка`

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Incorrect
```bsl
// Using a category that is too broad or abstract
Raise("Failed to connect to the service", ErrorCategory.AllErrors);
ВызватьИсключение("Invalid data format", КатегорияОшибки.ПрочаяОшибка);
```
Correct
```bsl
// Specifying a distinct application-level error category
Raise("Failed to connect to the service", ErrorCategory.NetworkError);
ВызватьИсключение("Invalid data format", КатегорияОшибки.ОшибкаКонфигурации);
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
Source: [1С:Предприятие 8.3.21. Документация. Глава 4. Встроенный язык. 4.6.10.2. Вызвать исключение](https://its.1c.ru/db/v8321doc#bookmark:dev:TI000002551)
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
