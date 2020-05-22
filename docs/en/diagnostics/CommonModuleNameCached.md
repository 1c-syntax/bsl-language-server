# Missed postfix "Cached" (CommonModuleNameCached)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Code smell` | `BSL` | `Major` | `Yes` | `5` | `standard`<br>`badpractice`<br>`unpredictable`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Модули, предназначенные для реализации на сервере или на клиенте функций с повторным использованием возвращаемых значений (на время вызова или на время сеанса), именуются с постфиксом "ПовтИсп" (англ. "Cached") и "КлиентПовтИсп" (англ. "ClientCached") соответственно.

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

FilesClientCached, UsersInternalCached

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

[Standard: Rules for creating common modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:3.2.3)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleNameCached-off // BSLLS:CommonModuleNameCached-on
```

### Parameter for config

```json
"CommonModuleNameCached": false
```
