# Empty region (EmptyRegion)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Minor` | `Yes` | `1` | `standard`<br/>`badpractice` |

## <Params>

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Module should not contain empty regions.
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```bsl
#Region EmptyRegion
// Enter code here.
#EndRegion
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информаця: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
Source: [Standard: Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets
<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:<DiagnosticKey>-off
// BSLLS:<DiagnosticKey>-on
```

### Parameter for config

```json
"<DiagnosticKey>": <DiagnosticConfig>
```
