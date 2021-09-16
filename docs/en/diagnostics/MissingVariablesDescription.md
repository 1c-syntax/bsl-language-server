# All variables declarations must have a description (MissingVariablesDescription)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Code smell` |    `BSL`<br>`OS`    | `Minor`  |             `Yes`             |           `1`           | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
All module variables and export variables must have comments.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect:

```bsl
Var Context;
```

Correct:

```bsl
Var Context; // Detailed description that explains the purpose of the variable 

// Detailed description that explains the purpose of the variable 
Var Context;
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Reference: [Code conventions. Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MissingVariablesDescription-off
// BSLLS:MissingVariablesDescription-on
```

### Parameter for config

```json
"MissingVariablesDescription": false
```
