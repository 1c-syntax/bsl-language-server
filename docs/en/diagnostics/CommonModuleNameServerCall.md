# Missed postfix "ServerCall" (CommonModuleNameServerCall)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Code smell` | `BSL` | `Minor` | `Yes` | `5` | `standard`<br>`badpractice`<br>`unpredictable`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Серверные общие модули для вызова с клиента содержат серверные процедуры и функции, доступные для использования из клиентского кода. Они составляют клиентский программный интерфейс сервера приложения. Такие процедуры и функции размещаются в общих модулях с признаком:

- Server (ServerCall is enabled)

Серверные общие модули для вызова с клиента называются по общим правилам именования объектов метаданных и должны именоваться с постфиксом "ВызовСервера" (англ. "ServerCall").

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
// BSLLS:CommonModuleNameServerCall-off // BSLLS:CommonModuleNameServerCall-on
```

### Parameter for config

```json
"CommonModuleNameServerCall": false
```
