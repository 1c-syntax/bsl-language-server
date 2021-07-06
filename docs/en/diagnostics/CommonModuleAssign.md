# CommonModuleAssign (CommonModuleAssign)

|  Type   |        Scope        | Severity  |    Activated<br>by default    |    Minutes<br>to fix    |  Tags   |
|:-------:|:-------------------:|:---------:|:-----------------------------:|:-----------------------:|:-------:|
| `Error` |    `BSL`<br>`OS`    | `Blocker` |             `Yes`             |           `2`           | `error` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Setting a value to a common module throws exception. Such situation is possible when a common module is added to the configuration with a name that has already been used for the variable.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleAssign-off
// BSLLS:CommonModuleAssign-on
```

### Parameter for config

```json
"CommonModuleAssign": false
```
