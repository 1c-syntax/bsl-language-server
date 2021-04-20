# Общий модуль должен иметь программный интерфейс (CommonModuleMissingAPI)

 |     Type     | Поддерживаются<br>языки | Severity | Activated<br>by default | Minutes<br>to fix |                 Tags                  |
 |:------------:|:-----------------------------:|:--------:|:-----------------------------:|:-----------------------:|:-------------------------------------:|
 | `Code smell` |             `BSL`             | `Major`  |             `Yes`             |           `1`           | `brainoverload`<br>`suspicious` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostics description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Общий модуль должен иметь хотя бы один экспортный метод, а также область "ПрограммныйИнтерфейс" или "СлужебныйПрограммныйИнтерфейс".

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect

```Bsl
// Start module
Procedure Test(A)
    A = A + 1;
EndProcedure
// End module
```

Correct

```Bsl
// Start module
#Region Private
Procedure Test(A) Export
    A = A + 1;
EndProcedure
#EndRegion
// End module
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Standard: Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

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
