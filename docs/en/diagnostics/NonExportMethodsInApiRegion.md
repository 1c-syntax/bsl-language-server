# Non export methods in API regions (NonExportMethodsInApiRegion)

|     Type     |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |    Теги    |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Code smell` | `BSL`<br>`OS` | `Major`  |             `Yes`             |           `1`           | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

* The "API Interface" region contains export procedures and functions intended for use by other configuration objects or other programs (for example, via an external connection).

* The “Service Programming Interface” region is intended for modules that are part of some functional subsystem. It should contain export procedures and functions that can only be called from other functional subsystems of the same library.

## Reference

* [Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:NonExportMethodsInApiRegion-off
// BSLLS:NonExportMethodsInApiRegion-on
```

### Parameter for config

```json
"NonExportMethodsInApiRegion": false
```
