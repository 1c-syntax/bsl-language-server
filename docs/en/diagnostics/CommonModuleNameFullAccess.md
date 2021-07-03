# Missed postfix "FullAccess" (CommonModuleNameFullAccess)

|        Type        | Scope | Severity | Activated<br>by default | Minutes<br>to fix |                            Tags                            |
|:------------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:----------------------------------------------------------:|
| `Security Hotspot` | `BSL` | `Major`  |             `Yes`             |           `5`           | `standard`<br>`badpractice`<br>`unpredictable` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Modules executed in privileged mode have the Privileged flag, are named with the "FullAccess" postfix (rus. "ПолныеПрава").

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

FilesFullAccess

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


[Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:3.2.2)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleNameFullAccess-off
// BSLLS:CommonModuleNameFullAccess-on
```

### Parameter for config

```json
"CommonModuleNameFullAccess": false
```
