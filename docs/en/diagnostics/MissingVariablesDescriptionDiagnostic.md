# All variables declarations must have a description (MissingVariablesDescriptionDiagnostic)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Minor` | `Yes` | `5` | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

All module variables and export variables, must have comments. Comments are placed on the same line after the variable.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Incorrect:

```bsl
Var Context;
```

Correct:

```bsl
Var Context; // Detailed description that explains the purpose of the variable
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информаця: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* Reference [Code conventions. Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MissingVariablesDescriptionDiagnostic-off
// BSLLS:MissingVariablesDescriptionDiagnostic-on
```

### Parameter for config

```json
"MissingVariablesDescriptionDiagnostic": false
```
