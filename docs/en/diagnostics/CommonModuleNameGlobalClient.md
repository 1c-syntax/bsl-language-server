# Global module with postfix "Client" (CommonModuleNameGlobalClient)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Code smell` | `BSL` | `Major` | `Yes` | `5` | `standard`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Для глобальных модулей добавляется постфикс "Глобальный" (англ. "Global"), в этом случае постфикс "Клиент" добавлять не следует.

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

НеПравильно: РаботаСФайламиГлобальныйКлиент, InfobaseUpdateGlobalClient

Правильно: РаботаСФайламиГлобальный, InfobaseUpdateGlobal

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

[Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:469:hdoc:3.2.1)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleNameGlobalClient-off // BSLLS:CommonModuleNameGlobalClient-on
```

### Parameter for config

```json
"CommonModuleNameGlobalClient": false
```
