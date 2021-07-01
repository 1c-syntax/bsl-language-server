# Common module invalid type (CommonModuleInvalidType)

|  Type   | Scope | Severity | Activated<br>by default | Minutes<br>to fix |                         Теги                          |
|:-------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:-----------------------------------------------------:|
| `Error` | `BSL` | `Major`  |             `Yes`             |           `5`           | `standard`<br>`unpredictable`<br>`design` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

When developing common modules, you should choose one of four code execution contexts:

| Common module type              | Naming example                 | Server call | Server | External connection | Client (Ordinary application) | Client (Managed application) |
| ------------------------------- | ------------------------------ | ----------- | ------ | ------------------- | ----------------------------- | ---------------------------- |
| Server-side                     | Common (or CommonServer)       |             | +      | +                   | +                             |                              |
| Server-side to call from client | CommonServerCall               | +           | +      |                     |                               |                              |
| Client-side                     | CommonClient (or CommonGlobal) |             |        |                     | +                             | +                            |
| Client-server                   | CommonClientServer             |             | +      | +                   | +                             | +                            |


## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources

* Standard: [Rules for creating common modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:1.2)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleInvalidType-off
// BSLLS:CommonModuleInvalidType-on
```

### Parameter for config

```json
"CommonModuleInvalidType": false
```
