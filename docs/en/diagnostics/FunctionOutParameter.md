# Out function parameter (FunctionOutParameter)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |   Tags   |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:--------:|
| `Code smell` |    `BSL`<br>`OS`    | `Major`  |             `No`              |          `10`           | `design` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The function must have no output parameters. All output must be in the return value. If you need to return multiple values, you should use such types as Structure, Array, etc.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
// Incorrect:
ServiceURL = "";
UserName = "";
UserPassword = "";

FillConnectionParameters(ServiceURL, UserName, UserPassword);

// Correct:
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

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:FunctionOutParameter-off
// BSLLS:FunctionOutParameter-on
```

### Parameter for config

```json
"FunctionOutParameter": false
```
