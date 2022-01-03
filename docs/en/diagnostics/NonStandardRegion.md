# Non-standard region of module (NonStandardRegion)

|     Type     | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `1`           | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

All module code should be structured and divided into sections (regions).   
The set of sections for each type of module (form module, object module, common module, etc.) is uniquely determined, the presence of extraneous sections is unacceptable.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* Reference: [Code conventions. Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:NonStandardRegion-off
// BSLLS:NonStandardRegion-on
```

### Parameter for config

```json
"NonStandardRegion": false
```
