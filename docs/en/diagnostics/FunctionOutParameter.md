# Out function parameter (FunctionOutParameter)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The function must have no output parameters. All output must be in the return value. If you need to return multiple values, you should use such types as Structure, Array, etc.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
// Wrong:
ServiceURL = "";
UserName = "";
UserPassword = "";

FillConnectionParameters(ServiceURL, UserName, UserPassword);

// Correctly:
ConnectionParameters = NewConnectionParameters();
// Returned value - Structure:
//     Service URL  - String
//     UserName     - String
//     UserPassword - String
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
