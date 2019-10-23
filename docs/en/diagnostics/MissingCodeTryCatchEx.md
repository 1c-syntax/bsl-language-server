# Missing code in Raise block in "Try ... Raise ... EndTry"

It is unacceptable to catch any exceptions without a trace for the system administrator

*Wrong*

```bsl
Попытка 
    // код, приводящий к вызову исключения
    ....
Исключение // перехват любых исключений
КонецПопытки;

```

As a rule, such a design hides a real problem, which is subsequently impossible to diagnose.

*Right*

```bsl
Попытка 
    // код, приводящий к вызову исключения
    ....
Исключение
    // Пояснение причин перехвата всех исключений "незаметно" от пользователя.
    // ....
    // И запись события в журнал регистрации для системного администратора.
    ЗаписьЖурналаРегистрации(НСтр("ru = 'Выполнение операции'"),
       УровеньЖурналаРегистрации.Ошибка,,,
       ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
КонецПопытки;
```

Source: [Catching Exceptions in Code (RU)](https://its.1c.ru/db/v8std#content:499:hdoc)

## Parameters

- `commentAsCode` - `Boolean` - Consider the comment in the exception as code
