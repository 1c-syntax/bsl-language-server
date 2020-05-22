# Missed postfix "ServerCall" (CommonModuleNameServerCall)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL` | `Minor` | `Yes` | `5` | `standard`<br/>`badpractice`<br/>`unpredictable` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Server common modules for calling from the client code contain server methods available for use from client code. It forms the application server client interface. Such methods are placed in common modules with the attribute:

- Server (ServerCall is enabled)

Server common modules for calling from the client must be named with the postfix "ServerCall"

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

FilesServerCall, CommonServerCall

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

[Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:2.2)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleNameServerCall-off
// BSLLS:CommonModuleNameServerCall-on
```

### Parameter for config

```json
"CommonModuleNameServerCall": false
```
