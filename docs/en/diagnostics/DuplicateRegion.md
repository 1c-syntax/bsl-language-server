# Duplicate regions (DuplicateRegion)

|     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |    Теги    |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Code smell` | `BSL`<br>`OS` |  `Info`  |             `Yes`             |           `1`           | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

All code should be structured and divided into sections (regions), each section should be present in the module in the singular.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* Reference [Code conventions. Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DuplicateRegion-off
// BSLLS:DuplicateRegion-on
```

### Parameter for config

```json
"DuplicateRegion": false
```
