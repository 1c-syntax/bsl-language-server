# There is no check for the attribute DataExchange.Load in the object's event handler (DataExchangeLoading)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
All actions in the event-handler procedures BeforeWrite, OnWrite, BeforeDelete should be performed after checking for DataExchange.Load.

This is necessary so that no business logic of the object is executed when writing the object through the data exchange mechanism, since it has already been executed for the object in the node where it was created. In this case, all data is loaded into the Information Base "as is", without distortion (changes), checks or any other actions that prevent data loading.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Bad:
```bsl
Procedure BeforeWrite(Cancel) 

     If Not Cancel Then
        RumMyFunction();
    EndIf;

    // other code
    //
    // ...

EndProcedure
```
Good:
```bsl
Procedure BeforeWrite(Cancel) 

     If DataExchange.Load Then
        Return;
    EndIf;

    // other code
    //
    // ...
EndProcedure
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* [Standard: Using DataExchange.Load in object handlers (RU)](https://its.1c.ru/db/v8std#content:773)
* [Handler OnWrite (RU)](https://its.1c.ru/db/v8std#content:465)
* [Handler BeforeWrite (RU)](https://its.1c.ru/db/v8std#content:464)
* [Handler BeforeDelete (RU)](https://its.1c.ru/db/v8std#content:752)
