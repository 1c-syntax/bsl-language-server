# Wrong use of ProceedWithCall function (WrongUseFunctionProceedWithCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
You should call ProceedWithCall function only in Extensions and only methods annotated with &AROUND.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```bsl
&AtClient
Procedure Test()
    ProceedWithCall(); // there is error    
EndProcedure
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Extensions. Functionality -> Modules (RU)](https://its.1c.ru/db/pubextensions#content:54:1)
