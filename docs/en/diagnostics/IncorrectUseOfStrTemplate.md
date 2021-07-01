# Incorrect use of "StrTemplate" (IncorrectUseOfStrTemplate)

|  Type   |        Scope        | Severity  |    Activated<br>by default    |    Minutes<br>to fix    |                              Tags                              |
|:-------:|:-------------------:|:---------:|:-----------------------------:|:-----------------------:|:--------------------------------------------------------------:|
| `Error` |    `BSL`<br>`OS`    | `Blocker` |             `Yes`             |           `1`           |       `brainoverload`<br>`suspicious`<br>`unpredictable`       |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
When using the `StrTemplate` method, you must carefully compose the template string and pass the correct number of parameters. So, it is quite easy to make mistakes when passing values for `StrTemplate`.

It is important to remember that
- `StrTemplate` only accepts parameters from `%1` to `%10`
- if you want to pass a number immediately after the template, you need to add parentheses - `"%(1)45"`

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

1. The number of values passed after the template string is not equal (less or more) to the maximum number from a string like %N inside the template string
  - `StrTemplate("Name (version %1)");`
  - `StrTemplate("%1 (version%2)", Name);`

2. no values are passed at all, except for a formatted string due to the large number of parentheses inside a simple expression with `NStr` and `StrTemplate` Example:
- `StrTemplate(NStr("ru = 'Name (version %1)'", Version()));`
  - here the parenthesis is erroneously not closed for `NStr`
  - as a result, the expression after evaluating `NStr` becomes empty.

It is rather difficult to detect such an error by reading the code due to the presence of parentheses. And you can only catch it at runtime by getting an exception.

Right:
  - `StrTemplate(NStr ("ru = 'Name (version %1)'"), Version());`

3. An example of passing digits immediately after a template value
  - `StrTemplate("Name %(1)2"), Name);`

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Standard: Localization Requirements](https://its.1c.ru/db/v8std/content/763/hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:IncorrectUseOfStrTemplate-off
// BSLLS:IncorrectUseOfStrTemplate-on
```

### Parameter for config

```json
"IncorrectUseOfStrTemplate": false
```
