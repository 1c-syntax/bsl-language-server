# Using of the deprecated method "CurrentDate" (DeprecatedCurrentDate)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL` | `Major` | `Yes` | `5` | `standard`<br/>`deprecated`<br/>`unpredictable` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Method "CurrentDate" is deprecated. Use "CurrentSessionDate" instead.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect:

```bsl
OperationDate = CurrentDate();
```


Correct:

```bsl
OperationDate = CurrentSessionDate();
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информаця: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* Reference: [Metadata creation and change. Work in different timezones](https://its.1c.ru/db/v8std/content/643/hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DeprecatedCurrentDate-off
// BSLLS:DeprecatedCurrentDate-on
```

### Parameter for config

```json
"DeprecatedCurrentDate": false
```
