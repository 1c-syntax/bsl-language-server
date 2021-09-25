# Unrecommended common module name (CommonModuleNameWords)

|     Type     | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `5`           | `standard` |

## Parameters 


|  Name   |   Type   |      Description      |                                               Default value                                                |
|:-------:|:--------:|:---------------------:|:----------------------------------------------------------------------------------------------------------:|
| `words` | `String` | `Unrecommended words` | `процедуры|procedures|функции|functions|обработчики|handlers|модуль|module|функциональность|functionality` |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
It is not recommended to use the words "Procedures", "Functions", "Handlers", "Module", "Functionality" in the name of the general module.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources

* Standard: Rules for creating common modules (RU)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleNameWords-off
// BSLLS:CommonModuleNameWords-on
```

### Parameter for config

```json
"CommonModuleNameWords": {
    "words": "процедуры|procedures|функции|functions|обработчики|handlers|модуль|module|функциональность|functionality"
}
```
