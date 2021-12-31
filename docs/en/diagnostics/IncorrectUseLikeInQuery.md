# Incorrect use of 'LIKE' (IncorrectUseLikeInQuery)

|  Type   | Scope | Severity | Activated<br>by default | Minutes<br>to fix |                        Tags                        |
|:-------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:--------------------------------------------------:|
| `Error` | `BSL` | `Major`  |             `Yes`             |          `10`           | `standard`<br>`sql`<br>`unpredictable` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

When using the operator `LIKE` in the query text, it is allowed to use only
- constant string literals
- query parameters

It is forbidden to form a template string using calculations, use string concatenation using the query language.

Queries in which the control characters of the operator template `LIKE` are in query fields or in calculated expressions are interpreted differently on different DBMSs.

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
// BSLLS:IncorrectUseLikeInQuery-off
// BSLLS:IncorrectUseLikeInQuery-on
```

### Parameter for config

```json
"IncorrectUseLikeInQuery": false
```
