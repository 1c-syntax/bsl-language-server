# Common module should have a programming interface (CommonModuleMissingAPI)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

A common module must have at least one export method and region "Public" or "Internal".

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
#Region Internal
Procedure Test(A) Export
    A = A + 1;
EndProcedure
#EndRegion
// End module
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Standard: Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)
