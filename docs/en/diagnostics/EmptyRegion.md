# The region should not be empty (EmptyRegion)

|      Type      |    Scope    |     Severity     |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:-------------:|:-----------------------------:|:----------------:|:------------------------------:|:-----------------------------------:|:----------:|
| `Code smell` |         `BSL`<br>`OS`         | `Info` |              `Yes`              |                 `1`                 | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Module should not contain empty regions.
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```bsl
#Region EmptyRegion
#EndRegion
```

## Sources

* Source: [Standard: Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:EmptyRegion-off
// BSLLS:EmptyRegion-on
```

### Parameter for config

```json
"EmptyRegion": false
```
