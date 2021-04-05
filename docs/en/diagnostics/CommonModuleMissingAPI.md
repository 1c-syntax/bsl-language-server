# Common module should have a programming interface (CommonModuleMissingAPI)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Code smell` | `BSL` | `Major` | `Yes` | `1` | `brainoverload`<br>`suspicious`

## <Params>

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

A common module should have at least one export method, and "ProgramInterface" or "ServiceProgramInterface" area.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Неправильно

```Bsl
// begin module
Procedure Test(A)
    A = A + 1;
EndProcedure
// end module
```

Правильно

```Bsl
// begin module
#Region Internal
Procedure Test(A)
    A = A + 1;
EndProcedure
#EndRegion
// end module
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Standart: Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets
<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleMissingAPI-off
// BSLLS:CommonModuleMissingAPI-on
```

### Parameter for config

```json
"CommonModuleMissingAPI": false
```
