# Missed postfix "ClientServer" (CommonModuleNameClientServer)

|     Type     | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |                            Tags                            |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:----------------------------------------------------------:|
| `Code smell` | `BSL` | `Major`  |             `Yes`             |           `5`           |       `standard`<br>`badpractice`<br>`unpredictable`       |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

To avoid code duplication, it is recommended to create client-server common modules with methods whose contents are the same on the server and on the client. These modules have signs:

* Client (Managed application)
* Server (ServerCall is disabled)
* Client (Ordinary application)
* External connection

Common modules of this type are named with the "ClientServer" (rus. "КлиентСервер").

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

FilesClientServer, CommonClientServer, UsersClientServer

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


[Standard: Rules for creating common modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:2.4)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleNameClientServer-off
// BSLLS:CommonModuleNameClientServer-on
```

### Parameter for config

```json
"CommonModuleNameClientServer": false
```
