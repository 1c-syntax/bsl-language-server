# FormDataToValue method call (FormDataToValue)

|     Type     | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |     Tags      |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:-------------:|
| `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `5`           | `badpractice` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
In most cases, you should use the FormAttributeToValue method, instead of the FormDataToValue.

The recommendation is due to considerations of unification of the application code and the fact that the syntax of the FormAttributeToValue is simpler than FormDataToValue.
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```bsl
Procedure Test()
    Form=Doc.GetForm("DocumentForm");
    FD = Form.FormDataToValue(Object, Type("ValueTable"));
EndProcedure
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Using of FormAttributeToValue and FormDataToValue methods (RU)](https://its.1c.ru/db/v8std#content:409:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:FormDataToValue-off
// BSLLS:FormDataToValue-on
```

### Parameter for config

```json
"FormDataToValue": false
```
