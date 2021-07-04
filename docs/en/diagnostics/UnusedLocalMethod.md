# Unused local method (UnusedLocalMethod)

|     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |                        Tags                        |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:--------------------------------------------------:|
| `Code smell` | `BSL`<br>`OS` | `Major`  |             `Yes`             |           `1`           | `standard`<br>`suspicious`<br>`unused` |

## Parameters


|            Name            |   Type   |             Description             |        Default value        |
|:--------------------------:|:--------:|:-----------------------------------:|:---------------------------:|
| `attachableMethodPrefixes` | `String` | `Method prefixes (comma separated)` | `подключаемый_,attachable_` |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Modules should not have unused procedures and functions. Diagnostics can skip `attachable methods` that have prefixes specified in the diagnostic parameter.

## Sources

* [Standard: Modules](https://its.1c.ru/db/v8std#content:456:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UnusedLocalMethod-off
// BSLLS:UnusedLocalMethod-on
```

### Parameter for config

```json
"UnusedLocalMethod": {
    "attachableMethodPrefixes": "подключаемый_,attachable_"
}
```
