# Wrong use of ProceedWithCall function (WrongUseFunctionProceedWithCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Using the `ProceedWithCall` function outside of extension methods with the `&Around` annotation will result in a run-time error.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
&AtClient
Procedure Test()

    // copy-past from extension
    ProceedWithCall(); // Срабатывание здесь

EndProcedure
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Extensions. Functionality -> Modules (RU)](https://its.1c.ru/db/pubextensions#content:54:1)
