# Ban export global module variables (ExportVariables)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In most scenarios, we recommend that you do not use global variables and use other 1C:Enterprise script tools instead. Since monitoring the visibility (usage) areas of such variables is tricky, they often might cause issues that cannot be easily located.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
Variable FileConversion Export;
Procedure BeforeWrite(Cancel)

  If FileConversion Then
  ...

EndProcedure

```

We recommend that you use the AdditionalProperties object property for passing parameters between event subscription handlers and for passing parameters from external script to object module event handlers

```bsl
Procedure BeforeWrite(Cancel)

  If AdditionalProperties.Property("FileConversion") Then 
  ...

EndProcedure

// script that calls the procedure
FileObject.AdditionalProperties.Insert("FileConversion", True);
FileObject.Write();
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников -->

[Standard: Using global variables in modules (RU)](https://its.1c.ru/db/v8std#content:639:hdoc)
