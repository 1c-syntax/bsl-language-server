# Export procedures and functions in command and general command modules (CommandModuleExportMethods)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL` | `Info` | `Yes` | `1` | `standard`<br>`clumsy` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
You should not place export procedures and functions in command and General command modules. You can't access these modules
from code external to them, so export procedures and functions in these modules do not make sense.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources

* [Источник(RU)](https://its.1c.ru/db/v8std/content/544/hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CommandModuleExportMethods-off
// BSLLS:CommandModuleExportMethods-on
```

### Parameter for config

```json
"CommandModuleExportMethods": false
```
