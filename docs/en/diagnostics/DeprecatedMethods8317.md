# Using of deprecated platform 8.3.17 global methods (DeprecatedMethods8317)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL` | `Info` | `Yes` | `5` | `deprecated` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
In platform 8.3.17, the global context property ErrorProcessing was implemented.

The following global context methods are deprecated and should not be used:
* BriefErrorDescription()
* DetailErrorDescription()
* ShowErrorInfo()

You should use the same methods of the ErrorProcessing object instead.
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информаця: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
* Source: [Platform 8.3.17 changelog](https://dl03.1c.ru/content/Platform/8_3_17_1386/1cv8upd_8_3_17_1386.htm)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DeprecatedMethods8317-off
// BSLLS:DeprecatedMethods8317-on
```

### Parameter for config

```json
"DeprecatedMethods8317": false
```
