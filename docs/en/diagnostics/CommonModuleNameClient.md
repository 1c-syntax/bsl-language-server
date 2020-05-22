# Missed postfix "Client" (CommonModuleNameClient)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Code smell` | `BSL` | `Minor` | `Yes` | `5` | `standard`<br>`badpractice`<br>`unpredictable`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Клиентские общие модули содержат клиентскую бизнес-логику (функциональность, определенную только для клиента) и имеют признаки:

- Client (Managed application)
- Client (Ordinary application)

Исключение составляют случаи, когда клиентские процедуры и функции должны быть доступны только в режиме управляемого приложения (только в режиме обычного приложения или только в режиме внешнего соединения). В таких случаях, допустима иная комбинация двух этих признаков.

Client common modules are named with the "Client" postfix. Except when the Global flag is on.

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

FilesClient, CommonClient, StandardSubsystemsClient

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

[Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:2.2)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleNameClient-off // BSLLS:CommonModuleNameClient-on
```

### Parameter for config

```json
"CommonModuleNameClient": false
```
